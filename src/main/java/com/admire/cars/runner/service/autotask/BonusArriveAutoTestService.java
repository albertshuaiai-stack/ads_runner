package com.admire.cars.runner.service.autotask;

import com.admire.cars.runner.config.AutoTaskConfig;
import com.admire.cars.runner.constant.Constant;
import com.admire.cars.runner.constant.StatusConstant;
import com.admire.cars.runner.dto.AffiliateAdsTestResponseDto;
import com.admire.cars.runner.dto.IpVerificationDto;
import com.admire.cars.runner.entity.AffiliateAds;
import com.admire.cars.runner.entity.AffiliateAutoTask;
import com.admire.cars.runner.entity.AffiliateTest;
import com.admire.cars.runner.entity.IpProxyInfo;
import com.admire.cars.runner.repository.AffiliateAdsRepository;
import com.admire.cars.runner.repository.AffiliateAutoTaskRepository;
import com.admire.cars.runner.repository.AffiliateTestRepository;
import com.admire.cars.runner.repository.IpProxyInfoRepository;
import com.admire.cars.runner.service.proxy.IpProxyService;
import jakarta.persistence.criteria.Predicate;
import org.apache.commons.compress.utils.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Service
public class BonusArriveAutoTestService {

    private static final Logger log = LoggerFactory.getLogger(BonusArriveAutoTestService.class);

    private static final List<Integer> REDIRECT_STATUS_CODES = List.of(200,301, 302, 303, 307, 308);

    private final AffiliateAutoTaskRepository affiliateAutoTaskRepository;

    private final AffiliateAdsRepository affiliateAdsSyncRepository;

    private final IpProxyInfoRepository ipProxyInfoRepository;

    private final AffiliateTestRepository affiliateTestRepository;

    private final IpProxyService ipProxyService;

    @Autowired
    private AutoTaskConfig adsConfig;


    public BonusArriveAutoTestService(
            AffiliateAutoTaskRepository affiliateAutoTaskRepository,
            AffiliateAdsRepository affiliateAdsSyncRepository,
            IpProxyInfoRepository ipProxyInfoRepository,
            AffiliateTestRepository affiliateTestRepository,
            IpProxyService ipProxyService) {
        this.affiliateAutoTaskRepository = affiliateAutoTaskRepository;
        this.affiliateAdsSyncRepository = affiliateAdsSyncRepository;
        this.ipProxyInfoRepository = ipProxyInfoRepository;
        this.affiliateTestRepository = affiliateTestRepository;
        this.ipProxyService = ipProxyService;
    }

    @Transactional
    public void testAdsAsync(Long taskId) {
        AffiliateAutoTask task = affiliateAutoTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("AFFILIATE_AUTO_TASK not found: " + taskId));
        long successCount = 0L;
        long failedCount = 0L;
        long totalCount = 0L;
        List<AffiliateAds> syncs = affiliateAdsSyncRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = Lists.newArrayList();
            predicates.add(cb.equal(root.get("affiliateNetwork"), task.getAffiliateNetwork()));
            predicates.add(cb.equal(root.get("adsOwner"), task.getAdsOwner()));
            predicates.add(cb.equal(cb.lower(root.get("status")), StatusConstant.TO_BE_TEST.toLowerCase()));
            if (StringUtils.hasText(task.getRegion())) {
                predicates.add(cb.equal(cb.lower(root.get("region")), task.getRegion().trim().toLowerCase()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        });

        if (CollectionUtils.isEmpty(syncs)) {
            log.info("AFFILIATE_TEST_TASK_NO_SYNC taskId={} affiliateNetwork={} adsOwner={} region={}",
                    taskId, task.getAffiliateNetwork(), task.getAdsOwner(), task.getRegion());
            task.setTotalCount(totalCount);
            task.setSuccessCount(successCount);
            task.setFailedCount(failedCount);
            task.setEndDate(LocalDateTime.now());
            task.setDuration(calculateDurationSeconds(task.getStartDate(), task.getEndDate()));
            task.setStatus(StatusConstant.COMPLETED);
            task.setUpdateDate(LocalDateTime.now());
            affiliateAutoTaskRepository.save(task);
            return;
        }
        String syncRegion = task.getRegion() == null ? null : task.getRegion().trim();
        long deleted = affiliateTestRepository.deleteByAffiliateNetworkAndAdsOwnerAndRegion(
                task.getAffiliateNetwork(),
                task.getAdsOwner(),
                syncRegion);
        log.info("AFFILIATE_TEST_TASK_CLEANUP taskId={} deleted={} affiliateNetwork={} adsOwner={} region={}",
                taskId, deleted, task.getAffiliateNetwork(), task.getAdsOwner(), syncRegion);

        List<IpProxyInfo> proxies = ipProxyInfoRepository.findByAdsOwnerAndStatusIgnoreCaseAndProxyTypeAndProxyProtocolOrderByIdDesc(
                task.getAdsOwner(),
                StatusConstant.ENABLED,
                Constant.PROXY_TYPE_DYNAMIC,
                Constant.PROXY_PROTOCOL_SOCKETS5);
        if (proxies.isEmpty()) {
            throw new IllegalArgumentException("No ENABLED IP_PROXY_INFO found for adsOwner: " + task.getAdsOwner());
        }
        OkHttpClient httpClient = null;
        IpProxyInfo ipProxyInfo = null;
        IpVerificationDto ipVerification = null;
        final List<String> proxyFailures = new ArrayList<>();
        for (IpProxyInfo proxy : proxies) {
            httpClient = ipProxyService.buildOkHttpClient(proxy.getProxyInfo());
            try {
                ipVerification = ipProxyService.ipVerification4OkHttpClient(httpClient, task.getRegion());
                ipProxyInfo = proxy;
                if (!StringUtils.hasText(task.getRegion())) {
                    ipVerification.setMatched(true);
                    break;
                } else if (!ipVerification.isMatched()) {
                    proxyFailures.add("proxyId=" + ipProxyInfo.getId()
                            + " region mismatch expected=" + task.getRegion()
                            + " actual=" + ipVerification.getCountryCode());
                }
            } catch (IOException e) {
                proxyFailures.add("proxyId=" + ipProxyInfo.getId() + " verification failed: " + e.getMessage());
            }
        }

        if (null != ipVerification && ipVerification.isMatched()) {
            log.info("AFFILIATE_TEST_TASK_PROXY_VERIFIED taskId={} proxyId={} proxyInfo={} region={}, ipVerification: {}",
                    taskId, ipProxyInfo.getId(), ipProxyInfo.getProxyInfo(), task.getRegion(), ipVerification);
            try {
                for (AffiliateAds sync : syncs) {
                    totalCount++;
                    sync.setStatus(StatusConstant.TESTING);
                    affiliateAdsSyncRepository.save(sync);
                    try {
                        if (!StringUtils.hasText(sync.getTrackingUrl()) || !StringUtils.hasText(sync.getSiteUrl())) {
                            failedCount++;
                            sync.setStatus(StatusConstant.TEST_FAILED);
                        } else {
                            AffiliateAdsTestResponseDto affiliateAdsTestResponseDto = this.applyTestAffiliateAd(httpClient, sync, ipProxyInfo);
                            if (null == affiliateAdsTestResponseDto) {
                                failedCount++;
                                sync.setStatus(StatusConstant.TEST_FAILED);
                            } else {
                                AffiliateTest result = new AffiliateTest();
                                result.setAffiliateNetwork(sync.getAffiliateNetwork());
                                result.setRegion(sync.getRegion());
                                result.setSiteName(sync.getSiteName());
                                result.setSiteUrl(sync.getSiteUrl());
                                result.setTrackingUrl(sync.getTrackingUrl());
                                result.setFinalUrl(affiliateAdsTestResponseDto.getUrl());
                                result.setStatus(affiliateAdsTestResponseDto.getStatus());
                                result.setAdsOwner(task.getAdsOwner());
                                affiliateTestRepository.save(result);
                                if (StatusConstant.SUCCESS.equalsIgnoreCase(affiliateAdsTestResponseDto.getStatus())) {
                                    successCount++;
                                    sync.setStatus(StatusConstant.TEST_SUCCESS);
                                } else {
                                    failedCount++;
                                    sync.setStatus(StatusConstant.TEST_FAILED);
                                }
                            }
                        }
                    } catch (Exception e) {
                        failedCount++;
                        sync.setStatus(StatusConstant.TEST_FAILED);
                        log.warn("AFFILIATE_TEST_TASK failed for syncId={} taskId={} error={}", sync.getId(), taskId, e.getMessage(), e);
                    }
                    affiliateAdsSyncRepository.save(sync);
                    task.setTotalCount(totalCount);
                    task.setSuccessCount(successCount);
                    task.setFailedCount(failedCount);
                    task.setEndDate(LocalDateTime.now());
                    task.setDuration(calculateDurationSeconds(task.getStartDate(), task.getEndDate()));
                    task.setStatus(StatusConstant.COMPLETED);
                    task.setUpdateDate(LocalDateTime.now());
                    affiliateAutoTaskRepository.save(task);
                }
            } catch (Exception e) {
                log.info("AFFILIATE_TEST_TASK Failed taskId={} affiliateNetwork={} adsOwner={} region={} error message={}",
                        taskId, task.getAffiliateNetwork(), task.getAdsOwner(), syncRegion, e.getMessage());
                task.setTotalCount(totalCount);
                task.setSuccessCount(successCount);
                task.setFailedCount(failedCount);
                task.setEndDate(LocalDateTime.now());
                task.setDuration(calculateDurationSeconds(task.getStartDate(), task.getEndDate()));
                task.setStatus(StatusConstant.FAILED);
                task.setUpdateDate(LocalDateTime.now());
                affiliateAutoTaskRepository.save(task);
            }
            log.info("AFFILIATE_TEST_TASK_COMPLETED taskId={} total={}", taskId, totalCount);
        } else {
            String errorMessage = "No valid proxy found for adsOwner: " + task.getAdsOwner();
            if (!proxyFailures.isEmpty()) {
                errorMessage += ". Proxy failures: " + String.join("; ", proxyFailures);
            }
            throw new IllegalArgumentException(errorMessage);
        }
    }


    private Long calculateDurationSeconds(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return null;
        }
        return Duration.between(start, end).getSeconds();
    }



    public AffiliateAdsTestResponseDto applyTestAffiliateAd(OkHttpClient httpClient,
                                                       AffiliateAds affiliateAdsSync,
                                                       IpProxyInfo ipProxyInfo) {
        final String landingPageUrl = requireText(affiliateAdsSync.getSiteUrl(), "landingPageUrl is required");
        String affiliateUrl = requireText(affiliateAdsSync.getTrackingUrl(), "tracking id is required");
        URI currentUrl = toRequestUri(affiliateUrl, "affiliteUrl");
        String lastError = null;
        int lastStatusCode = -1;
        URI responseUrl = null;
        final int maxRequests = 3;
        for (int requestCount = 1; requestCount <= maxRequests; requestCount++) {
            Request request = new Request.Builder()
                    .url(currentUrl.toString())
                    .header("Accept", "text/html, application/json, text/plain, */*")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Cache-Control", "no-cache")
                    .header("Pragma", "no-cache")
                    .header("X-Device-Type", Constant.DEVICE_TYPE_DESK)
                    .header("User-Agent", Constant.DEFAULT_DESKTOP_USER_AGENT)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                lastStatusCode = response.code();
                if (null != response.request()
                        && null != response.request().url()) {
                    responseUrl = URI.create(response.request().url().toString());
                }
                URI effectiveUrl = responseUrl != null ? responseUrl : currentUrl;
                log.warn("Affiliate Ads request attempt={}/{}. proxy protocol={} proxy info={} Request URL={} Response={}",
                        requestCount, maxRequests,
                        ipProxyInfo.getProxyProtocol(), ipProxyInfo.getProxyInfo(), currentUrl, responseUrl);
                if (REDIRECT_STATUS_CODES.contains(lastStatusCode)) {
                    if (isLandingPage(effectiveUrl, landingPageUrl)
                            && lastStatusCode >= 200
                            && lastStatusCode < 300) {
                        return new AffiliateAdsTestResponseDto(StatusConstant.SUCCESS, effectiveUrl.toString(), "");
                    }
                    currentUrl = effectiveUrl;
                }
            } catch (IOException proxyIoException) {
                lastError = proxyIoException.getMessage();
                log.warn("Affiliate Ads Test request failed (attempt {}/{}). proxy protocol={} proxy info={} Invoke URL={} message={}",
                        requestCount, maxRequests,
                        ipProxyInfo.getProxyProtocol(), ipProxyInfo.getProxyInfo(), currentUrl, lastError);
            }
        }
        String error = "Maximum request limit reached (" + maxRequests + ") without reaching landing page prefix";
        if (lastStatusCode > 0) {
            error = error + ". Last status=" + lastStatusCode;
        }
        if (StringUtils.hasText(lastError)) {
            error = error + ". Last error=" + lastError;
        }
        return new AffiliateAdsTestResponseDto(StatusConstant.FAILED, responseUrl != null ? responseUrl.toString() : "", error);
    }

    private URI toRequestUri(String value, String fieldName) {
        String normalized = requireText(value, fieldName + " is required");
        try {
            return URI.create(normalized);
        } catch (IllegalArgumentException ex) {
            String sanitized = normalized.replace(" ", "%20").replace("|", "%7C");
            try {
                return URI.create(sanitized);
            } catch (IllegalArgumentException nested) {
                throw new IllegalArgumentException(fieldName + " is invalid URL: " + normalized, nested);
            }
        }
    }

    private String resolveUrl(String baseUrl, String relativeUrl) throws IOException {
        if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
            return relativeUrl;
        }
        try {
            java.net.URI base = java.net.URI.create(baseUrl);
            java.net.URI resolved = base.resolve(relativeUrl);
            return resolved.toString();
        } catch (Exception e) {
            throw new IOException("Failed to resolve URL: " + e.getMessage(), e);
        }
    }

    private boolean isLandingPage(final URI uri, final String landingPage) {
        return uri.toString().startsWith(landingPage);
    }


    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }


}
