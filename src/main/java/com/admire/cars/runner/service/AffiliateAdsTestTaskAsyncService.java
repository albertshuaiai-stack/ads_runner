package com.admire.cars.runner.service;

import com.admire.cars.runner.config.AdsConfig;
import com.admire.cars.runner.constant.Constant;
import com.admire.cars.runner.dto.AffiliateAdsTestResponseDto;
import com.admire.cars.runner.dto.IpVerificationDto;
import com.admire.cars.runner.dto.ProxyConfigurationDto;
import com.admire.cars.runner.entity.*;
import com.admire.cars.runner.repository.AffiliateAdsSyncConfigRepository;
import com.admire.cars.runner.repository.AffiliateAdsSyncRepository;
import com.admire.cars.runner.repository.AffiliateAdsTestTaskRepository;
import com.admire.cars.runner.repository.IpProxyInfoRepository;
import org.apache.commons.compress.utils.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Service
public class AffiliateAdsTestTaskAsyncService {

    private static final Logger log = LoggerFactory.getLogger(AffiliateAdsTestTaskAsyncService.class);

    private final AffiliateAdsTestTaskRepository affiliateAdsTestTaskRepository;
    private final AffiliateAdsSyncConfigRepository affiliateAdsSyncConfigRepository;
    private final AffiliateAdsSyncRepository affiliateAdsSyncRepository;
    private final AffiliateAdsTestResultService affiliateAdsTestResultService;
    private final AffiliateAdsService affiliateAdsService;
    private final IpProxyInfoRepository ipProxyInfoRepository;

    @Autowired
    private AdsConfig adsConfig;

    @Autowired
    private ObjectMapper objectMapper;

    public AffiliateAdsTestTaskAsyncService(
            AffiliateAdsTestTaskRepository affiliateAdsTestTaskRepository,
            AffiliateAdsSyncConfigRepository affiliateAdsSyncConfigRepository,
            AffiliateAdsSyncRepository affiliateAdsSyncRepository,
            AffiliateAdsTestResultService affiliateAdsTestResultService,
            AffiliateAdsService affiliateAdsService,
            IpProxyInfoRepository ipProxyInfoRepository) {
        this.affiliateAdsTestTaskRepository = affiliateAdsTestTaskRepository;
        this.affiliateAdsSyncConfigRepository = affiliateAdsSyncConfigRepository;
        this.affiliateAdsSyncRepository = affiliateAdsSyncRepository;
        this.affiliateAdsTestResultService = affiliateAdsTestResultService;
        this.affiliateAdsService = affiliateAdsService;
        this.ipProxyInfoRepository = ipProxyInfoRepository;
    }

    @Async("adsAsyncExecutor")
    @Transactional
    public void testAdsAsync(Long taskId, Long currentUserId) {
        AffiliateAdsTestTask task = affiliateAdsTestTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("AFFILIATE_ADS_TEST_TASK not found: " + taskId));
        AffiliateAdsSyncConfig config = affiliateAdsSyncConfigRepository.findById(task.getAffiliateAdsSyncConfigId())
                .orElseThrow(() -> new IllegalArgumentException("AFFILIATE_ADS_SYNC_CONFIG not found: " + task.getAffiliateAdsSyncConfigId()));
        IpProxyInfo ipProxyInfo = ipProxyInfoRepository.findById(task.getIpProxyInfoId())
                .orElseThrow(() -> new IllegalArgumentException("IP_PROXY_INFO not found: " + task.getIpProxyInfoId()));
        final OkHttpClient httpClient = buildHttpClient(ipProxyInfo);
        final OkHttpClient directHttpClient = buildHttpClient(null);
        try {
            if (adsConfig.isIpVerification()) {
                try {
                    final IpVerificationDto ipVerification = ipVerification(httpClient, task.getRegion());
                    if (!ipVerification.isMatched()) {
                        log.warn("IP proxy didn't match the expected region. Expected: {}, Actually:{}.  proxy protocol={} proxy info={} Ip Lookup URL={}",
                                task.getRegion(), ipVerification.getCountryCode(), ipProxyInfo.getProxyProtocol(), ipProxyInfo.getProxyInfo(), adsConfig.getIpLookupUrl());
                        task.setTotalCount(0L);
                        task.setSuccessCount(0L);
                        task.setFailedCount(0L);
                        task.setPreEndDate(LocalDateTime.now());
                        task.setPreDuration(calculateDurationSeconds(task.getPreStartDate(), task.getPreEndDate()));
                        task.setStatus("FAILED");
                        task.setUpdateDate(LocalDateTime.now());
                        affiliateAdsTestTaskRepository.save(task);
                        return;
                    }
                } catch (IOException e) {
                    String message = e.getMessage();
                    if (StringUtils.hasText(message) && message.contains("407")) {
                        log.warn("Affiliate Ads Test IP VERIFICATION proxy auth required (407), falling back to direct. proxy protocol={} proxy info={} Ip Lookup URL={}",
                                ipProxyInfo.getProxyProtocol(), ipProxyInfo.getProxyInfo(), adsConfig.getIpLookupUrl());
                        try {
                            final IpVerificationDto directVerification = ipVerification(directHttpClient, task.getRegion());
                            if (!directVerification.isMatched()) {
                                log.warn("IP proxy didn't match the expected region (via direct lookup). Expected: {}, Actually:{}. proxy protocol={} proxy info={} Ip Lookup URL={}",
                                        task.getRegion(), directVerification.getCountryCode(), ipProxyInfo.getProxyProtocol(), ipProxyInfo.getProxyInfo(), adsConfig.getIpLookupUrl());
                                task.setTotalCount(0L);
                                task.setSuccessCount(0L);
                                task.setFailedCount(0L);
                                task.setPreEndDate(LocalDateTime.now());
                                task.setPreDuration(calculateDurationSeconds(task.getPreStartDate(), task.getPreEndDate()));
                                task.setStatus("FAILED");
                                task.setUpdateDate(LocalDateTime.now());
                                affiliateAdsTestTaskRepository.save(task);
                                return;
                            }
                        } catch (IOException directException) {
                            log.warn("Affiliate Ads Test IP VERIFICATION direct fallback failed, skipping verification. message={}",
                                    directException.getMessage());
                        }
                    } else {
                        log.error("Affiliate Ads Test IP VERIFICATION Exception. proxy protocol={} proxy info={} Ip Lookup URL={} message={}",
                                ipProxyInfo.getProxyProtocol(), ipProxyInfo.getProxyInfo(), adsConfig.getIpLookupUrl(), message);
                        task.setTotalCount(0L);
                        task.setSuccessCount(0L);
                        task.setFailedCount(0L);
                        task.setPreEndDate(LocalDateTime.now());
                        task.setPreDuration(calculateDurationSeconds(task.getPreStartDate(), task.getPreEndDate()));
                        task.setStatus("FAILED");
                        task.setUpdateDate(LocalDateTime.now());
                        affiliateAdsTestTaskRepository.save(task);
                        return;
                    }
                }
            }

            List<AffiliateAdsSync> syncs = affiliateAdsSyncRepository.findAll((root, query, cb) -> {
                List<jakarta.persistence.criteria.Predicate> predicates = Lists.newArrayList();
                predicates.add(cb.equal(root.get("affiliateNetwork"), config.getAffiliateNetwork()));
                predicates.add(cb.equal(root.get("adsOwner"), task.getAdsOwner()));
                predicates.add(cb.equal(cb.lower(root.get("status")), "enabled"));
                if (StringUtils.hasText(task.getRegion())) {
                    predicates.add(cb.equal(cb.lower(root.get("region")), task.getRegion().trim().toLowerCase()));
                }
                return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
            });

            long successCount = 0L;
            long failedCount = 0L;
            long totalCount = 0L;

            for (AffiliateAdsSync sync : syncs) {
                totalCount++;
                if (!StringUtils.hasText(sync.getTrackingUrl()) || !StringUtils.hasText(sync.getSiteUrl())) {
                    continue;
                }
                AffiliateAdsTestResponseDto affiliateAdsTestResponseDto = affiliateAdsService.getAffiliateAds(httpClient, sync, ipProxyInfo);
                if (null != affiliateAdsTestResponseDto && "200".equalsIgnoreCase(affiliateAdsTestResponseDto.getStatus())) {
                    AffiliateAdsTestResult result = new AffiliateAdsTestResult();
                    result.setAffiliateNetwork(sync.getAffiliateNetwork());
                    result.setRegion(sync.getRegion());
                    result.setSiteName(sync.getSiteName());
                    result.setSiteUrl(sync.getSiteUrl());
                    result.setTrackingUrl(sync.getTrackingUrl());
                    result.setFinalUrl(affiliateAdsTestResponseDto.getUrl());
                    result.setStatus("SUCCESS");
                    result.setAdsOwner(task.getAdsOwner());
                    affiliateAdsTestResultService.create(result, currentUserId);
                    successCount++;
                } else {
                    failedCount++;
                    AffiliateAdsTestResult result = new AffiliateAdsTestResult();
                    result.setAffiliateNetwork(sync.getAffiliateNetwork());
                    result.setRegion(sync.getRegion());
                    result.setSiteName(sync.getSiteName());
                    result.setSiteUrl(sync.getSiteUrl());
                    result.setTrackingUrl(sync.getTrackingUrl());
                    result.setFinalUrl(affiliateAdsTestResponseDto != null ? affiliateAdsTestResponseDto.getUrl() : null);
                    result.setStatus("FAILED");
                    result.setAdsOwner(task.getAdsOwner());
                    affiliateAdsTestResultService.create(result, currentUserId);
                }
            }
            task.setTotalCount(totalCount);
            task.setSuccessCount(successCount);
            task.setFailedCount(failedCount);
            task.setPreEndDate(LocalDateTime.now());
            task.setPreDuration(calculateDurationSeconds(task.getPreStartDate(), task.getPreEndDate()));
            task.setStatus("COMPLETED");
            task.setUpdateDate(LocalDateTime.now());
            affiliateAdsTestTaskRepository.save(task);
            log.info("AFFILIATE_TEST_TASK_COMPLETED taskId={} total={}", taskId, totalCount);
        } catch (Exception e) {
            task.setPreEndDate(LocalDateTime.now());
            task.setPreDuration(calculateDurationSeconds(task.getPreStartDate(), task.getPreEndDate()));
            task.setStatus("FAILED");
            task.setFailedCount(task.getFailedCount() == null ? 1L : Math.max(1L, task.getFailedCount()));
            task.setUpdateDate(LocalDateTime.now());
            affiliateAdsTestTaskRepository.save(task);
            log.error("AFFILIATE_TEST_TASK_FAILED taskId={}: {}", taskId, e.getMessage(), e);
        }
    }


    private Long calculateDurationSeconds(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return null;
        }
        return java.time.Duration.between(start, end).getSeconds();
    }


    private OkHttpClient buildHttpClient(IpProxyInfo dynamicProxyInfo) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(adsConfig.getConnectTimeoutMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(adsConfig.getRequestTimeoutMillis(), TimeUnit.MILLISECONDS);

        if (dynamicProxyInfo == null || !StringUtils.hasText(dynamicProxyInfo.getProxyInfo())) {
            return builder.build();
        }

        Optional<ProxyConfigurationDto> p = parseProxyConfiguration(dynamicProxyInfo.getProxyInfo()); // user:pass@host:port
        if (p.isEmpty()) {
            return builder.build();
        }

        log.info("Building OkHttpClient with SOCKS5 proxy: {}:{} (protocol: {})",
                p.get().getHost(), p.get().getPort(), dynamicProxyInfo.getProxyProtocol());

        // For SOCKS5, use Java Authenticator (RFC 1929) instead of HTTP Basic Auth
        java.net.Authenticator.setDefault(new java.net.Authenticator() {
            @Override
            protected java.net.PasswordAuthentication getPasswordAuthentication() {
                log.debug("Authenticator called for {}:{} (type: {})",
                        getRequestingHost(), getRequestingPort(), getRequestorType());
                if (getRequestorType() == RequestorType.PROXY || 
                    getRequestorType() == RequestorType.SERVER) {
                    log.debug("Providing SOCKS5 credentials for {}:{}",
                            getRequestingHost(), getRequestingPort());
                    return new java.net.PasswordAuthentication(
                            p.get().getUsername(),
                            p.get().getPassword().toCharArray()
                    );
                }
                return null;
            }
        });

        // Use SOCKS5 proxy with OkHttp (supports SOCKS5 natively)
        builder.proxy(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(p.get().getHost(), p.get().getPort())));

        return builder.build();
    }

    private Optional<ProxyConfigurationDto> parseProxyConfiguration(String proxyInfo) {
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
            return Optional.of(new ProxyConfigurationDto(
                    credentials[0].trim(),
                    credentials[1].trim(),
                    hostAndPort[0].trim(),
                    Integer.parseInt(hostAndPort[1].trim())));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("dynamicProxyInfo port must be numeric", ex);
        }
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
        if (StringUtils.hasText(adsConfig.getIpLookupUrl())) {
            configuredUrl = adsConfig.getIpLookupUrl().trim();
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

    private void validateHttpUrl(final String url, final String fieldName) {
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException(fieldName + " URL is required");
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException(fieldName + " URL must use http or https scheme");
        }
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

}
