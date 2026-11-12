package com.admire.cars.runner.job;

import com.admire.cars.runner.constant.Constant;
import com.admire.cars.runner.constant.StatusConstant;
import com.admire.cars.runner.entity.AdsNormalInfo;
import com.admire.cars.runner.entity.ShiftLink;
import com.admire.cars.runner.repository.AdsNormalInfoRepository;
import com.admire.cars.runner.repository.ShiftLinkRepository;
import com.admire.cars.runner.service.AdsClickTrackService;
import com.admire.cars.runner.service.proxy.IpProxyService;
import com.admire.cars.runner.service.proxy.UserAgentService;
import com.admire.cars.runner.util.AdsHttpClientTool;
import com.admire.cars.runner.util.AdsHttpResponseDto;
import okhttp3.OkHttpClient;
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

    @Autowired
    private AdsClickTrackService adsClickTrackService;

    @Autowired
    private com.admire.cars.runner.config.ShiftLinkClickConfig shiftLinkClickConfig;

    @Autowired
    private com.admire.cars.runner.service.NormalShiftLinkQuotaService normalShiftLinkQuotaService;

    @Override
    protected void executeTask(JobExecutionContext context) {
        JobDataMap jobDataMap = context.getMergedJobDataMap();
        String jobId = resolveJobId(context, jobDataMap);
        Long adsId = resolveAdsId(jobId, jobDataMap);

        AdsNormalInfo adsNormalInfo = adsNormalInfoRepository.findById(adsId).orElse(null);
        if (adsNormalInfo == null) {
            log.warn("AUTO_JOB_SKIP_MISSING_ADS jobId={} adsId={}", jobId, adsId);
            return;
        }
        // 每天基础额度 + 点击增量额度, 用完后跳过本轮生成 / skip generation once today's allowance is used up
        if (!normalShiftLinkQuotaService.allowGeneration(adsNormalInfo)) {
            log.info("NORMAL_AUTO_TASK_DAILY_LIMIT_SKIP adsId={} jobId={}", adsId, jobId);
            return;
        }
        try {
            AdsHttpResponseDto adsHttpResponseDto = adsHttpClientTool.applyAffiliateAd(adsNormalInfo);
            if (StatusConstant.SUCCESS.equals(adsHttpResponseDto.getStatus())) {
                ShiftLink shiftLink = new ShiftLink();
                shiftLink.setAdsId(adsNormalInfo.getId());
                shiftLink.setAdsName(adsNormalInfo.getCampainName());
                shiftLink.setAdsType(Constant.ADS_TYPE_NORMAL);
                shiftLink.setPlatformName(adsNormalInfo.getPlatformName());
                shiftLink.setLandingPageUrl(adsNormalInfo.getLandingPageUrl());
                shiftLink.setFullUrl(adsHttpResponseDto.getUrl());
                shiftLink.setDisplayNumber(shiftLinkClickConfig.capacityFor(Constant.ADS_TYPE_NORMAL));
                shiftLink.setStatus(adsNormalInfo.getStatus());
                shiftLink.setAdsOwner(adsNormalInfo.getAdsOwner());
                ShiftLink savedLink = shiftLinkRepository.save(shiftLink);

                // 把本次的点击ID与 normaltask 绑定, 供后续 postback 归因 / bind this click id to the normal task
                adsClickTrackService.recordIssued(adsHttpResponseDto.getClickId(), adsNormalInfo,
                        savedLink, adsHttpResponseDto.getRequestUrl());

                adsNormalInfo.setSuccessCount(adsNormalInfo.getSuccessCount() + 1);
                adsNormalInfo.setLastSuccessDate(LocalDateTime.now());
            } else {
                adsNormalInfo.setFailedCount(adsNormalInfo.getFailedCount() + 1);
                log.warn("NORMAL_AUTO_TASK_FAILED adsId={} campaign={} status={} error={}",
                        adsId, adsNormalInfo.getCampainName(),
                        adsHttpResponseDto.getStatus(), adsHttpResponseDto.getError());
            }
        } catch (Exception e) {
            // 异常也必须计数, 否则页面成功/失败都是 0, 无法分辨任务是否在失败
            // count exceptions too, otherwise both counters stay 0 and failures are invisible
            adsNormalInfo.setFailedCount(adsNormalInfo.getFailedCount() + 1);
            log.error("NORMAL_AUTO_TASK_EXCEPTION adsId={} campaign={} error={}",
                    adsId, adsNormalInfo.getCampainName(), e.getMessage(), e);
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
