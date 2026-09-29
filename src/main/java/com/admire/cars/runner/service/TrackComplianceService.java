package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.TrackCampaign;
import com.admire.cars.runner.entity.TrackHealthCheck;
import com.admire.cars.runner.repository.TrackHealthCheckRepository;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 链路合规巡检 / Tracker compliance check.
 * <p>
 * 检查项 / checks:
 * 1. 首跳必须是服务端 3xx 跳转 / first hop must be a server side 3xx redirect
 * 2. 不得出现页面内自动跳转(meta refresh / js) / no client side auto redirect
 * 3. 最终落地域名必须命中白名单 / final host must be in the allow list
 * 4. 爬虫 UA 与浏览器 UA 的最终落地页必须一致(防 cloak) / crawler and browser must land on the same page
 */
@Slf4j
@Service
public class TrackComplianceService {

    private static final String CHROME_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    private static final String GOOGLEBOT_UA =
            "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)";

    private static final int MAX_HOPS = 10;

    // 页面内自动跳转特征 / client side auto redirect signals
    private static final Pattern AUTO_REDIRECT_PATTERN = Pattern.compile(
            "(?is)(<meta[^>]+http-equiv\\s*=\\s*['\"]?refresh|window\\.location|location\\.href\\s*=|location\\.replace\\s*\\()");

    private final TrackCampaignService campaignService;
    private final TrackHealthCheckRepository healthCheckRepository;
    private final OkHttpClient httpClient;

    // 中转域名根地址, 如 https://track.example.com / public base url of the tracker domain
    @Value("${tracker.base-url:}")
    private String baseUrl;

    public TrackComplianceService(
            TrackCampaignService campaignService,
            TrackHealthCheckRepository healthCheckRepository) {
        this.campaignService = campaignService;
        this.healthCheckRepository = healthCheckRepository;
        this.httpClient = new OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    public TrackHealthCheck check(Long campaignId, Long currentUserId) {
        TrackCampaign campaign = campaignService.getById(campaignId, currentUserId);

        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalArgumentException("tracker.base-url is not configured");
        }

        // 去掉结尾斜杠, 避免出现 //r/ 双斜杠 / strip trailing slashes to avoid //r/
        String startUrl = baseUrl.trim().replaceAll("/+$", "")
                + "/r/" + campaign.getSlug() + "?gclid=healthcheck-" + System.currentTimeMillis();
        log.info("track compliance check start, slug={}", campaign.getSlug());

        ChainResult normal = follow(startUrl, CHROME_UA);
        ChainResult bot = follow(startUrl, GOOGLEBOT_UA);

        TrackHealthCheck result = new TrackHealthCheck();
        result.setSlug(campaign.getSlug());
        result.setAdsOwner(campaign.getAdsOwner());
        result.setCampaignName(campaign.getCampaignName());
        result.setCheckTime(LocalDateTime.now());
        result.setFirstStatus(normal.firstStatus);
        result.setHopCount(normal.hops);
        result.setTargetUrl(truncate(normal.targetUrl, 1024));
        result.setFinalUrl(truncate(normal.finalUrl, 1024));
        result.setFinalHost(truncate(hostOf(normal.finalUrl), 256));
        result.setBotFinalUrl(truncate(bot.finalUrl, 1024));
        result.setRawChain(truncate(normal.chain + "\n--- crawler ---\n" + bot.chain, 2048));

        boolean serverRedirect = normal.firstStatus != null
                && normal.firstStatus >= 300 && normal.firstStatus < 400
                && normal.firstStatus != 304;
        result.setAutoRedirect(normal.autoRedirect ? TrackHealthCheck.YES : TrackHealthCheck.NO);
        result.setLandingOk(evaluateLanding(campaign, normal.finalUrl));
        // 对比落地页时忽略 query, 因为 clickId 与 siteId 每次随机
        // ignore query when comparing, clickId and siteId are random per click
        boolean cloakSuspected = !sameTarget(normal.finalUrl, bot.finalUrl);
        result.setCloakSuspected(cloakSuspected ? TrackHealthCheck.YES : TrackHealthCheck.NO);
        result.setGclidPassed(normal.finalUrl != null && normal.finalUrl.contains("gclid=")
                ? TrackHealthCheck.YES : TrackHealthCheck.NO);

        List<String> failures = new ArrayList<>();
        if (!serverRedirect) {
            failures.add("first hop is not a server side redirect, status=" + normal.firstStatus);
        }
        if (normal.autoRedirect) {
            failures.add("client side auto redirect detected in response body");
        }
        if (TrackHealthCheck.NO.equals(result.getLandingOk())) {
            failures.add("final host not in allow list: " + result.getFinalHost());
        }
        if (cloakSuspected) {
            failures.add("crawler and browser landed on different targets");
        }

        boolean passed = failures.isEmpty();
        result.setPassed(passed ? TrackHealthCheck.YES : TrackHealthCheck.NO);
        result.setMessage(truncate(passed ? "all checks passed" : String.join("; ", failures), 512));

        log.info("track compliance check done, slug={} passed={}", campaign.getSlug(), result.getPassed());
        return healthCheckRepository.save(result);
    }

    // 逐跳追踪, 不自动跟随重定向 / follow the chain hop by hop without auto following
    private ChainResult follow(String startUrl, String userAgent) {
        ChainResult result = new ChainResult();
        StringBuilder chain = new StringBuilder();
        URI current = URI.create(startUrl);
        result.finalUrl = startUrl;

        for (int hop = 0; hop < MAX_HOPS; hop++) {
            Request request = new Request.Builder()
                    .url(current.toString())
                    .header("User-Agent", userAgent)
                    .header("Accept", "text/html,application/xhtml+xml,*/*")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                int code = response.code();
                if (result.firstStatus == null) {
                    result.firstStatus = code;
                }
                chain.append(hop + 1).append(") ").append(code).append(" ").append(current).append("\n");

                if (code >= 300 && code < 400) {
                    String location = response.header("Location");
                    result.hops++;
                    if (!StringUtils.hasText(location)) {
                        chain.append("no Location header\n");
                        break;
                    }
                    URI next = current.resolve(location.trim());
                    if (hop == 0) {
                        result.targetUrl = next.toString();
                    }
                    current = next;
                    result.finalUrl = next.toString();
                    continue;
                }

                if (code >= 200 && code < 300) {
                    String body = response.body() == null ? "" : response.body().string();
                    if (AUTO_REDIRECT_PATTERN.matcher(body).find()) {
                        result.autoRedirect = true;
                    }
                    result.finalUrl = current.toString();
                }
                break;
            } catch (IOException e) {
                chain.append("ERROR ").append(e.getMessage()).append("\n");
                log.warn("track compliance check request failed, url={} error={}", current, e.getMessage());
                break;
            }
        }

        result.chain = chain.toString();
        return result;
    }

    // 落地域名白名单校验 / check the final host against the allow list
    private String evaluateLanding(TrackCampaign campaign, String finalUrl) {
        String allowed = campaign.getAllowedLandingHosts();
        String host = hostOf(finalUrl);
        if (!StringUtils.hasText(allowed) || !StringUtils.hasText(host)) {
            return TrackHealthCheck.NA;
        }
        String target = host.toLowerCase(Locale.ROOT);
        for (String candidate : allowed.split(",")) {
            String normalized = candidate.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty() && (target.equals(normalized) || target.endsWith("." + normalized))) {
                return TrackHealthCheck.YES;
            }
        }
        return TrackHealthCheck.NO;
    }

    // 只比较 scheme + host + path / compare scheme, host and path only
    private boolean sameTarget(String left, String right) {
        String a = normalizeTarget(left);
        String b = normalizeTarget(right);
        return a != null && a.equals(b);
    }

    private String normalizeTarget(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            return host + path;
        } catch (IllegalArgumentException e) {
            return url.trim().toLowerCase(Locale.ROOT);
        }
    }

    private String hostOf(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        try {
            return URI.create(url.trim()).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static class ChainResult {
        private Integer firstStatus;
        private int hops;
        private String targetUrl;
        private String finalUrl;
        private boolean autoRedirect;
        private String chain = "";
    }
}
