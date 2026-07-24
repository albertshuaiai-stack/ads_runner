package com.admire.cars.runner.job;

import com.admire.cars.runner.entity.AdsNormalInfo;
import com.admire.cars.runner.entity.ShiftLink;
import com.admire.cars.runner.repository.AdsNormalInfoRepository;
import com.admire.cars.runner.repository.ShiftLinkRepository;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NormalAdsAutoTaskJob extends AdsAutoTaskJob {

    private static final Logger log = LoggerFactory.getLogger(NormalAdsAutoTaskJob.class);
    private static final int MAX_REDIRECTS = 10;
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s\"'<>]+");
    private static final Map<String, String> COUNTRY_SUPPORT = Map.ofEntries(
            Map.entry("US", "United States"),
            Map.entry("SG", "Singapore"),
            Map.entry("HK", "Hong Kong"),
            Map.entry("TW", "Taiwan"),
            Map.entry("JP", "Japan"),
            Map.entry("KR", "South Korea"),
            Map.entry("CN", "China"),
            Map.entry("IN", "India"),
            Map.entry("GB", "United Kingdom"),
            Map.entry("UK", "United Kingdom"),
            Map.entry("DE", "Germany"),
            Map.entry("FR", "France"),
            Map.entry("AU", "Australia"),
            Map.entry("CA", "Canada"));

    @Autowired
    private AdsNormalInfoRepository adsNormalInfoRepository;

    @Autowired
    private ShiftLinkRepository shiftLinkRepository;

    @Override
    protected void executeTask(JobExecutionContext context) {
        JobDataMap jobDataMap = context.getMergedJobDataMap();
        String jobId = resolveJobId(context, jobDataMap);
        Long adsId = resolveAdsId(jobId, jobDataMap);

        AdsNormalInfo adsNormalInfo = adsNormalInfoRepository.findById(adsId)
                .orElseThrow(() -> new IllegalArgumentException("ADS_NORMAL_INFO not found: " + adsId));

        validateProxyCountry(adsNormalInfo.getCampainCountry());

        String affiliateUrl = requireText(adsNormalInfo.getAffiliteUrl(), "affiliteUrl is required");
        String landingPageUrl = requireText(adsNormalInfo.getLandingPageUrl(), "landingPageUrl is required");
        List<String> proxyCandidates = buildProxyCandidates(adsNormalInfo.getDynamicProxyInfo(), adsNormalInfo.getDynamicProxyInfoBackup());

        InvocationResult invocationResult = invokeUntilLandingPage(affiliateUrl, landingPageUrl, proxyCandidates);

        ShiftLink shiftLink = new ShiftLink();
        shiftLink.setAdsId(adsNormalInfo.getId());
        shiftLink.setAdsName(adsNormalInfo.getCampainName());
        shiftLink.setAdsType("Normal");
        shiftLink.setPlatformName(adsNormalInfo.getPlatformName());
        shiftLink.setLandingPageUrl(adsNormalInfo.getLandingPageUrl());
        shiftLink.setFullUrl(invocationResult.finalUrl);
        shiftLink.setDisplayNumber(0L);
        shiftLink.setStatus(adsNormalInfo.getStatus());
        shiftLink.setAdsOwner(adsNormalInfo.getAdsOwner());
        shiftLinkRepository.save(shiftLink);

        log.info("NORMAL_AUTO_TASK_AUDIT_SAVED adsId={} jobId={} finalUrl={} redirects={}",
                adsNormalInfo.getId(), jobId, invocationResult.finalUrl(), invocationResult.redirectCount());
    }

    private String resolveJobId(JobExecutionContext context, JobDataMap jobDataMap) {
        String jobId = jobDataMap.getString("jobId");
        if (StringUtils.hasText(jobId)) {
            return jobId;
        }
        return context.getJobDetail().getKey().getName();
    }

    private Long resolveAdsId(String jobId, JobDataMap jobDataMap) {
        String source = StringUtils.hasText(jobId) ? jobId : jobDataMap.getString("jobId");
        if (StringUtils.hasText(source)) {
            int separatorIndex = source.indexOf('-');
            String prefix = separatorIndex > 0 ? source.substring(0, separatorIndex) : source;
            if (StringUtils.hasText(prefix)) {
                try {
                    return Long.valueOf(prefix);
                } catch (NumberFormatException ignored) {
                    // fall back to Quartz job data
                }
            }
        }

        long jobDataAdsId = jobDataMap.getLongValue("adsId");
        if (jobDataAdsId <= 0) {
            throw new IllegalArgumentException("adsId is required for normal ads job execution");
        }
        return jobDataAdsId;
    }

    private void validateProxyCountry(String campainCountry) {
        String countryKey = normalizeToken(campainCountry);
        if (!StringUtils.hasText(countryKey) || !COUNTRY_SUPPORT.containsKey(countryKey)) {
            throw new IllegalArgumentException("Unsupported campainCountry for proxy routing: " + campainCountry);
        }
    }

    private List<String> buildProxyCandidates(String primaryProxy, String backupProxy) {
        List<String> candidates = new ArrayList<>();
        if (StringUtils.hasText(primaryProxy)) {
            candidates.add(primaryProxy.trim());
        }
        if (StringUtils.hasText(backupProxy)) {
            candidates.add(backupProxy.trim());
        }
        candidates.add(null);
        return candidates;
    }

    private InvocationResult invokeUntilLandingPage(String affiliateUrl, String landingPageUrl, List<String> proxyCandidates) {
        IOException lastException = null;
        for (String proxyInfo : proxyCandidates) {
            try {
                return invokeWithProxyStrategy(affiliateUrl, landingPageUrl, proxyInfo);
            } catch (IOException ex) {
                lastException = ex;
                if (proxyInfo != null) {
                    log.warn("NORMAL_AUTO_TASK_PROXY_FAILED proxyInfo={} message={}", proxyInfo, ex.getMessage());
                }
            } catch (RuntimeException ex) {
                if (proxyInfo != null) {
                    log.warn("NORMAL_AUTO_TASK_PROXY_INVALID proxyInfo={} message={}", proxyInfo, ex.getMessage());
                    continue;
                }
                throw ex;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Normal ads job interrupted", ex);
            }
        }
        throw new IllegalStateException("Failed to invoke affiliate_url chain", lastException);
    }

    private InvocationResult invokeWithProxyStrategy(String affiliateUrl, String landingPageUrl, String proxyInfo)
            throws IOException, InterruptedException {
        HttpClient client = buildHttpClient(proxyInfo);
        String currentUrl = affiliateUrl;
        for (int redirectCount = 1; redirectCount <= MAX_REDIRECTS; redirectCount++) {
            HttpResponse<String> response = sendRequest(client, currentUrl);
            if (matchesLandingPage(currentUrl, landingPageUrl, response)) {
                return new InvocationResult(currentUrl, redirectCount);
            }

            String nextUrl = resolveNextUrl(currentUrl, response);
            if (!StringUtils.hasText(nextUrl) || normalizeToken(nextUrl).equals(normalizeToken(currentUrl))) {
                throw new IllegalStateException("Unable to resolve next URL from affiliate_url response: " + currentUrl);
            }
            currentUrl = nextUrl;
        }
        throw new IllegalStateException("Exceeded redirect limit while invoking affiliate_url");
    }

    private HttpClient buildHttpClient(String proxyInfo) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10));

        if (!StringUtils.hasText(proxyInfo)) {
            return builder.build();
        }

        ProxyConfiguration proxyConfiguration = parseProxyConfiguration(proxyInfo);
        builder.proxy(ProxySelector.of(new InetSocketAddress(proxyConfiguration.host(), proxyConfiguration.port())));
        builder.authenticator(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                if (getRequestorType() == RequestorType.PROXY) {
                    return new PasswordAuthentication(proxyConfiguration.username(), proxyConfiguration.password().toCharArray());
                }
                return null;
            }
        });
        return builder.build();
    }

    private ProxyConfiguration parseProxyConfiguration(String proxyInfo) {
        String[] userAndHost = proxyInfo.split("@", 2);
        if (userAndHost.length != 2) {
            throw new IllegalArgumentException("dynamicProxyInfo must use user:password@host:port format");
        }
        String[] credentials = userAndHost[0].split(":", 2);
        if (credentials.length != 2 || !StringUtils.hasText(credentials[0]) || !StringUtils.hasText(credentials[1])) {
            throw new IllegalArgumentException("dynamicProxyInfo credentials must use user:password format");
        }
        String[] hostAndPort = userAndHost[1].split(":", 2);
        if (hostAndPort.length != 2 || !StringUtils.hasText(hostAndPort[0]) || !StringUtils.hasText(hostAndPort[1])) {
            throw new IllegalArgumentException("dynamicProxyInfo host must use host:port format");
        }
        try {
            return new ProxyConfiguration(
                    credentials[0].trim(),
                    credentials[1].trim(),
                    hostAndPort[0].trim(),
                    Integer.parseInt(hostAndPort[1].trim()));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("dynamicProxyInfo port must be numeric", ex);
        }
    }

    private HttpResponse<String> sendRequest(HttpClient client, String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "text/html,application/xhtml+xml")
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private boolean matchesLandingPage(String currentUrl, String landingPageUrl, HttpResponse<String> response) {
        String normalizedCurrent = normalizeToken(currentUrl);
        String normalizedLanding = normalizeToken(landingPageUrl);
        if (normalizedCurrent.equals(normalizedLanding)) {
            return true;
        }

        String body = response.body();
        if (StringUtils.hasText(body) && normalizeToken(body).contains(normalizedLanding)) {
            return true;
        }

        return response.headers().firstValue("Location")
                .map(this::normalizeToken)
                .map(normalizedLanding::equals)
                .orElse(false);
    }

    private String resolveNextUrl(String currentUrl, HttpResponse<String> response) {
        return response.headers().firstValue("Location")
                .map(location -> resolveAgainstCurrent(currentUrl, location))
                .orElseGet(() -> extractUrlFromBody(response.body()));
    }

    private String resolveAgainstCurrent(String currentUrl, String location) {
        URI baseUri = URI.create(currentUrl);
        URI nextUri = baseUri.resolve(location.trim());
        return nextUri.toString();
    }

    private String extractUrlFromBody(String body) {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        Matcher matcher = URL_PATTERN.matcher(body);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalizeToken(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("/+$", "").replaceAll("\\s+", "");
    }

    private record ProxyConfiguration(String username, String password, String host, int port) {
    }

    private record InvocationResult(String finalUrl, int redirectCount) {
    }
}
