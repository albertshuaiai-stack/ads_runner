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
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NormalAdsAutoTaskJob extends AdsAutoTaskJob {

    private static final Logger log = LoggerFactory.getLogger(NormalAdsAutoTaskJob.class);
    private static final int MAX_REDIRECTS = 10;
    private static final String PROXY_COUNTRY_CHECK_URL = "https://api.country.is/";
    private static final Pattern COUNTRY_JSON_PATTERN = Pattern.compile("\"country\"\\s*:\\s*\"([A-Za-z]{2})\"");
    private static final Pattern PROXY_USERNAME_COUNTRY_PATTERN = Pattern.compile("(?i)(?:^|[-_])(country|cc)[-_]?([a-z]{2})(?:$|[-_])");
    private static final String DEVICE_TYPE_DESK = "desk";
    private static final String DEVICE_TYPE_PHONE = "phone";
    private static final String DEVICE_TYPE_PAD = "pad";
    private static final String DEFAULT_DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36";
    private static final String DEFAULT_PHONE_USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1";
    private static final String DEFAULT_PAD_USER_AGENT = "Mozilla/5.0 (iPad; CPU OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1";
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

        String expectedCountryCode = validateProxyCountry(adsNormalInfo.getCampainCountry());
        RequestProfile requestProfile = resolveRequestProfile(jobDataMap);

        String affiliateUrl = requireText(adsNormalInfo.getAffiliteUrl(), "affiliteUrl is required");
        String landingPageUrl = requireText(adsNormalInfo.getLandingPageUrl(), "landingPageUrl is required");
        List<String> proxyCandidates = buildProxyCandidates(adsNormalInfo.getDynamicProxyInfo(), adsNormalInfo.getDynamicProxyInfoBackup());

        InvocationResult invocationResult = invokeUntilLandingPage(
                affiliateUrl,
                landingPageUrl,
                expectedCountryCode,
                proxyCandidates,
                requestProfile);

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

        log.info("NORMAL_AUTO_TASK_AUDIT_SAVED adsId={} jobId={} finalUrl={} redirects={} deviceType={}",
                adsNormalInfo.getId(), jobId, invocationResult.finalUrl(), invocationResult.redirectCount(), requestProfile.deviceType());
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

    private String validateProxyCountry(String campainCountry) {
        String countryKey = normalizeCountryCode(campainCountry);
        if (!StringUtils.hasText(countryKey) || !COUNTRY_SUPPORT.containsKey(countryKey)) {
            throw new IllegalArgumentException("Unsupported campainCountry for proxy routing: " + campainCountry);
        }
        return countryKey;
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

    private InvocationResult invokeUntilLandingPage(
            String affiliateUrl,
            String landingPageUrl,
            String expectedCountryCode,
            List<String> proxyCandidates,
            RequestProfile requestProfile) {
        IOException lastException = null;
        for (String proxyInfo : proxyCandidates) {
            try {
                return invokeWithProxyStrategy(affiliateUrl, landingPageUrl, expectedCountryCode, proxyInfo, requestProfile);
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

    private InvocationResult invokeWithProxyStrategy(
            String affiliateUrl,
            String landingPageUrl,
            String expectedCountryCode,
            String proxyInfo,
            RequestProfile requestProfile)
            throws IOException, InterruptedException {
        ProxyConfiguration proxyConfiguration = StringUtils.hasText(proxyInfo) ? parseProxyConfiguration(proxyInfo) : null;
        HttpClient client = buildHttpClient(proxyConfiguration);
        if (proxyConfiguration != null) {
            verifyProxyCountryCode(client, expectedCountryCode, proxyConfiguration, requestProfile);
        }
        String currentUrl = affiliateUrl;
        for (int redirectCount = 1; redirectCount <= MAX_REDIRECTS; redirectCount++) {
            HttpResponse<String> response = sendRequest(client, currentUrl, requestProfile);
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

    private HttpClient buildHttpClient(ProxyConfiguration proxyConfiguration) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER);

        if (proxyConfiguration == null) {
            return builder.build();
        }

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

    private HttpResponse<String> sendRequest(HttpClient client, String url, RequestProfile requestProfile) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "text/html,application/xhtml+xml")
                .header("User-Agent", requestProfile.userAgent())
                .header("X-Device-Type", requestProfile.deviceType())
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private boolean matchesLandingPage(String currentUrl, String landingPageUrl, HttpResponse<String> response) {
        String normalizedLanding = normalizeToken(landingPageUrl);
        if (isLandingUrlMatch(currentUrl, landingPageUrl)) {
            return true;
        }

        String body = response.body();
        if (StringUtils.hasText(body) && normalizeToken(body).contains(normalizedLanding)) {
            return true;
        }
        return false;
    }

    private boolean isLandingUrlMatch(String targetUrl, String landingPageUrl) {
        String normalizedTarget = normalizeToken(targetUrl);
        String normalizedLanding = normalizeToken(landingPageUrl);
        if (normalizedTarget.equals(normalizedLanding)) {
            return true;
        }
        if (!normalizedTarget.startsWith(normalizedLanding)) {
            return false;
        }
        if (normalizedLanding.endsWith("/") || normalizedTarget.length() == normalizedLanding.length()) {
            return true;
        }
        char boundary = normalizedTarget.charAt(normalizedLanding.length());
        return boundary == '/' || boundary == '?' || boundary == '#';
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

    private String normalizeCountryCode(String value) {
        return requireText(value, "campainCountry is required").toUpperCase(Locale.ROOT);
    }

    private RequestProfile resolveRequestProfile(JobDataMap jobDataMap) {
        String deviceType = normalizeDeviceType(jobDataMap.getString("deviceType"));
        String userAgentOverride = trimToNull(jobDataMap.getString("httpClientUserAgent"));
        String userAgent = StringUtils.hasText(userAgentOverride)
                ? userAgentOverride
                : defaultUserAgentByDeviceType(deviceType);
        return new RequestProfile(deviceType, userAgent);
    }

    private String normalizeDeviceType(String value) {
        if (!StringUtils.hasText(value)) {
            return DEVICE_TYPE_DESK;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "desk", "desktop", "pc", "laptop" -> DEVICE_TYPE_DESK;
            case "phone", "mobile" -> DEVICE_TYPE_PHONE;
            case "pad", "tablet", "ipad" -> DEVICE_TYPE_PAD;
            default -> throw new IllegalArgumentException("Unsupported deviceType for HttpClient: " + value);
        };
    }

    private String defaultUserAgentByDeviceType(String deviceType) {
        return switch (deviceType) {
            case DEVICE_TYPE_PHONE -> DEFAULT_PHONE_USER_AGENT;
            case DEVICE_TYPE_PAD -> DEFAULT_PAD_USER_AGENT;
            default -> DEFAULT_DESKTOP_USER_AGENT;
        };
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private void verifyProxyCountryCode(
            HttpClient client,
            String expectedCountryCode,
            ProxyConfiguration proxyConfiguration,
            RequestProfile requestProfile) throws IOException, InterruptedException {
        String countryFromUsername = extractCountryCodeFromProxyUsername(proxyConfiguration.username());
        if (StringUtils.hasText(countryFromUsername)) {
            if (!expectedCountryCode.equals(countryFromUsername)) {
                throw new IllegalStateException("Proxy country code mismatch. expected=" + expectedCountryCode + ", actual=" + countryFromUsername);
            }
            return;
        }

        HttpResponse<String> countryResponse = sendRequest(client, PROXY_COUNTRY_CHECK_URL, requestProfile);
        if (countryResponse.statusCode() < 200 || countryResponse.statusCode() >= 300) {
            throw new IllegalStateException("Proxy country verification failed with status: " + countryResponse.statusCode());
        }

        String actualCountryCode = parseCountryCode(countryResponse.body());
        if (!StringUtils.hasText(actualCountryCode)) {
            throw new IllegalStateException("Proxy country verification response missing country code");
        }
        if (!expectedCountryCode.equals(actualCountryCode)) {
            throw new IllegalStateException("Proxy country code mismatch. expected=" + expectedCountryCode + ", actual=" + actualCountryCode);
        }
    }

    private String extractCountryCodeFromProxyUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        Matcher matcher = PROXY_USERNAME_COUNTRY_PATTERN.matcher(username.trim());
        if (matcher.find()) {
            return matcher.group(2).toUpperCase(Locale.ROOT);
        }
        return null;
    }

    private String parseCountryCode(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        Matcher matcher = COUNTRY_JSON_PATTERN.matcher(responseBody);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase(Locale.ROOT);
        }
        String trimmed = responseBody.trim();
        if (trimmed.matches("(?i)^[a-z]{2}$")) {
            return trimmed.toUpperCase(Locale.ROOT);
        }
        return null;
    }

    private String normalizeToken(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("/+$", "").replaceAll("\\s+", "");
    }

    private record ProxyConfiguration(String username, String password, String host, int port) {
    }

    private record RequestProfile(String deviceType, String userAgent) {
    }

    private record InvocationResult(String finalUrl, int redirectCount) {
    }
}
