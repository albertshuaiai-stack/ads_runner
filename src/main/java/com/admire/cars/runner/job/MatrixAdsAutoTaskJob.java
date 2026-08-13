package com.admire.cars.runner.job;

import com.admire.cars.runner.constant.Constant;
import com.admire.cars.runner.constant.StatusConstant;
import com.admire.cars.runner.dto.IpVerificationDto;
import com.admire.cars.runner.entity.*;
import com.admire.cars.runner.repository.AdsMatrixInfoRepository;
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
    private AdsTaskLogRepository adsTaskLogRepository;

    @Autowired
    private IpProxyService ipProxyService;

    @Autowired
    private UserAgentService userAgentService;


    @Autowired
    private AdsHttpClientTool adsHttpClientTool;

    @Override
    protected void executeTask(JobExecutionContext context) {

        JobDataMap jobDataMap = context.getMergedJobDataMap();
        String jobId = resolveJobId(context, jobDataMap);
        Long adsId = resolveAdsId(jobId, jobDataMap);

        AdsMatrixInfo adsMatrixInfo = adsMatrixInfoRepository.findById(adsId)
                .orElseThrow(() -> new IllegalArgumentException("ADS_MATRIX_INFO not found: " + adsId));
        final String landingPageUrl = requireText(adsMatrixInfo.getLandingPageUrl(), "landingPageUrl is required");
        List<AdsMatrixAffiliateInfo> adsMatrixAffiliateInfoList = adsMatrixInfo.getAffiliateInfos();
        if (CollectionUtils.isEmpty(adsMatrixAffiliateInfoList)) {
            log.warn("MATRIX_AUTO_TASK_NO_AFFILIATE_INFO adsId={} Job Id:{}  message={}",
                    adsMatrixInfo.getId(), jobId, "No affiliate info found for this matrix ad");
            return;
        }
        String userAgent = userAgentService.getUserAgent();
        for (int affiliateIndex = 0; affiliateIndex < adsMatrixAffiliateInfoList.size(); affiliateIndex++) {
            List<AdsTaskLog> adsTaskLogList = Lists.newArrayList();
            List<ShiftLink> shiftLinkList = Lists.newArrayList();
            AdsTaskLog adsTaskLog = new AdsTaskLog();
            adsTaskLogList.add(adsTaskLog);
            AdsMatrixAffiliateInfo adsMatrixAffiliateInfo = adsMatrixAffiliateInfoList.get(affiliateIndex);
            final OkHttpClient okHttpClient = ipProxyService.buildOkHttpClient(adsMatrixInfo.getDynamicProxyInfo());
            // Verify IP for each affiliate
            IpVerificationDto ipVerificationDto = ipProxyService.ipVerification4OkHttpClient(okHttpClient, adsMatrixInfo.getCampainCountry());
            buildAdsTaskLog(adsTaskLog, adsMatrixInfo, null,
                    ipVerificationDto.getIp(), ipVerificationDto.getCountryCode(),
                    0L, userAgent, null);
            if (!ipVerificationDto.isMatched()) {
                log.warn("MATRIX_AUTO_TASK_IP_VERIFICATION_FAILED adsId={} affiliateIndex={} platform={} IP verification failed",
                        adsMatrixInfo.getId(), affiliateIndex, adsMatrixAffiliateInfo.getPlatformName());
                adsTaskLog.setErrMsg("IP verification failed for affiliate");
                adsTaskLog.setSuccess(false);
                continue;
            }
            adsTaskLog.setSuccess(true);
            AdsTaskLog adsTaskLog1 = new AdsTaskLog();
            adsTaskLogList.add(adsTaskLog1);
            buildAdsTaskLog(adsTaskLog1, adsMatrixInfo, adsMatrixAffiliateInfo.getPlatformName(),
                    (null != ipVerificationDto) ? ipVerificationDto.getIp() : null,
                    (null != ipVerificationDto) ? ipVerificationDto.getCountryCode() : null,
                    (long) (affiliateIndex + 1), userAgent, adsMatrixAffiliateInfo.getAffiliteUrl());
            AdsHttpRequestDto adsHttpRequestDto = new AdsHttpRequestDto(adsMatrixAffiliateInfo.getAffiliteUrl(), landingPageUrl, Constant.DEVICE_TYPE_DESK, userAgent);
            final long startTime = System.currentTimeMillis();
            AdsHttpResponseDto adsHttpResponseDto = adsHttpClientTool.applyAffiliateAd(okHttpClient, adsHttpRequestDto);
            final long durationMillis = System.currentTimeMillis() - startTime;
            adsTaskLog1.setDurationMillis(String.valueOf(durationMillis));
            adsTaskLog1.setStatusCode(String.valueOf(adsHttpResponseDto.getCode()));
            adsTaskLog1.setResponseUrl(adsHttpResponseDto.getUrl());
            adsTaskLog1.setErrMsg(adsHttpResponseDto.getError());
            if (StatusConstant.SUCCESS.equals(adsHttpResponseDto.getStatus())) {
                ShiftLink shiftLink = new ShiftLink();
                shiftLink.setAdsId(adsMatrixInfo.getId());
                shiftLink.setAdsName(adsMatrixInfo.getCampainName());
                shiftLink.setAdsType(Constant.ADS_TYPE_MATRIX);
                shiftLink.setPlatformName(adsMatrixAffiliateInfo.getPlatformName());
                shiftLink.setLandingPageUrl(adsMatrixInfo.getLandingPageUrl());
                shiftLink.setFullUrl(adsHttpResponseDto.getUrl());
                shiftLink.setDisplayNumber(5L);
                shiftLink.setStatus(adsMatrixInfo.getStatus());
                shiftLink.setAdsOwner(adsMatrixInfo.getAdsOwner());
                shiftLink.setRemarks(adsMatrixAffiliateInfo.getRemarks());
                shiftLinkList.add(shiftLink);

                adsTaskLog1.setSuccess(true);

                adsMatrixInfo.setSuccessCount(adsMatrixInfo.getSuccessCount() + 1);
                adsMatrixInfo.setLastSuccessDate(LocalDateTime.now());
            } else {
                adsMatrixInfo.setFailedCount(adsMatrixInfo.getFailedCount() + 1);
                adsTaskLog1.setSuccess(false);
            }
            adsTaskLogRepository.saveAll(adsTaskLogList);
            shiftLinkRepository.saveAll(shiftLinkList);
            adsMatrixInfoRepository.save(adsMatrixInfo);
            // Sleep 5 minutes before processing next affiliate
            if (affiliateIndex < adsMatrixAffiliateInfoList.size() - 1) {
                sleepBeforeNextAffiliate();
            }
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

    private void sleepBeforeNextAffiliate() {
        try {
            log.info("Sleeping for 5 minutes before processing next AdsMatrixAffiliateInfo...");
            Thread.sleep(5 * 60 * 1000); // 5 minutes in milliseconds
        } catch (InterruptedException e) {
            log.warn("Thread sleep interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    private void buildAdsTaskLog(AdsTaskLog adsTaskLog, AdsMatrixInfo adsMatrixInfo, String platformName,
                                            String ip, String countryCode, Long sequence, String userAgent, String requestUrl) {
        adsTaskLog.setAdsOwner(adsMatrixInfo.getAdsOwner());
        adsTaskLog.setAdsName(adsMatrixInfo.getCampainName());
        adsTaskLog.setAdsType(Constant.ADS_TYPE_MATRIX);
        adsTaskLog.setPlatformName(platformName);
        adsTaskLog.setIp(ip);
        adsTaskLog.setCountryCode(countryCode);
        adsTaskLog.setDevice(Constant.DEVICE_TYPE_DESK);
        adsTaskLog.setUserAgent(userAgent);
        adsTaskLog.setSequence((long) sequence);
        adsTaskLog.setRequestUrl(requestUrl);
    }
}
