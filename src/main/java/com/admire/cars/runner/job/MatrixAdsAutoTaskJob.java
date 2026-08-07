package com.admire.cars.runner.job;

import com.admire.cars.runner.config.AutoTaskConfig;
import com.admire.cars.runner.constant.Constant;
import com.admire.cars.runner.dto.IpVerificationDto;
import com.admire.cars.runner.entity.*;
import com.admire.cars.runner.repository.AdsMatrixInfoRepository;
import com.admire.cars.runner.repository.NormalTaskRedirectLogRepository;
import com.admire.cars.runner.repository.ShiftLinkRepository;
import com.admire.cars.runner.service.proxy.IpProxyService;
import com.admire.cars.runner.service.proxy.UserAgentService;
import org.apache.commons.compress.utils.Lists;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

public class MatrixAdsAutoTaskJob extends AdsAutoTaskJob {


    private static final Logger log = LoggerFactory.getLogger(MatrixAdsAutoTaskJob.class);


    private static final List<Integer> REDIRECT_STATUS_CODES = List.of(301, 302, 303, 307, 308);

    @Autowired
    private AdsMatrixInfoRepository adsMatrixInfoRepository;


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

        AdsMatrixInfo adsMatrixInfo = adsMatrixInfoRepository.findById(adsId)
                .orElseThrow(() -> new IllegalArgumentException("ADS_MATRIX_INFO not found: " + adsId));
        final String landingPageUrl = requireText(adsMatrixInfo.getLandingPageUrl(), "landingPageUrl is required");
        String userAgent = userAgentService.getUserAgent();
        final HttpClient httpClient = ipProxyService.buildHttpClient(adsMatrixInfo.getDynamicProxyInfo());
        IpVerificationDto ipVerificationDto = null;
        NormalTaskRedirectLog normalTaskRedirectLog = new NormalTaskRedirectLog();
        //Verify Http client IP region
        try {
            ipVerificationDto = ipProxyService.ipVerification4HttpClient(httpClient, adsMatrixInfo.getCampainCountry());
            buildNormalTaskRedirectLog(normalTaskRedirectLog, adsMatrixInfo,
                    ipVerificationDto.getIp(), ipVerificationDto.getCountryCode(),
                    0L, userAgent, null);
            normalTaskRedirectLog.setSuccess(true);
            if (!ipVerificationDto.isMatched()) {
                normalTaskRedirectLog.setErrMsg("IP verification failed");
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            normalTaskRedirectLog.setErrMsg(e.getMessage());
        }
        if (StringUtils.hasText(normalTaskRedirectLog.getErrMsg())) {
            log.warn("MATRIX_AUTO_TASK_IP_LOOKUP_PROXY_AUTH_REQUIRED adsId={} Job Id:{}  message={}",
                    adsMatrixInfo.getId(), jobId, normalTaskRedirectLog.getErrMsg());
            return;
        }
        List<AdsMatrixAffiliateInfo> adsMatrixAffiliateInfoList = adsMatrixInfo.getAffiliateInfos();
        if (CollectionUtils.isEmpty(adsMatrixAffiliateInfoList)) {
            log.warn("MATRIX_AUTO_TASK_NO_AFFILIATE_INFO adsId={} Job Id:{}  message={}",
                    adsMatrixInfo.getId(), jobId, "No affiliate info found for this matrix ad");
            return;

        }
        normalTaskRedirectLogList.add(normalTaskRedirectLog);
        for (AdsMatrixAffiliateInfo adsMatrixAffiliateInfo : adsMatrixAffiliateInfoList) {
            List<NormalTaskRedirectLog> affiliateRedirectLogList = Lists.newArrayList();
            boolean affiliateSucceeded = false;
            try {
                int lastStatusCode = -1;
                String affiliateUrl = requireText(adsMatrixAffiliateInfo.getAffiliteUrl(), "affiliteUrl is required");
                // Proceed with redirect following regardless of IP verification status
                URI currentUrl = toRequestUri(affiliateUrl, "affiliteUrl");
                for (int sequence = 1; sequence <= autoTaskConfig.getMaxRedirects(); sequence++) {
                    NormalTaskRedirectLog redirectLog = new NormalTaskRedirectLog();
                    buildNormalTaskRedirectLog(redirectLog, adsMatrixInfo,
                            (null != ipVerificationDto) ? ipVerificationDto.getIp() : null,
                            (null != ipVerificationDto) ? ipVerificationDto.getCountryCode() : null,
                            (long) sequence, userAgent, currentUrl.toString());
                    final long startTime = System.currentTimeMillis();
                    final HttpRequest httpRequest = ipProxyService.buildBaseRequest(currentUrl, userAgent, Constant.DEVICE_TYPE_DESK)
                            .GET()
                            .build();
                    URI responseUrl = null;
                    try {
                        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                        lastStatusCode = response.statusCode();
                        if (null != response.uri()) {
                            responseUrl = response.uri();
                            redirectLog.setLocation(responseUrl.toString());
                            redirectLog.setResponseUrl(responseUrl.toString());
                        }
                        final long durationMillis = System.currentTimeMillis() - startTime;
                        redirectLog.setDurationMillis(String.valueOf(durationMillis));
                        redirectLog.setStatusCode(String.valueOf(lastStatusCode));
                        URI effectiveUrl = responseUrl != null ? responseUrl : currentUrl;
                        if (!REDIRECT_STATUS_CODES.contains(lastStatusCode)) {
                            if (isLandingPage(effectiveUrl, landingPageUrl)) {
                                redirectLog.setSuccess(true);
                                affiliateSucceeded = true;
                                affiliateRedirectLogList.add(redirectLog);
                                break;
                            }
                            redirectLog.setSuccess(false);
                            redirectLog.setErrMsg("Non-redirect status code received: " + lastStatusCode);
                            affiliateRedirectLogList.add(redirectLog);
                            if (responseUrl == null || effectiveUrl.equals(currentUrl)) {
                                break;
                            }
                            currentUrl = effectiveUrl;
                            continue;
                        }
                        redirectLog.setSuccess(false);
                        redirectLog.setErrMsg("Redirect status code received: " + lastStatusCode);
                        affiliateRedirectLogList.add(redirectLog);
                        if (responseUrl == null || responseUrl.equals(currentUrl)) {
                            break;
                        }
                        currentUrl = responseUrl;
                    } catch (IOException | InterruptedException proxyIoException) {
                        if (proxyIoException instanceof InterruptedException) {
                            Thread.currentThread().interrupt();
                        }
                        log.warn("MATRIX_AUTO_TASK_PROXY_REQUEST_FAILED adsId={} jobId={} requestUrl={} message={}",
                                adsMatrixInfo.getId(),
                                jobId,
                                currentUrl,
                                proxyIoException.getMessage());
                        redirectLog.setSuccess(false);
                        redirectLog.setErrMsg(proxyIoException.getMessage());
                        affiliateRedirectLogList.add(redirectLog);
                        break;
                    }
                }
            } catch (IllegalArgumentException invalidAffiliateException) {
                NormalTaskRedirectLog invalidAffiliateLog = new NormalTaskRedirectLog();
                buildNormalTaskRedirectLog(invalidAffiliateLog, adsMatrixInfo,
                        (null != ipVerificationDto) ? ipVerificationDto.getIp() : null,
                        (null != ipVerificationDto) ? ipVerificationDto.getCountryCode() : null,
                        0L, userAgent, adsMatrixAffiliateInfo.getAffiliteUrl());
                invalidAffiliateLog.setSuccess(false);
                invalidAffiliateLog.setErrMsg(invalidAffiliateException.getMessage());
                affiliateRedirectLogList.add(invalidAffiliateLog);
            }

            if (!affiliateSucceeded) {
                log.warn("MATRIX_AUTO_TASK_AFFILIATE_FAILED adsId={} jobId={} platformName={} message={}",
                        adsMatrixInfo.getId(),
                        jobId,
                        adsMatrixAffiliateInfo.getPlatformName(),
                        "Max redirects reached or request failed; continuing next affiliate");
            }
            normalTaskRedirectLogList.addAll(affiliateRedirectLogList);
            NormalTaskRedirectLog successTask = affiliateRedirectLogList.stream().filter(log ->
                    null != log.getResponseUrl() && log.getSuccess()).findFirst().orElse(null);
            if (null != successTask) {
                ShiftLink shiftLink = new ShiftLink();
                shiftLink.setAdsId(adsMatrixInfo.getId());
                shiftLink.setAdsName(adsMatrixInfo.getCampainName());
                shiftLink.setAdsType(Constant.ADS_TYPE_MATRIX);
                shiftLink.setPlatformName(adsMatrixAffiliateInfo.getPlatformName());
                shiftLink.setLandingPageUrl(adsMatrixInfo.getLandingPageUrl());
                shiftLink.setFullUrl(successTask.getResponseUrl());
                shiftLink.setDisplayNumber(0L);
                shiftLink.setStatus(adsMatrixInfo.getStatus());
                shiftLink.setAdsOwner(adsMatrixInfo.getAdsOwner());
                shiftLinkRepository.save(shiftLink);
            }

        }
        normalTaskRedirectLogRepository.saveAll(normalTaskRedirectLogList);
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
            throw new IllegalArgumentException("adsId is required for matrix ads job execution");
        }
        return jobDataAdsId;
    }


    private boolean isLandingPage(final URI uri, final String landingPage) {
        return uri.toString().startsWith(landingPage);
    }



    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private URI toRequestUri(String value, String fieldName) {
        String normalized = requireText(value, fieldName + " is required");
        try {
            return URI.create(normalized);
        } catch (IllegalArgumentException ex) {
            String sanitized = normalized.replace(" ", "%20").replace("|", "%7C");
            try {
                return URI.create(sanitized);
            } catch (IllegalArgumentException nested) {
                throw new IllegalArgumentException(fieldName + " is invalid URL: " + normalized, nested);
            }
        }
    }

    private void buildNormalTaskRedirectLog(NormalTaskRedirectLog normalTaskRedirectLog, AdsMatrixInfo adsMatrixInfo,
                                            String ip, String countryCode, Long sequence,String userAgent, String requestUrl) {
        normalTaskRedirectLog.setAdsOwner(adsMatrixInfo.getAdsOwner());
        normalTaskRedirectLog.setIp(ip);
        normalTaskRedirectLog.setCountryCode(countryCode);
        normalTaskRedirectLog.setNormalInfoId(adsMatrixInfo.getId());
        normalTaskRedirectLog.setDevice(Constant.DEVICE_TYPE_DESK);
        normalTaskRedirectLog.setUserAgent(userAgent);
        normalTaskRedirectLog.setSequence((long) sequence);
        normalTaskRedirectLog.setRequestUrl(requestUrl);
    }
}
