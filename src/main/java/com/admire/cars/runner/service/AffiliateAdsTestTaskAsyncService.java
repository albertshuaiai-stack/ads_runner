package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AffiliateAdsSync;
import com.admire.cars.runner.entity.AffiliateAdsSyncConfig;
import com.admire.cars.runner.entity.AffiliateAdsTestResult;
import com.admire.cars.runner.entity.AffiliateAdsTestTask;
import com.admire.cars.runner.repository.AffiliateAdsSyncConfigRepository;
import com.admire.cars.runner.repository.AffiliateAdsSyncRepository;
import com.admire.cars.runner.repository.AffiliateAdsTestTaskRepository;
import org.apache.commons.compress.utils.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AffiliateAdsTestTaskAsyncService {

    private static final Logger log = LoggerFactory.getLogger(AffiliateAdsTestTaskAsyncService.class);

    private final AffiliateAdsTestTaskRepository affiliateAdsTestTaskRepository;
    private final AffiliateAdsSyncConfigRepository affiliateAdsSyncConfigRepository;
    private final AffiliateAdsSyncRepository affiliateAdsSyncRepository;
    private final AffiliateAdsTestResultService affiliateAdsTestResultService;

    public AffiliateAdsTestTaskAsyncService(
            AffiliateAdsTestTaskRepository affiliateAdsTestTaskRepository,
            AffiliateAdsSyncConfigRepository affiliateAdsSyncConfigRepository,
            AffiliateAdsSyncRepository affiliateAdsSyncRepository,
            AffiliateAdsTestResultService affiliateAdsTestResultService) {
        this.affiliateAdsTestTaskRepository = affiliateAdsTestTaskRepository;
        this.affiliateAdsSyncConfigRepository = affiliateAdsSyncConfigRepository;
        this.affiliateAdsSyncRepository = affiliateAdsSyncRepository;
        this.affiliateAdsTestResultService = affiliateAdsTestResultService;
    }

    @Async("adsAsyncExecutor")
    @Transactional
    public void testAdsAsync(Long taskId, Long currentUserId) {
        AffiliateAdsTestTask task = affiliateAdsTestTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("AFFILIATE_ADS_TEST_TASK not found: " + taskId));
        AffiliateAdsSyncConfig config = affiliateAdsSyncConfigRepository.findById(task.getAffiliateAdsSyncConfigId())
                .orElseThrow(() -> new IllegalArgumentException("AFFILIATE_ADS_SYNC_CONFIG not found: " + task.getAffiliateAdsSyncConfigId()));

        try {
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
                try {
                    AffiliateAdsTestResult result = new AffiliateAdsTestResult();
                    result.setAffiliateNetwork(sync.getAffiliateNetwork());
                    result.setRegion(sync.getRegion());
                    result.setSiteName(sync.getSiteName());
                    result.setSiteUrl(sync.getSiteUrl());
                    result.setTrackingUrl(sync.getTrackingUrl());
                    result.setFinalUrl(resolveFinalUrl(sync));
                    result.setStatus(StringUtils.hasText(result.getFinalUrl()) ? "SUCCESS" : "FAILED");
                    result.setAdsOwner(task.getAdsOwner());
                    affiliateAdsTestResultService.create(result, currentUserId);
                    if ("SUCCESS".equalsIgnoreCase(result.getStatus())) {
                        successCount++;
                    } else {
                        failedCount++;
                    }
                } catch (Exception itemException) {
                    failedCount++;
                    log.warn("AFFILIATE_TEST_TASK_ITEM_FAILED taskId={} syncId={} message={}",
                            taskId, sync.getId(), itemException.getMessage());
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

    private String resolveFinalUrl(AffiliateAdsSync sync) {
        if (StringUtils.hasText(sync.getTrackingUrl())) {
            return sync.getTrackingUrl();
        }
        if (StringUtils.hasText(sync.getSiteUrl())) {
            return sync.getSiteUrl();
        }
        return null;
    }

    private Long calculateDurationSeconds(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return null;
        }
        return java.time.Duration.between(start, end).getSeconds();
    }
}
