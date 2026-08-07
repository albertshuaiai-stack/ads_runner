package com.admire.cars.runner.job;

import com.admire.cars.runner.config.AutoTaskConfig;
import com.admire.cars.runner.constant.Constant;
import com.admire.cars.runner.dto.IpVerificationDto;
import com.admire.cars.runner.entity.AdsNormalInfo;
import com.admire.cars.runner.entity.NormalTaskRedirectLog;
import com.admire.cars.runner.entity.ShiftLink;
import com.admire.cars.runner.repository.AdsNormalInfoRepository;
import com.admire.cars.runner.repository.NormalTaskRedirectLogRepository;
import com.admire.cars.runner.repository.ShiftLinkRepository;
import com.admire.cars.runner.service.proxy.IpProxyService;
import com.admire.cars.runner.service.proxy.UserAgentService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.compress.utils.Lists;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.*;
import java.util.*;

public class NormalAdsAutoTaskJob extends AdsAutoTaskJob {

    private static final Logger log = LoggerFactory.getLogger(NormalAdsAutoTaskJob.class);

    private static final List<Integer> REDIRECT_STATUS_CODES = List.of(301, 302, 303, 307, 308);

    @Autowired
    private AdsNormalInfoRepository adsNormalInfoRepository;

    @Autowired
    private ShiftLinkRepository shiftLinkRepository;

    @Autowired
    private NormalTaskRedirectLogRepository normalTaskRedirectLogRepository;

    @Autowired
    private IpProxyService ipProxyService;

    @Autowired
    private UserAgentService userAgentService;

    @Autowired
    private AutoTaskConfig autoTaskConfig;

    @Override
    protected void executeTask(JobExecutionContext context) {

        List<NormalTaskRedirectLog> normalTaskRedirectLogList = Lists.newArrayList();
        JobDataMap jobDataMap = context.getMergedJobDataMap();
        String jobId = resolveJobId(context, jobDataMap);
        Long adsId = resolveAdsId(jobId, jobDataMap);
        int lastStatusCode = -1;

        AdsNormalInfo adsNormalInfo = adsNormalInfoRepository.findById(adsId)
                .orElseThrow(() -> new IllegalArgumentException("ADS_NORMAL_INFO not found: " + adsId));

        String userAgent = userAgentService.getUserAgent();
        String affiliateUrl = requireText(adsNormalInfo.getAffiliteUrl(), "affiliteUrl is required");
        final String landingPageUrl = requireText(adsNormalInfo.getLandingPageUrl(), "landingPageUrl is required");
        final OkHttpClient okHttpClient = ipProxyService.buildOkHttpClient(adsNormalInfo.getDynamicProxyInfo());
        IpVerificationDto ipVerificationDto = null;
        NormalTaskRedirectLog normalTaskRedirectLog = new NormalTaskRedirectLog();
        //Verify Http client IP region
        try {
            ipVerificationDto = ipProxyService.ipVerification4OkHttpClient(okHttpClient, adsNormalInfo.getCampainCountry());
            buildNormalTaskRedirectLog(normalTaskRedirectLog, adsNormalInfo,
                    ipVerificationDto.getIp(), ipVerificationDto.getCountryCode(),
                    0L, userAgent, null);
            normalTaskRedirectLog.setSuccess(true);
            if (!ipVerificationDto.isMatched()) {
                normalTaskRedirectLog.setErrMsg("IP verification failed");
            }
        } catch (IOException e) {
            normalTaskRedirectLog.setErrMsg(e.getMessage());
        }
        if (StringUtils.hasText(normalTaskRedirectLog.getErrMsg())) {
            log.warn("NORMAL_AUTO_TASK_IP_LOOKUP_PROXY_AUTH_REQUIRED adsId={} Job Id:{}  message={}",
                    adsNormalInfo.getId(), jobId, normalTaskRedirectLog.getErrMsg());
            return;
        }
        normalTaskRedirectLogList.add(normalTaskRedirectLog);
        
        // Proceed with redirect following regardless of IP verification status
        URI currentUrl = URI.create(affiliateUrl);
        for (int sequence = 1; sequence <= autoTaskConfig.getMaxRedirects(); sequence++) {
            normalTaskRedirectLog = new NormalTaskRedirectLog();
            buildNormalTaskRedirectLog(normalTaskRedirectLog, adsNormalInfo, 
                    (null != ipVerificationDto) ? ipVerificationDto.getIp() : null,
                    (null != ipVerificationDto) ? ipVerificationDto.getCountryCode() : null, 
                    (long) sequence, userAgent, currentUrl.toString());
            final long startTime = System.currentTimeMillis();
            final Request httpRequest = ipProxyService.buildOkHttpClientBaseRequest(currentUrl.toString(), userAgent, Constant.DEVICE_TYPE_DESK);
            URI responseUrl = null;
            try (Response response = okHttpClient.newCall(httpRequest).execute()){
                lastStatusCode = response.code();
                if (null != response.networkResponse()
                        && null != response.networkResponse().request()
                        && null != response.networkResponse().request().url()) {
                    responseUrl = URI.create(response.networkResponse().request().url().toString());
                    normalTaskRedirectLog.setLocation(responseUrl.toString());
                    normalTaskRedirectLog.setResponseUrl(responseUrl.toString());

                }
                final long durationMillis = System.currentTimeMillis() - startTime;
                normalTaskRedirectLog.setDurationMillis(String.valueOf(durationMillis));
                normalTaskRedirectLog.setStatusCode(String.valueOf(lastStatusCode));
                if (!REDIRECT_STATUS_CODES.contains(lastStatusCode)) {
                    if (isLandingPage(currentUrl, landingPageUrl)
                            && lastStatusCode >= 200
                            && lastStatusCode < 300) {
                        normalTaskRedirectLog.setSuccess(true);
                        normalTaskRedirectLogList.add(normalTaskRedirectLog);
                        break;
                    }
                    normalTaskRedirectLog.setSuccess(false);
                    normalTaskRedirectLog.setErrMsg("Non-redirect status code received: " + lastStatusCode);
                    normalTaskRedirectLogList.add(normalTaskRedirectLog);
                    currentUrl = responseUrl;
                    continue;
                }
                normalTaskRedirectLog.setSuccess(false);
                normalTaskRedirectLog.setErrMsg("Redirect status code received: " + lastStatusCode);
                normalTaskRedirectLogList.add(normalTaskRedirectLog);
                currentUrl = responseUrl;
            } catch (IOException proxyIoException) {
                log.warn("NORMAL_AUTO_TASK_PROXY_REQUEST_FAILED adsId={} jobId={} requestUrl={} message={}",
                        adsNormalInfo.getId(),
                        jobId,
                        currentUrl,
                        proxyIoException.getMessage());
                normalTaskRedirectLog.setErrMsg(proxyIoException.getMessage());
            }
        }
        normalTaskRedirectLogRepository.saveAll(normalTaskRedirectLogList);
        NormalTaskRedirectLog successTask = normalTaskRedirectLogList.stream().filter(log ->
                null != log.getResponseUrl() && log.getSuccess()).findFirst().orElse(null);
        if (null != successTask) {
            ShiftLink shiftLink = new ShiftLink();
            shiftLink.setAdsId(adsNormalInfo.getId());
            shiftLink.setAdsName(adsNormalInfo.getCampainName());
            shiftLink.setAdsType(Constant.ADS_TYPE_NORMAL);
            shiftLink.setPlatformName(adsNormalInfo.getPlatformName());
            shiftLink.setLandingPageUrl(adsNormalInfo.getLandingPageUrl());
            shiftLink.setFullUrl(successTask.getResponseUrl());
            shiftLink.setDisplayNumber(0L);
            shiftLink.setStatus(adsNormalInfo.getStatus());
            shiftLink.setAdsOwner(adsNormalInfo.getAdsOwner());
            shiftLinkRepository.save(shiftLink);
        }
    }


    private void buildNormalTaskRedirectLog(NormalTaskRedirectLog normalTaskRedirectLog, AdsNormalInfo adsNormalInfo,
                                            String ip, String countryCode, Long sequence,String userAgent, String requestUrl) {
        normalTaskRedirectLog.setAdsOwner(adsNormalInfo.getAdsOwner());
        normalTaskRedirectLog.setIp(ip);
        normalTaskRedirectLog.setCountryCode(countryCode);
        normalTaskRedirectLog.setNormalInfoId(adsNormalInfo.getId());
        normalTaskRedirectLog.setDevice(Constant.DEVICE_TYPE_DESK);
        normalTaskRedirectLog.setUserAgent(userAgent);
        normalTaskRedirectLog.setSequence((long) sequence);
        normalTaskRedirectLog.setRequestUrl(requestUrl);
    }



    private boolean isLandingPage(final URI uri, final String landingPage) {
        return uri.toString().startsWith(landingPage);
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
