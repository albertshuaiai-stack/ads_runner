package com.admire.cars.runner.service.autotask;

import com.admire.cars.runner.constant.Constant;
import com.admire.cars.runner.constant.StatusConstant;
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
import com.admire.cars.runner.service.proxy.UserAgentService;
import com.admire.cars.runner.util.AdsHttpClientTool;
import com.admire.cars.runner.util.AdsHttpRequestDto;
import com.admire.cars.runner.util.AdsHttpResponseDto;
import jakarta.persistence.criteria.Predicate;
import org.apache.commons.compress.utils.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;

@Service
public class BonusArriveAutoTestService {

    private static final Logger log = LoggerFactory.getLogger(BonusArriveAutoTestService.class);

    private static final List<Integer> REDIRECT_STATUS_CODES = List.of(200,301, 302, 303, 307, 308);

    @Autowired
    private AffiliateAutoTaskRepository affiliateAutoTaskRepository;

    @Autowired
    private AffiliateAdsRepository affiliateAdsSyncRepository;

    @Autowired
    private IpProxyInfoRepository ipProxyInfoRepository;

    @Autowired
    private AffiliateTestRepository affiliateTestRepository;

    @Autowired
    private IpProxyService ipProxyService;

    @Autowired
    private AdsHttpClientTool adsHttpClientTool;

    @Autowired
    private UserAgentService userAgentService;


    @Transactional
    public void testAdsAsync(Long taskId) {
        AffiliateAutoTask task = affiliateAutoTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("AFFILIATE_AUTO_TASK not found: " + taskId));
        long successCount = 0L;
        long failedCount = 0L;
        long totalCount = 0L;
        List<AffiliateAds> affiliateAdsList = affiliateAdsSyncRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = Lists.newArrayList();
            predicates.add(cb.equal(root.get("affiliateNetwork"), task.getAffiliateNetwork()));
            predicates.add(cb.equal(root.get("adsOwner"), task.getAdsOwner()));
            predicates.add(cb.equal(cb.lower(root.get("status")), StatusConstant.TO_BE_TEST.toLowerCase()));
            if (StringUtils.hasText(task.getRegion())) {
                predicates.add(cb.equal(cb.lower(root.get("region")), task.getRegion().trim().toLowerCase()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        });

        if (CollectionUtils.isEmpty(affiliateAdsList)) {
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
            ipVerification = ipProxyService.ipVerification4OkHttpClient(httpClient, task.getRegion());
            ipProxyInfo = proxy;
            if (ipVerification.isMatched()) {
                break;
            } else {
                proxyFailures.add("proxyId=" + ipProxyInfo.getId()
                        + " region mismatch expected=" + task.getRegion()
                        + " actual=" + ipVerification.getCountryCode());
            }
        }
        if (null != ipVerification && ipVerification.isMatched()) {
            String userAgent = userAgentService.getUserAgent();
            log.info("AFFILIATE_TEST_TASK_PROXY_VERIFIED taskId={} proxyId={} proxyInfo={} region={}, ipVerification: {}",
                    taskId, ipProxyInfo.getId(), ipProxyInfo.getProxyInfo(), task.getRegion(), ipVerification);
            try {
                for (AffiliateAds affiliateAds : affiliateAdsList) {
                    totalCount ++;
                    affiliateAds.setStatus(StatusConstant.TESTING);
                    affiliateAdsSyncRepository.save(affiliateAds);
                    AffiliateTest result = new AffiliateTest();
                    result.setAffiliateNetwork(affiliateAds.getAffiliateNetwork());
                    result.setRegion(affiliateAds.getRegion());
                    result.setSiteName(affiliateAds.getSiteName());
                    result.setSiteUrl(affiliateAds.getSiteUrl());
                    result.setTrackingUrl(affiliateAds.getTrackingUrl());
                    result.setAdsOwner(affiliateAds.getAdsOwner());

                    String region = affiliateAds.getRegion() == null ? null : affiliateAds.getRegion().trim();
                    log.info("AFFILIATE_TEST_TASK Proxy verification passed. Ad ID={} proxyId={} region={}, ipVerification:{}",
                            affiliateAds.getId(), ipProxyInfo.getId(), region, ipVerification);
                    AdsHttpRequestDto adsHttpRequestDto = new AdsHttpRequestDto(
                            affiliateAds.getTrackingUrl(),affiliateAds.getSiteUrl(),Constant.DEVICE_TYPE_DESK, userAgent);
                    AdsHttpResponseDto adsHttpResponseDto = adsHttpClientTool.applyAffiliateAd(httpClient, adsHttpRequestDto);
                    result.setFinalUrl(adsHttpResponseDto.getUrl());
                    result.setStatus(adsHttpResponseDto.getStatus());
                    if (StatusConstant.SUCCESS.equals(adsHttpResponseDto.getStatus())) {
                        successCount++;
                    } else {
                        failedCount++;
                    }
                    affiliateAds.setStatus(adsHttpResponseDto.getStatus());
                    affiliateAdsSyncRepository.save(affiliateAds);
                }
                task.setTotalCount(totalCount);
                task.setSuccessCount(successCount);
                task.setFailedCount(failedCount);
                task.setEndDate(LocalDateTime.now());
                task.setDuration(calculateDurationSeconds(task.getStartDate(), task.getEndDate()));
                task.setStatus(StatusConstant.COMPLETED);
                task.setUpdateDate(LocalDateTime.now());
                affiliateAutoTaskRepository.save(task);
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



}
