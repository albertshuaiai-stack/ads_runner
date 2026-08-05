package com.admire.cars.runner.job;

import com.admire.cars.runner.config.AdsConfig;
import com.admire.cars.runner.constant.Constant;
import com.admire.cars.runner.dto.IpVerificationDto;
import com.admire.cars.runner.dto.ProxyConfigurationDto;
import com.admire.cars.runner.entity.AdsNormalInfo;
import com.admire.cars.runner.entity.IpProxyInfo;
import com.admire.cars.runner.entity.NormalTaskRedirectLog;
import com.admire.cars.runner.entity.ShiftLink;
import com.admire.cars.runner.repository.AdsNormalInfoRepository;
import com.admire.cars.runner.repository.NormalTaskRedirectLogRepository;
import com.admire.cars.runner.repository.ShiftLinkRepository;
import com.admire.cars.runner.service.ReferUserAgentService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.RandomUtils;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class NormalAdsAutoTaskJob extends AdsAutoTaskJob {

    private static final Logger log = LoggerFactory.getLogger(NormalAdsAutoTaskJob.class);
    private static final String PROXY_COUNTRY_CHECK_URL = "https://api.country.is/";
    private static final Pattern COUNTRY_JSON_PATTERN = Pattern.compile("\"country\"\\s*:\\s*\"([A-Za-z]{2})\"");
    private static final Pattern PROXY_USERNAME_COUNTRY_PATTERN = Pattern.compile("(?i)(?:^|[-_])(country|cc)[-_]?([a-z]{2})(?:$|[-_])");
    private static final String DEVICE_TYPE_DESK = "Desktop";
    private static final String DEVICE_TYPE_PHONE = "phone";
    private static final String DEVICE_TYPE_PAD = "pad";
    private static final String DEFAULT_DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36";
    private static final String DEFAULT_PHONE_USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1";
    private static final String DEFAULT_PAD_USER_AGENT = "Mozilla/5.0 (iPad; CPU OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1";
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s\"'<>]+");

    private static final List<Integer> REDIRECT_STATUS_CODES = List.of(301, 302, 303, 307, 308);

    @Autowired
    private AdsNormalInfoRepository adsNormalInfoRepository;

    @Autowired
    private ShiftLinkRepository shiftLinkRepository;

    @Autowired
    private ReferUserAgentService referUserAgentService;

    @Autowired
    private NormalTaskRedirectLogRepository normalTaskRedirectLogRepository;

    @Autowired
    private AdsConfig normalAdsTaskConfig;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    protected void executeTask(JobExecutionContext context) {

        List<NormalTaskRedirectLog> normalTaskRedirectLogList = Lists.newArrayList();
        JobDataMap jobDataMap = context.getMergedJobDataMap();
        String jobId = resolveJobId(context, jobDataMap);
        Long adsId = resolveAdsId(jobId, jobDataMap);
        int lastStatusCode = -1;

        AdsNormalInfo adsNormalInfo = adsNormalInfoRepository.findById(adsId)
                .orElseThrow(() -> new IllegalArgumentException("ADS_NORMAL_INFO not found: " + adsId));

        String userAgent = getUserAgent();
        String affiliateUrl = requireText(adsNormalInfo.getAffiliteUrl(), "affiliteUrl is required");
        final String landingPageUrl = requireText(adsNormalInfo.getLandingPageUrl(), "landingPageUrl is required");
        final OkHttpClient httpClient = buildOkHttpClient(adsNormalInfo.getDynamicProxyInfo());
        IpVerificationDto ipVerificationDto = null;
        NormalTaskRedirectLog normalTaskRedirectLog = new NormalTaskRedirectLog();
        //Verify Http client IP region
        if (normalAdsTaskConfig.isIpVerification()) {
            try {
                ipVerificationDto = ipVerification(httpClient, adsNormalInfo.getCampainCountry());
                buildNormalTaskRedirectLog(normalTaskRedirectLog, adsNormalInfo,
                        ipVerificationDto.getIp(), ipVerificationDto.getCountryCode(),
                        0L, userAgent, null);
                normalTaskRedirectLog.setSuccess(true);
                if (!ipVerificationDto.isMatched()) {
                    normalTaskRedirectLog.setErrMsg("IP verification failed");
                }
            } catch (IOException e) {
                normalTaskRedirectLog.setErrMsg(e.getMessage());
            }
        } else {
            // If IP verification is disabled, still initialize the log with basic info
            buildNormalTaskRedirectLog(normalTaskRedirectLog, adsNormalInfo,
                    null, null, 0L, userAgent, null);
            normalTaskRedirectLog.setSuccess(true);
        }

        if (StringUtils.hasText(normalTaskRedirectLog.getErrMsg())) {
            log.warn("NORMAL_AUTO_TASK_IP_LOOKUP_PROXY_AUTH_REQUIRED adsId={} Job Id:{} url={} message={}",
                    adsNormalInfo.getId(), normalAdsTaskConfig.getIpLookupUrl(), jobId, normalTaskRedirectLog.getErrMsg());
            return;
        }
        normalTaskRedirectLogList.add(normalTaskRedirectLog);
        
        // Proceed with redirect following regardless of IP verification status
        URI currentUrl = URI.create(affiliateUrl);
        for (int sequence = 1; sequence <= normalAdsTaskConfig.getMaxRedirects(); sequence++) {
            normalTaskRedirectLog = new NormalTaskRedirectLog();
            buildNormalTaskRedirectLog(normalTaskRedirectLog, adsNormalInfo, 
                    (null != ipVerificationDto) ? ipVerificationDto.getIp() : null,
                    (null != ipVerificationDto) ? ipVerificationDto.getCountryCode() : null, 
                    (long) sequence, userAgent, currentUrl.toString());
            final long startTime = System.currentTimeMillis();
            final Request httpRequest = buildBaseRequest(currentUrl.toString(), userAgent, DEVICE_TYPE_DESK);
            URI responseUrl = null;
            try (Response response = httpClient.newCall(httpRequest).execute()){
                lastStatusCode = response.code();
                if (null != response.networkResponse()
                        && null != response.networkResponse().request()
                        && null != response.networkResponse().request().url()) {
                    responseUrl = URI.create(response.networkResponse().request().url().toString());
                    normalTaskRedirectLog.setLocation(responseUrl.toString());
                    normalTaskRedirectLog.setResponseUrl(responseUrl.toString());

                }
                final long durationMillis = System.currentTimeMillis() - startTime;
                normalTaskRedirectLog.setDurationMillis(String.valueOf(durationMillis));
                normalTaskRedirectLog.setStatusCode(String.valueOf(lastStatusCode));
                if (!REDIRECT_STATUS_CODES.contains(lastStatusCode)) {
                    if (isLandingPage(currentUrl, landingPageUrl)
                            && lastStatusCode >= 200
                            && lastStatusCode < 300) {
                        normalTaskRedirectLog.setSuccess(true);
                        normalTaskRedirectLogList.add(normalTaskRedirectLog);
                        break;
                    }
                    normalTaskRedirectLog.setSuccess(false);
                    normalTaskRedirectLog.setErrMsg("Non-redirect status code received: " + lastStatusCode);
                    normalTaskRedirectLogList.add(normalTaskRedirectLog);
                    currentUrl = responseUrl;
                    continue;
                }
                normalTaskRedirectLog.setSuccess(false);
                normalTaskRedirectLog.setErrMsg("Redirect status code received: " + lastStatusCode);
                normalTaskRedirectLogList.add(normalTaskRedirectLog);
                currentUrl = responseUrl;
            } catch (IOException proxyIoException) {
                log.warn("NORMAL_AUTO_TASK_PROXY_REQUEST_FAILED adsId={} jobId={} requestUrl={} message={}",
                        adsNormalInfo.getId(),
                        jobId,
                        currentUrl,
                        proxyIoException.getMessage());
                normalTaskRedirectLog.setErrMsg(proxyIoException.getMessage());
            }
        }
        normalTaskRedirectLogRepository.saveAll(normalTaskRedirectLogList);
        NormalTaskRedirectLog successTask = normalTaskRedirectLogList.stream().filter(log ->
                null != log.getResponseUrl() && log.getSuccess()).findFirst().orElse(null);
        if (null != successTask) {
            ShiftLink shiftLink = new ShiftLink();
            shiftLink.setAdsId(adsNormalInfo.getId());
            shiftLink.setAdsName(adsNormalInfo.getCampainName());
            shiftLink.setAdsType("Normal");
            shiftLink.setPlatformName(adsNormalInfo.getPlatformName());
            shiftLink.setLandingPageUrl(adsNormalInfo.getLandingPageUrl());
            shiftLink.setFullUrl(successTask.getResponseUrl());
            shiftLink.setDisplayNumber(0L);
            shiftLink.setStatus(adsNormalInfo.getStatus());
            shiftLink.setAdsOwner(adsNormalInfo.getAdsOwner());
            shiftLinkRepository.save(shiftLink);
        }
    }


    private void buildNormalTaskRedirectLog(NormalTaskRedirectLog normalTaskRedirectLog, AdsNormalInfo adsNormalInfo,
                                            String ip, String countryCode, Long sequence,String userAgent, String requestUrl) {
        normalTaskRedirectLog.setAdsOwner(adsNormalInfo.getAdsOwner());
        normalTaskRedirectLog.setIp(ip);
        normalTaskRedirectLog.setCountryCode(countryCode);
        normalTaskRedirectLog.setNormalInfoId(adsNormalInfo.getId());
        normalTaskRedirectLog.setDevice(DEVICE_TYPE_DESK);
        normalTaskRedirectLog.setUserAgent(userAgent);
        normalTaskRedirectLog.setSequence((long) sequence);
        normalTaskRedirectLog.setRequestUrl(requestUrl);
    }

    private String getUserAgent() {
        List<String> userAgentList = referUserAgentService.getUserAgentListByDevice(DEVICE_TYPE_DESK);
        if (userAgentList.isEmpty()) {
            userAgentList = List.of(DEFAULT_DESKTOP_USER_AGENT);
        }
        String userAgent = userAgentList.get(RandomUtils.nextInt(0, userAgentList.size()));
        if (!StringUtils.hasText(userAgent) || !userAgent.contains("Mozilla/5.0")) {
            userAgent = DEFAULT_DESKTOP_USER_AGENT;
        }
        return userAgent;
    }

    private boolean isLandingPage(final URI uri, final String landingPage) {
        return uri.toString().startsWith(landingPage);
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


    private Optional<ProxyConfiguration> parseProxyConfiguration(String proxyInfo) {
        if (!StringUtils.hasText(proxyInfo)) {
            return Optional.empty();
        }
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
            return Optional.of(new ProxyConfiguration(
                    credentials[0].trim(),
                    credentials[1].trim(),
                    hostAndPort[0].trim(),
                    Integer.parseInt(hostAndPort[1].trim())));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("dynamicProxyInfo port must be numeric", ex);
        }
    }


    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
    private IpVerification ipVerification(
            HttpClient httpClient,
            String expectedCountryCode) throws IOException, InterruptedException {
        if (!StringUtils.hasText(normalAdsTaskConfig.getIpLookupUrl())){
            throw new IllegalStateException("IP lookup URL is not configured");
        }

        final URI ipLookupUri = URI.create(normalAdsTaskConfig.getIpLookupUrl().trim());
        validateHttpUrl(ipLookupUri, "IP lookup url");
        final HttpResponse<String> response = httpClient.send(buildBaseRequest(ipLookupUri, null, DEVICE_TYPE_DESK).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("IP lookup request failed with status code: " + response.statusCode());
        }
        final JsonNode jsonNode = objectMapper.readTree(response.body());
        final String ip = getFirstText(jsonNode,"ip","query");
        final String countryCode = getFirstText(jsonNode, "country","countryCode", "country_code");
        final boolean matched = StringUtils.hasText(countryCode) && countryCode.equalsIgnoreCase(expectedCountryCode);
        return new IpVerification(ip, countryCode, matched);

    }

    private String getFirstText (final JsonNode jsonNode, final String... fields) {
        for (String field : fields) {
            final JsonNode node = jsonNode.get(field);
            if (node != null && !node.isNull() && node.isValueNode() && StringUtils.hasText(node.asText())) {
                return node.asText();
            }
        }
        return null;
    }

    private void validateHttpUrl(final URI uri, final String fieldName) {
        final String scheme = Optional.ofNullable(uri.getScheme())
                .orElseThrow(() -> new IllegalArgumentException(fieldName + " URL is missing scheme"));
        if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
            throw new IllegalArgumentException(fieldName + " URL must use http or https scheme");
        }
    }


    private HttpRequest.Builder buildBaseRequest(final URI uri, String userAgent, String deviceType) {
        final HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(normalAdsTaskConfig.getRequestTimeoutMillis()))
                .header("Accept", " text/html, application/json, text/plain, */*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cache-Control","no-cache")
                .header("Pragma", "no-cache")
                .header("X-Device-Type", deviceType)
                .header("User-Agent", Optional.ofNullable(userAgent).orElse(DEFAULT_DESKTOP_USER_AGENT));
        return builder;
    }


    private Request buildBaseRequest(final String uri, String userAgent, String deviceType) {
        Request request = new Request.Builder()
                .url(uri)
                .header("Accept", "text/html, application/json, text/plain, */*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .header("X-Device-Type", deviceType)
                .header("User-Agent", Optional.ofNullable(userAgent).orElse(DEFAULT_DESKTOP_USER_AGENT))
                .get()
                .build();
        return request;
    }

    private record ProxyConfiguration(String username, String password, String host, int port) {
    }

    private record IpVerification(String ip, String countryCode, boolean matched) {
        private static IpVerification unverified() {
            return new IpVerification(null, null, true);
        }
    }

    private HttpClient buildHttpClient(String dynamicProxyInfo) {
        final HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(normalAdsTaskConfig.getConnectTimeoutMillis()))
                .followRedirects(HttpClient.Redirect.NEVER);
        parseProxyConfiguration(dynamicProxyInfo).ifPresent(proxyConfig -> {
            builder.proxy(ProxySelector.of(new InetSocketAddress(proxyConfig.host(), proxyConfig.port())));
            builder.authenticator(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    if (getRequestorType() == RequestorType.PROXY) {
                        return new PasswordAuthentication(proxyConfig.username(), proxyConfig.password().toCharArray());
                    }
                    return null;
                }
            });
        });
        return builder.build();
    }

    private OkHttpClient buildOkHttpClient(String dynamicProxyInfo) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(normalAdsTaskConfig.getConnectTimeoutMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(normalAdsTaskConfig.getRequestTimeoutMillis(), TimeUnit.MILLISECONDS);

        if (!StringUtils.hasText(dynamicProxyInfo)) {
            return builder.build();
        }
        parseProxyConfiguration(dynamicProxyInfo).ifPresent(proxyConfiguration -> {
            // For SOCKS5, use Java Authenticator (RFC 1929) instead of HTTP Basic Auth
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    log.debug("Authenticator called for {}:{} (type: {})",
                            getRequestingHost(), getRequestingPort(), getRequestorType());
                    if (getRequestorType() == RequestorType.PROXY ||
                            getRequestorType() == RequestorType.SERVER) {
                        log.debug("Providing SOCKS5 credentials for {}:{}",
                                getRequestingHost(), getRequestingPort());
                        return new PasswordAuthentication(
                                proxyConfiguration.username(),
                                proxyConfiguration.password().toCharArray());
                    }
                    return null;
                }
            });
            // Use SOCKS5 proxy with OkHttp (supports SOCKS5 natively)
            builder.proxy(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(proxyConfiguration.host(), proxyConfiguration.port())));
        });
        return builder.build();
    }

    private IpVerificationDto ipVerification(
            OkHttpClient httpClient,
            String expectedCountryCode) throws IOException {

        // Define multiple IP lookup endpoints
        class IpEndpoint {
            String url;
            String[] ipFields;
            String[] countryFields;
            IpEndpoint(String url, String[] ipFields, String[] countryFields) {
                this.url = url;
                this.ipFields = ipFields;
                this.countryFields = countryFields;
            }
        }

        IpEndpoint[] endpoints = {
                new IpEndpoint("https://api.country.is/", new String[]{"ip"}, new String[]{"country"}),
                new IpEndpoint("https://ipapi.co/json/", new String[]{"ip"}, new String[]{"country_code"}),
                new IpEndpoint("https://httpbin.org/ip", new String[]{"origin"}, new String[]{}),
        };

        // Also try configured URL if provided
        String configuredUrl = null;
        if (StringUtils.hasText(normalAdsTaskConfig.getIpLookupUrl())) {
            configuredUrl = normalAdsTaskConfig.getIpLookupUrl().trim();
            validateHttpUrl(configuredUrl, "IP lookup url");
        }

        IOException lastException = null;

        // Try configured URL first if available
        if (StringUtils.hasText(configuredUrl)) {
            try {
                IpVerificationDto result = attemptIpLookup(httpClient, configuredUrl,
                        new String[]{"ip", "query"}, new String[]{"country", "countryCode", "country_code"});
                if (result != null) {
                    log.info("IP Verification succeeded with configured URL: {}", configuredUrl);
                    result.setMatched(result.getCountryCode() != null &&
                            result.getCountryCode().equalsIgnoreCase(expectedCountryCode));
                    return result;
                }
            } catch (IOException e) {
                log.warn("Configured IP lookup URL failed: {} - {}", configuredUrl, e.getMessage());
                lastException = e;
            }
        }

        // Try each predefined endpoint
        for (IpEndpoint endpoint : endpoints) {
            try {
                IpVerificationDto result = attemptIpLookup(httpClient, endpoint.url,
                        endpoint.ipFields, endpoint.countryFields);
                if (result != null) {
                    log.info("IP Verification succeeded with endpoint: {} (IP: {}, Country: {})",
                            endpoint.url, result.getIp(), result.getCountryCode());
                    result.setMatched(result.getCountryCode() != null &&
                            result.getCountryCode().equalsIgnoreCase(expectedCountryCode));
                    return result;
                }
            } catch (IOException e) {
                log.debug("IP lookup endpoint {} failed: {}", endpoint.url, e.getMessage());
                lastException = e;
            }
        }

        // All endpoints failed
        if (lastException != null) {
            throw new IOException("All IP lookup endpoints failed. Last error: " + lastException.getMessage(), lastException);
        }
        throw new IOException("No valid IP lookup endpoint returned data");
    }

    private void validateHttpUrl(final String url, final String fieldName) {
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException(fieldName + " URL is required");
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException(fieldName + " URL must use http or https scheme");
        }
    }

    private IpVerificationDto attemptIpLookup(OkHttpClient httpClient, String url,
                                              String[] ipFieldNames, String[] countryFieldNames) throws IOException {
        validateHttpUrl(url, "IP lookup url");

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", Constant.DEFAULT_DESKTOP_USER_AGENT)
                .header("X-Device-Type", Constant.DEVICE_TYPE_DESK)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() < 200 || response.code() >= 300) {
                throw new IOException("IP lookup failed with status code: " + response.code() + " from " + url);
            }

            String body = response.body() != null ? response.body().string() : "";
            if (!StringUtils.hasText(body)) {
                throw new IOException("Empty response from " + url);
            }

            final JsonNode jsonNode = objectMapper.readTree(body);
            final String ip = getFirstText(jsonNode, ipFieldNames);
            final String countryCode = getFirstText(jsonNode, countryFieldNames);

            // Consider successful only if we got an IP
            if (!StringUtils.hasText(ip)) {
                throw new IOException("No IP field found in response from " + url);
            }

            IpVerificationDto result = new IpVerificationDto();
            result.setIp(ip);
            result.setCountryCode(countryCode);
            return result;
        }
    }
}
