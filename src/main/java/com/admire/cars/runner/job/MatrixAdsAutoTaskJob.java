package com.admire.cars.runner.job;

import com.admire.cars.runner.constant.Constant;
import com.admire.cars.runner.constant.StatusConstant;
import com.admire.cars.runner.entity.*;
import com.admire.cars.runner.repository.AdsMatrixInfoRepository;
import com.admire.cars.runner.repository.ShiftLinkRepository;
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

public class MatrixAdsAutoTaskJob extends AdsAutoTaskJob {


    private static final Logger log = LoggerFactory.getLogger(MatrixAdsAutoTaskJob.class);

    @Autowired
    private AdsMatrixInfoRepository adsMatrixInfoRepository;

    @Autowired
    private ShiftLinkRepository shiftLinkRepository;


    @Autowired
    private AdsHttpClientTool adsHttpClientTool;

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
        List<AdsMatrixAffiliateInfo> adsMatrixAffiliateInfoList = adsMatrixInfo.getAffiliateInfos();
        if (CollectionUtils.isEmpty(adsMatrixAffiliateInfoList)) {
            log.warn("MATRIX_AUTO_TASK_NO_AFFILIATE_INFO adsId={} Job Id:{}  message={}",
                    adsMatrixInfo.getId(), jobId, "No affiliate info found for this matrix ad");
            return;
        }
        ShiftLink lastGeneratedShiftLink = shiftLinkRepository.findLastShiftLink(adsMatrixInfo.getAdsOwner(), adsMatrixInfo.getCampainName(), Constant.ADS_TYPE_MATRIX);
        AdsMatrixAffiliateInfo adsMatrixAffiliateInfo = adsMatrixAffiliateInfoList.get(0);
        if (null != lastGeneratedShiftLink) {
            for (int index = 0; index < adsMatrixAffiliateInfoList.size(); index ++) {
                if (lastGeneratedShiftLink.getPlatformName().equals(adsMatrixAffiliateInfoList.get(index).getPlatformName())
                        && lastGeneratedShiftLink.getRemarks().equalsIgnoreCase(adsMatrixAffiliateInfoList.get(index).getRemarks())) {
                    int nextIndex = (index + 1) % adsMatrixAffiliateInfoList.size();
                    adsMatrixAffiliateInfo = adsMatrixAffiliateInfoList.get(nextIndex);
                    break;

                }
            }
        }
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
            shiftLink.setDisplayNumber(1L);
            shiftLink.setStatus(adsMatrixInfo.getStatus());
            shiftLink.setAdsOwner(adsMatrixInfo.getAdsOwner());
            shiftLink.setRemarks(adsMatrixAffiliateInfo.getRemarks());
            updateMatrixSuccessCounter(adsMatrixInfo.getId(), eventTime);
            shiftLinkRepository.save(shiftLink);

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
