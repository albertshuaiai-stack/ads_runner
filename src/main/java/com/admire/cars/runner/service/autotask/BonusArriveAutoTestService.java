package com.admire.cars.runner.service.autotask;

import com.admire.cars.runner.constant.StatusConstant;
import com.admire.cars.runner.entity.AffiliateAds;
import com.admire.cars.runner.entity.AffiliateAutoTask;
import com.admire.cars.runner.entity.AffiliateTest;
import com.admire.cars.runner.repository.AffiliateAdsRepository;
import com.admire.cars.runner.repository.AffiliateAutoTaskRepository;
import com.admire.cars.runner.repository.AffiliateTestRepository;
import com.admire.cars.runner.service.proxy.IpProxyService;
import com.admire.cars.runner.service.proxy.UserAgentService;
import com.admire.cars.runner.util.AdsHttpClientTool;
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
import java.util.List;

@Service
public class BonusArriveAutoTestService {

    private static final Logger log = LoggerFactory.getLogger(BonusArriveAutoTestService.class);

    @Autowired
    private AffiliateAutoTaskRepository affiliateAutoTaskRepository;

    @Autowired
    private AffiliateAdsRepository affiliateAdsSyncRepository;

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

                AdsHttpResponseDto adsHttpResponseDto = adsHttpClientTool.applyAffiliateAd(affiliateAds, syncRegion);
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
    }

    private Long calculateDurationSeconds(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return null;
        }
        return Duration.between(start, end).getSeconds();
    }



}
