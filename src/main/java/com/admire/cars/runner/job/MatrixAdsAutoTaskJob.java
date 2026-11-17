package com.admire.cars.runner.job;

import com.admire.cars.runner.constant.Constant;
import com.admire.cars.runner.constant.StatusConstant;
import com.admire.cars.runner.entity.*;
import com.admire.cars.runner.repository.AdsMatrixInfoRepository;
import com.admire.cars.runner.service.MatrixShiftLinkQuotaService;
import com.admire.cars.runner.service.ShiftLinkService;
import com.admire.cars.runner.util.AdsHttpClientTool;
import com.admire.cars.runner.util.AdsHttpResponseDto;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class MatrixAdsAutoTaskJob extends AdsAutoTaskJob {


    private static final Logger log = LoggerFactory.getLogger(MatrixAdsAutoTaskJob.class);

    @Autowired
    private AdsMatrixInfoRepository adsMatrixInfoRepository;

    @Autowired
    private ShiftLinkService shiftLinkService;

    @Autowired
    private MatrixShiftLinkQuotaService matrixShiftLinkQuotaService;

    @Autowired
    private AdsHttpClientTool adsHttpClientTool;

    @Autowired
    private com.admire.cars.runner.config.ShiftLinkClickConfig shiftLinkClickConfig;

    @Override
    protected void executeTask(JobExecutionContext context) {

        JobDataMap jobDataMap = context.getMergedJobDataMap();
        String jobId = resolveJobId(context, jobDataMap);
        Long adsId = resolveAdsId(jobId, jobDataMap);

        AdsMatrixInfo adsMatrixInfo = adsMatrixInfoRepository.findById(adsId).orElse(null);
        if (adsMatrixInfo == null) {
            log.warn("AUTO_JOB_SKIP_MISSING_ADS jobId={} adsId={}", jobId, adsId);
            return;
        }
        // 以数据库状态为准, 触发器残留时暂停任务也不能生成 / DB status is the source of truth; stale triggers must not generate
        if (!"RUNNING".equalsIgnoreCase(adsMatrixInfo.getStatus())) {
            log.info("MATRIX_AUTO_TASK_PAUSED_SKIP adsId={} campaign={} status={}",
                    adsId, adsMatrixInfo.getCampainName(), adsMatrixInfo.getStatus());
            return;
        }
        // 先清理昨日链, 再判断今日配额 / purge stale links first, then check today's quota
        if (!matrixShiftLinkQuotaService.allowGeneration(adsId, adsMatrixInfo.getAdsOwner(), adsMatrixInfo.getCampainName())) {
            log.info("MATRIX_AUTO_TASK_DAILY_LIMIT_SKIP adsId={} jobId={}", adsId, jobId);
            return;
        }
        List<AdsMatrixAffiliateInfo> adsMatrixAffiliateInfoList = adsMatrixInfo.getAffiliateInfos();
        if (CollectionUtils.isEmpty(adsMatrixAffiliateInfoList)) {
            log.warn("MATRIX_AUTO_TASK_NO_AFFILIATE_INFO adsId={} Job Id:{}  message={}",
                    adsMatrixInfo.getId(), jobId, "No affiliate info found for this matrix ad");
            return;
        }
        AdsMatrixAffiliateInfo adsMatrixAffiliateInfo = adsMatrixAffiliateInfoList.get(ThreadLocalRandom.current().nextInt(adsMatrixAffiliateInfoList.size()));
        AdsHttpResponseDto adsHttpResponseDto = adsHttpClientTool.applyAffiliateAd(adsMatrixInfo, adsMatrixAffiliateInfo);
        LocalDateTime eventTime = LocalDateTime.now();
        if (StatusConstant.SUCCESS.equals(adsHttpResponseDto.getStatus())) {
            ShiftLink shiftLink = new ShiftLink();
            shiftLink.setAdsId(adsMatrixInfo.getId());
            shiftLink.setAdsName(adsMatrixInfo.getCampainName());
            shiftLink.setAdsType(Constant.ADS_TYPE_MATRIX);
            shiftLink.setPlatformName(adsMatrixAffiliateInfo.getPlatformName());
            shiftLink.setLandingPageUrl(adsMatrixInfo.getLandingPageUrl());
            shiftLink.setFullUrl(adsHttpResponseDto.getUrl());
            shiftLink.setDisplayNumber(shiftLinkClickConfig.capacityFor(Constant.ADS_TYPE_MATRIX));
            // 链接状态固定 RUNNING, 停不停投由任务控制 / link status is always RUNNING; the ads task decides pausing
            shiftLink.setStatus("RUNNING");
            shiftLink.setAdsOwner(adsMatrixInfo.getAdsOwner());
            shiftLink.setRemarks(adsMatrixAffiliateInfo.getRemarks());
            shiftLink.setUserName(adsMatrixAffiliateInfo.getUserName());
            updateMatrixSuccessCounter(adsMatrixInfo.getId(), eventTime);
            // 走统一入口：URL 去重 + Gmail 归属兜底 + 统计 / unified entry: url dedupe, gmail fallback and stats
            try {
                ShiftLink saved = shiftLinkService.createShiftLink(shiftLink, null);
                log.info("[MatrixJob] shift link archived adsId={} shiftLinkId={} gmail={} platform={} matchId={}",
                        adsMatrixInfo.getId(), saved.getId(), saved.getUserName(), saved.getPlatformName(),
                        adsMatrixAffiliateInfo.getMatchId());
            } catch (IllegalArgumentException e) {
                log.warn("[MatrixJob] shift link skipped adsId={} affiliateId={} reason={}",
                        adsMatrixInfo.getId(), adsMatrixAffiliateInfo.getId(), e.getMessage());
            }
        } else {
            updateMatrixFailedCounter(adsMatrixInfo.getId(), eventTime);
        }
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
            if (source.startsWith("matrix-ads-task-")) {
                return parseAdsIdToken(source.substring("matrix-ads-task-".length()));
            }
            if (source.startsWith("ads-task-")) {
                return parseAdsIdToken(source.substring("ads-task-".length()));
            }
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
            throw new IllegalArgumentException("adsId is required for matrix ads job execution");
        }
        return jobDataAdsId;
    }

    private Long parseAdsIdToken(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("adsId is required for matrix ads job execution");
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("adsId is invalid for matrix ads job execution: " + value, ex);
        }
    }


    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private void updateMatrixSuccessCounter(Long adsId, LocalDateTime eventTime) {
        int updated = adsMatrixInfoRepository.incrementSuccessCount(adsId, eventTime);
        if (updated != 1) {
            throw new IllegalStateException("Failed to update matrix success counter for adsId=" + adsId);
        }
    }

    private void updateMatrixFailedCounter(Long adsId, LocalDateTime eventTime) {
        int updated = adsMatrixInfoRepository.incrementFailedCount(adsId, eventTime);
        if (updated != 1) {
            throw new IllegalStateException("Failed to update matrix failed counter for adsId=" + adsId);
        }
    }

}
