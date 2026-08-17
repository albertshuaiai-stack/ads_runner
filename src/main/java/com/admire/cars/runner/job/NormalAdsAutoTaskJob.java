package com.admire.cars.runner.job;

import com.admire.cars.runner.constant.Constant;
import com.admire.cars.runner.constant.StatusConstant;
import com.admire.cars.runner.dto.IpVerificationDto;
import com.admire.cars.runner.entity.AdsNormalInfo;
import com.admire.cars.runner.entity.AdsTaskLog;
import com.admire.cars.runner.entity.ShiftLink;
import com.admire.cars.runner.repository.AdsNormalInfoRepository;
import com.admire.cars.runner.repository.AdsTaskLogRepository;
import com.admire.cars.runner.repository.ShiftLinkRepository;
import com.admire.cars.runner.service.proxy.IpProxyService;
import com.admire.cars.runner.service.proxy.UserAgentService;
import com.admire.cars.runner.util.AdsHttpClientTool;
import com.admire.cars.runner.util.AdsHttpRequestDto;
import com.admire.cars.runner.util.AdsHttpResponseDto;
import okhttp3.OkHttpClient;
import org.apache.commons.compress.utils.Lists;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import java.net.*;
import java.time.LocalDateTime;
import java.util.*;

public class NormalAdsAutoTaskJob extends AdsAutoTaskJob {

    private static final Logger log = LoggerFactory.getLogger(NormalAdsAutoTaskJob.class);


    @Autowired
    private AdsNormalInfoRepository adsNormalInfoRepository;

    @Autowired
    private ShiftLinkRepository shiftLinkRepository;

    @Autowired
    private AdsHttpClientTool adsHttpClientTool;

    @Override
    protected void executeTask(JobExecutionContext context) {
        JobDataMap jobDataMap = context.getMergedJobDataMap();
        String jobId = resolveJobId(context, jobDataMap);
        Long adsId = resolveAdsId(jobId, jobDataMap);

        AdsNormalInfo adsNormalInfo = adsNormalInfoRepository.findById(adsId)
                .orElseThrow(() -> new IllegalArgumentException("ADS_NORMAL_INFO not found: " + adsId));
        AdsHttpResponseDto adsHttpResponseDto = adsHttpClientTool.applyAffiliateAd(adsNormalInfo);
        if (StatusConstant.SUCCESS.equals(adsHttpResponseDto.getStatus())) {
            ShiftLink shiftLink = new ShiftLink();
            shiftLink.setAdsId(adsNormalInfo.getId());
            shiftLink.setAdsName(adsNormalInfo.getCampainName());
            shiftLink.setAdsType(Constant.ADS_TYPE_NORMAL);
            shiftLink.setPlatformName(adsNormalInfo.getPlatformName());
            shiftLink.setLandingPageUrl(adsNormalInfo.getLandingPageUrl());
            shiftLink.setFullUrl(adsHttpResponseDto.getUrl());
            shiftLink.setDisplayNumber(1L);
            shiftLink.setStatus(adsNormalInfo.getStatus());
            shiftLink.setAdsOwner(adsNormalInfo.getAdsOwner());
            shiftLinkRepository.save(shiftLink);

            adsNormalInfo.setSuccessCount(adsNormalInfo.getSuccessCount() + 1);
            adsNormalInfo.setLastSuccessDate(LocalDateTime.now());
        } else {
            adsNormalInfo.setFailedCount(adsNormalInfo.getFailedCount() + 1);
        }
        adsNormalInfoRepository.save(adsNormalInfo);
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

}
