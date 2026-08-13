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
    private AdsTaskLogRepository adsTaskLogRepository;

    @Autowired
    private IpProxyService ipProxyService;

    @Autowired
    private UserAgentService userAgentService;

    @Autowired
    private AdsHttpClientTool adsHttpClientTool;

    @Override
    protected void executeTask(JobExecutionContext context) {

        List<AdsTaskLog> adsTaskLogList = Lists.newArrayList();
        JobDataMap jobDataMap = context.getMergedJobDataMap();
        String jobId = resolveJobId(context, jobDataMap);
        Long adsId = resolveAdsId(jobId, jobDataMap);

        AdsNormalInfo adsNormalInfo = adsNormalInfoRepository.findById(adsId)
                .orElseThrow(() -> new IllegalArgumentException("ADS_NORMAL_INFO not found: " + adsId));

        String userAgent = userAgentService.getUserAgent();
        String affiliateUrl = requireText(adsNormalInfo.getAffiliteUrl(), "affiliteUrl is required");
        final String landingPageUrl = requireText(adsNormalInfo.getLandingPageUrl(), "landingPageUrl is required");
        AdsTaskLog adsTaskLog = new AdsTaskLog();
        adsTaskLogList.add(adsTaskLog);
        final OkHttpClient okHttpClient = ipProxyService.buildOkHttpClient(adsNormalInfo.getDynamicProxyInfo());
        //Verify Http client IP region
        IpVerificationDto ipVerificationDto = ipProxyService.ipVerification4OkHttpClient(okHttpClient, adsNormalInfo.getCampainCountry());
        buildAdsTaskLog(adsTaskLog, adsNormalInfo,
                ipVerificationDto.getIp(), ipVerificationDto.getCountryCode(),
                0L, userAgent, null);
        if (ipVerificationDto.isMatched()) {
            adsTaskLog.setSuccess(true);
            AdsHttpRequestDto adsHttpRequestDto = new AdsHttpRequestDto(affiliateUrl,landingPageUrl,Constant.DEVICE_TYPE_DESK,userAgent);
            final long startTime = System.currentTimeMillis();
            adsTaskLog = new AdsTaskLog();
            adsTaskLogList.add(adsTaskLog);
            buildAdsTaskLog(adsTaskLog, adsNormalInfo,
                    (null != ipVerificationDto) ? ipVerificationDto.getIp() : null,
                    (null != ipVerificationDto) ? ipVerificationDto.getCountryCode() : null,
                    1L, userAgent, affiliateUrl);
            AdsHttpResponseDto adsHttpResponseDto = adsHttpClientTool.applyAffiliateAd(okHttpClient, adsHttpRequestDto);

            final long durationMillis = System.currentTimeMillis() - startTime;
            adsTaskLog.setDurationMillis(String.valueOf(durationMillis));
            adsTaskLog.setStatusCode(String.valueOf(adsHttpResponseDto.getCode()));
            adsTaskLog.setResponseUrl(adsHttpResponseDto.getUrl());
            adsTaskLog.setErrMsg(adsHttpResponseDto.getError());
            if (StatusConstant.SUCCESS.equals(adsHttpResponseDto.getStatus())) {
                ShiftLink shiftLink = new ShiftLink();
                shiftLink.setAdsId(adsNormalInfo.getId());
                shiftLink.setAdsName(adsNormalInfo.getCampainName());
                shiftLink.setAdsType(Constant.ADS_TYPE_NORMAL);
                shiftLink.setPlatformName(adsNormalInfo.getPlatformName());
                shiftLink.setLandingPageUrl(adsNormalInfo.getLandingPageUrl());
                shiftLink.setFullUrl(adsHttpResponseDto.getUrl());
                shiftLink.setDisplayNumber(5L);
                shiftLink.setStatus(adsNormalInfo.getStatus());
                shiftLink.setAdsOwner(adsNormalInfo.getAdsOwner());
                shiftLinkRepository.save(shiftLink);
                adsTaskLog.setSuccess(true);

                adsNormalInfo.setSuccessCount(adsNormalInfo.getSuccessCount() + 1);
                adsNormalInfo.setLastSuccessDate(LocalDateTime.now());
            } else {
                adsTaskLog.setSuccess(false);
                adsNormalInfo.setFailedCount(adsNormalInfo.getFailedCount() + 1);
            }

        } else {
            adsTaskLog.setSuccess(false);
            adsTaskLog.setErrMsg("IP verification failed: expected country " + adsNormalInfo.getCampainCountry() + ", but got " + ipVerificationDto.getCountryCode());
        }

        adsNormalInfoRepository.save(adsNormalInfo);
        adsTaskLogRepository.saveAll(adsTaskLogList);
    }


    private void buildAdsTaskLog(AdsTaskLog adsTaskLog, AdsNormalInfo adsNormalInfo,
                                            String ip, String countryCode, Long sequence,String userAgent, String requestUrl) {
        adsTaskLog.setAdsOwner(adsNormalInfo.getAdsOwner());
        adsTaskLog.setAdsName(adsNormalInfo.getCampainName());
        adsTaskLog.setAdsType(Constant.ADS_TYPE_NORMAL);
        adsTaskLog.setPlatformName(adsNormalInfo.getPlatformName());
        adsTaskLog.setIp(ip);
        adsTaskLog.setCountryCode(countryCode);
        adsTaskLog.setDevice(Constant.DEVICE_TYPE_DESK);
        adsTaskLog.setUserAgent(userAgent);
        adsTaskLog.setSequence(sequence);
        adsTaskLog.setRequestUrl(requestUrl);
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


    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }


}
