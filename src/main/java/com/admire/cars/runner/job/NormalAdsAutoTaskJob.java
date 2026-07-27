package com.admire.cars.runner.job;

import com.admire.cars.runner.config.NormalAdsTaskConfig;
import com.admire.cars.runner.entity.AdsNormalInfo;
import com.admire.cars.runner.entity.NormalTaskRedirectLog;
import com.admire.cars.runner.entity.ShiftLink;
import com.admire.cars.runner.repository.AdsNormalInfoRepository;
import com.admire.cars.runner.repository.NormalTaskRedirectLogRepository;
import com.admire.cars.runner.repository.ShiftLinkRepository;
import com.admire.cars.runner.service.ReferUserAgentService;
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
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

public class NormalAdsAutoTaskJob extends AdsAutoTaskJob {

    private static final Logger log = LoggerFactory.getLogger(NormalAdsAutoTaskJob.class);
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

    private static final List<Integer> REDIRECT_STATUS_CODES = List.of(301, 302, 303, 307, 308,200);

    @Autowired
    private AdsNormalInfoRepository adsNormalInfoRepository;

    @Autowired
    private ShiftLinkRepository shiftLinkRepository;

    @Autowired
    private ReferUserAgentService referUserAgentService;

    @Autowired
    private NormalTaskRedirectLogRepository normalTaskRedirectLogRepository;

    @Autowired
    private NormalAdsTaskConfig normalAdsTaskConfig;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    protected void executeTask(JobExecutionContext context) {

        List<NormalTaskRedirectLog> normalTaskRedirectLogList = Lists.newArrayList();
        NormalTaskRedirectLog normalTaskRedirectLog;
        JobDataMap jobDataMap = context.getMergedJobDataMap();
        String jobId = resolveJobId(context, jobDataMap);
        Long adsId = resolveAdsId(jobId, jobDataMap);

        AdsNormalInfo adsNormalInfo = adsNormalInfoRepository.findById(adsId)
                .orElseThrow(() -> new IllegalArgumentException("ADS_NORMAL_INFO not found: " + adsId));

        List<String> userAgentList = referUserAgentService.getUserAgentListByDevice(DEVICE_TYPE_DESK);
        if (userAgentList.isEmpty()) {
            userAgentList = List.of(DEFAULT_DESKTOP_USER_AGENT);
        }
        String userAgent = userAgentList.get(RandomUtils.nextInt(0, userAgentList.size()));
        if (!StringUtils.hasText(userAgent) || !userAgent.contains("Mozilla/5.0")) {
            userAgent = DEFAULT_DESKTOP_USER_AGENT;
        }

        String affiliateUrl = requireText(adsNormalInfo.getAffiliteUrl(), "affiliteUrl is required");
        final String landingPageUrl = requireText(adsNormalInfo.getLandingPageUrl(), "landingPageUrl is required");
        final HttpClient httpClient = buildHttpClient(adsNormalInfo.getDynamicProxyInfo());
        final HttpClient directHttpClient = buildHttpClient(null);
        IpVerification ipVerification = IpVerification.unverified();
        try {
            //Verify Http client IP region
            if (normalAdsTaskConfig.isIpVerification()) {
                try {
                    ipVerification = ipVerification(httpClient, adsNormalInfo.getCampainCountry());
                    if (!ipVerification.matched) {
                        normalTaskRedirectLog = new NormalTaskRedirectLog();
                        normalTaskRedirectLog.setErrMsg("IP verification failed");
                        normalTaskRedirectLog.setAdsOwner(adsNormalInfo.getAdsOwner());
                        normalTaskRedirectLog.setNormalInfoId(adsNormalInfo.getId());
                        normalTaskRedirectLog.setSequence(1L);
                        normalTaskRedirectLogList.add(normalTaskRedirectLog);
                        return;
                    }
                } catch (IOException e) {
                    String message = e.getMessage();
                    if (StringUtils.hasText(message) && message.contains("407")) {
                        log.warn("NORMAL_AUTO_TASK_IP_LOOKUP_PROXY_AUTH_REQUIRED adsId={} jobId={} url={} message={}",
                                adsNormalInfo.getId(), jobId, normalAdsTaskConfig.getIpLookupUrl(), message);
                    } else {
                        log.warn("NORMAL_AUTO_TASK_IP_LOOKUP_SKIPPED adsId={} jobId={} url={} message={}",
                                adsNormalInfo.getId(), jobId, normalAdsTaskConfig.getIpLookupUrl(), message);
                    }
                    try {
                        ipVerification = ipVerification(directHttpClient, adsNormalInfo.getCampainCountry());
                    } catch (IOException secondLookupException) {
                        log.warn("NORMAL_AUTO_TASK_IP_LOOKUP_DIRECT_FAILED adsId={} jobId={} url={} message={}",
                                adsNormalInfo.getId(),
                                jobId,
                                normalAdsTaskConfig.getIpLookupUrl(),
                                secondLookupException.getMessage());
                        ipVerification = IpVerification.unverified();
                    }
                }
            }
            URI currentUrl = URI.create(affiliateUrl);
            for (int sequence = 1; sequence <= normalAdsTaskConfig.getMaxRedirects(); sequence++) {
                normalTaskRedirectLog = new NormalTaskRedirectLog();
                normalTaskRedirectLog.setAdsOwner(adsNormalInfo.getAdsOwner());
                normalTaskRedirectLog.setIp(ipVerification.ip);
                normalTaskRedirectLog.setCountryCode(ipVerification.countryCode);
                normalTaskRedirectLog.setNormalInfoId(adsNormalInfo.getId());
                normalTaskRedirectLog.setDevice(DEVICE_TYPE_DESK);
                normalTaskRedirectLog.setUserAgent(userAgent);
                normalTaskRedirectLog.setSequence((long) sequence);
                normalTaskRedirectLog.setRequestUrl(currentUrl.toString());

                final long startTime = System.currentTimeMillis();
                final HttpRequest httpRequest = buildBaseRequest(currentUrl, userAgent, DEVICE_TYPE_DESK).build();
                HttpResponse<Void> response;
                try {
                    response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.discarding());
                    if (response.statusCode() == 407) {
                        log.warn("NORMAL_AUTO_TASK_PROXY_AUTH_REQUIRED adsId={} jobId={} requestUrl={}", adsNormalInfo.getId(), jobId, currentUrl);
                        response = directHttpClient.send(httpRequest, HttpResponse.BodyHandlers.discarding());
                    }
                } catch (IOException proxyIoException) {
                    log.warn("NORMAL_AUTO_TASK_PROXY_REQUEST_FAILED adsId={} jobId={} requestUrl={} message={}",
                            adsNormalInfo.getId(),
                            jobId,
                            currentUrl,
                            proxyIoException.getMessage());
                    response = directHttpClient.send(httpRequest, HttpResponse.BodyHandlers.discarding());
                }
                final long durationMillis = System.currentTimeMillis() - startTime;
                final Optional<String> locationHeader = response.headers().firstValue("Location");
                normalTaskRedirectLog.setDurationMillis(String.valueOf(durationMillis));
                normalTaskRedirectLog.setStatusCode(String.valueOf(response.statusCode()));
                normalTaskRedirectLog.setLocation(locationHeader.orElse(null));
                if (!REDIRECT_STATUS_CODES.contains(response.statusCode())) {
                    if (isLandingPage(currentUrl, landingPageUrl)
                            && response.statusCode() >= 200
                            && response.statusCode() < 300) {
                        normalTaskRedirectLog.setSuccess(true);
                        normalTaskRedirectLog.setResponseUrl(currentUrl.toString());
                        normalTaskRedirectLogList.add(normalTaskRedirectLog);
                        break;
                    }
                    normalTaskRedirectLog.setSuccess(false);
                    normalTaskRedirectLog.setErrMsg("Non-redirect status code received: " + response.statusCode());
                    normalTaskRedirectLogList.add(normalTaskRedirectLog);
                    break;
                }
                if (locationHeader.isEmpty()) {
                    normalTaskRedirectLog.setSuccess(false);
                    normalTaskRedirectLog.setErrMsg("Redirect status code received but no Location header found");
                    normalTaskRedirectLogList.add(normalTaskRedirectLog);
                    break;
                }
                final URI responseUrl = currentUrl.resolve(locationHeader.get());
                if (isLandingPage(responseUrl, landingPageUrl)) {
                    normalTaskRedirectLog.setSuccess(true);
                    normalTaskRedirectLog.setResponseUrl(responseUrl.toString());
                    normalTaskRedirectLogList.add(normalTaskRedirectLog);
                    try {
                        final HttpRequest landingRequest = buildBaseRequest(responseUrl, userAgent, DEVICE_TYPE_DESK).build();
                        directHttpClient.send(landingRequest, HttpResponse.BodyHandlers.discarding());
                    } catch (IOException | InterruptedException landingProbeException) {
                        if (landingProbeException instanceof InterruptedException) {
                            Thread.currentThread().interrupt();
                        }
                        log.warn("NORMAL_AUTO_TASK_LANDING_PROBE_FAILED adsId={} jobId={} responseUrl={} message={}",
                                adsNormalInfo.getId(),
                                jobId,
                                responseUrl,
                                landingProbeException.getMessage());
                    }
                    break;

                }
                normalTaskRedirectLog.setSuccess(false);
                normalTaskRedirectLog.setResponseUrl(responseUrl.toString());
                normalTaskRedirectLogList.add(normalTaskRedirectLog);
                currentUrl = responseUrl;
            }
            normalTaskRedirectLogRepository.saveAll(normalTaskRedirectLogList);
            NormalTaskRedirectLog successTask = normalTaskRedirectLogList.stream().filter(log -> log.getSuccess()).findFirst().orElse(null);
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
        } catch (IOException | InterruptedException e) {
            log.warn("NORMAL_AUTO_TASK_PROXY_IP_VERIFICATION_FAILED adsId={} jobId={} message={}", adsNormalInfo.getId(), jobId, e.getMessage());
        }



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

}
