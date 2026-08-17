package com.admire.cars.runner.util;

import com.admire.cars.runner.constant.Constant;
import com.admire.cars.runner.constant.StatusConstant;
import com.admire.cars.runner.dto.IpVerificationDto;
import com.admire.cars.runner.entity.*;
import com.admire.cars.runner.repository.AdsTaskLogRepository;
import com.admire.cars.runner.repository.IpProxyInfoRepository;
import com.admire.cars.runner.service.proxy.IpProxyService;
import com.admire.cars.runner.service.proxy.UserAgentService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.compress.utils.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@Slf4j
@Service
public class AdsHttpClientTool {


    @Autowired
    private IpProxyService ipProxyService;

    @Autowired
    private UserAgentService userAgentService;

    @Autowired
    private AdsTaskLogRepository adsTaskLogRepository;

    @Autowired
    private IpProxyInfoRepository ipProxyInfoRepository;


    private static final int MAX_REDIRECT_HOPS = 10;


    /**
     * Normal Ads Task
     * @param adsNormalInfo
     * @return
     */
    public AdsHttpResponseDto applyAffiliateAd(AdsNormalInfo adsNormalInfo) {

        String userAgent = userAgentService.getUserAgent();
        String affiliateUrl = requireText(adsNormalInfo.getAffiliteUrl(), "affiliteUrl is required");
        final String landingPageUrl = requireText(adsNormalInfo.getLandingPageUrl(), "landingPageUrl is required");
        List<AdsTaskLog> adsTaskLogList = Lists.newArrayList();
        AdsTaskLog adsTaskLog = new AdsTaskLog();
        adsTaskLogList.add(adsTaskLog);
        final OkHttpClient okHttpClient = ipProxyService.buildOkHttpClient(adsNormalInfo.getDynamicProxyInfo());
        //Verify Http client IP region
        IpVerificationDto ipVerificationDto = ipProxyService.ipVerification4OkHttpClient(okHttpClient, adsNormalInfo.getCampainCountry());
        String enrichedAffiliateUrl = enrichAffiliateUrl(affiliateUrl);
        buildAdsTaskLog(adsTaskLog, adsNormalInfo,
                ipVerificationDto.getIp(), ipVerificationDto.getCountryCode(),
                0L, userAgent, null);
        if (ipVerificationDto.isMatched()) {
            AdsHttpRequestDto adsHttpRequestDto = new AdsHttpRequestDto(enrichedAffiliateUrl,landingPageUrl,Constant.DEVICE_TYPE_DESK,userAgent);
            URI requestUri = toRequestUri(enrichedAffiliateUrl, "affiliateUrl");
            try {
                RedirectResult result = null;
                // Redirect Start
                OkHttpClient redirectClient = okHttpClient.newBuilder()
                        .followRedirects(false)
                        .followSslRedirects(false)
                        .build();
                URI currentUri = requestUri;
                for (int hop = 0; hop < MAX_REDIRECT_HOPS; hop++) {
                    final long startTime = System.currentTimeMillis();
                    Request request = buildRequest(adsHttpRequestDto, currentUri.toString());
                    try (Response response = redirectClient.newCall(request).execute()) {
                        int statusCode = response.code();
                        URI responseUri = currentUri;
                        log.warn("Apply Affiliate Ads. Request URL={} Response={} hop={} status={}", currentUri, responseUri, hop + 1, statusCode);
                        adsTaskLog = new AdsTaskLog();
                        adsTaskLogList.add(adsTaskLog);
                        buildAdsTaskLog(adsTaskLog, adsNormalInfo,
                                (null != ipVerificationDto) ? ipVerificationDto.getIp() : null,
                                (null != ipVerificationDto) ? ipVerificationDto.getCountryCode() : null,
                                (long)(hop + 1), userAgent, currentUri.toString());

                        final long durationMillis = System.currentTimeMillis() - startTime;
                        adsTaskLog.setDurationMillis(String.valueOf(durationMillis));
                        adsTaskLog.setStatusCode(String.valueOf(statusCode));
                        adsTaskLog.setResponseUrl(responseUri.toString());
                        if (statusCode >= 200 && statusCode < 300) {
                            result = new RedirectResult(statusCode, responseUri);
                            adsTaskLog.setErrMsg("");
                            adsTaskLog.setSuccess(true);
                            break;
                        }

                        if (statusCode >= 300 && statusCode < 400) {
                            String location = response.header("Location");
                            adsTaskLog.setLocation(location);
                            if (!StringUtils.hasText(location)) {
                                result = new RedirectResult(statusCode, responseUri);
                                adsTaskLog.setErrMsg("No Location found from header");
                                break;
                            }
                            URI nextUri = currentUri.resolve(location.trim());
                            currentUri = nextUri;
                            continue;
                        }
                        result = new RedirectResult(statusCode, responseUri);
                        break;
                    }
                }
                adsTaskLogRepository.saveAll(adsTaskLogList);
                // Redirect End
                if (result.statusCode >= 200 && result.statusCode < 300) {
                    if (isLandingPage(result.finalUri, adsHttpRequestDto.getLandingPageUrl())) {
                        return new AdsHttpResponseDto(StatusConstant.SUCCESS, result.statusCode, result.finalUriText(), "");
                    }
                    return new AdsHttpResponseDto(StatusConstant.FAILED, result.statusCode, result.finalUriText(), "Response URL does not match landing page prefix");
                }
                if (result.statusCode >= 300 && result.statusCode < 400) {
                    return new AdsHttpResponseDto(StatusConstant.FAILED, result.statusCode, result.finalUriText(), "Response URL does not match landing page prefix");
                }
                return new AdsHttpResponseDto(StatusConstant.FAILED, result.statusCode, result.finalUriText(), "Response status code is not a redirect or success code");
            } catch (IOException proxyIoException) {
                log.error("Apply Affiliate Ads. Request URL={} error={}", requestUri, proxyIoException.getMessage(), proxyIoException);
                return new AdsHttpResponseDto(StatusConstant.FAILED, -1, requestUri.toString(), proxyIoException.getMessage());
            }
        } else {
            adsTaskLog.setSuccess(false);
            adsTaskLog.setErrMsg("IP verification failed: expected country " + adsNormalInfo.getCampainCountry() + ", but got " + ipVerificationDto.getCountryCode());
        }
        return new AdsHttpResponseDto(StatusConstant.FAILED, -1, null,
                "IP verification failed: expected country " + adsNormalInfo.getCampainCountry() + ", but got " + ipVerificationDto.getCountryCode());
    }


    public AdsHttpResponseDto applyAffiliateAd(AdsMatrixInfo matrixInfo, AdsMatrixAffiliateInfo adsMatrixAffiliateInfo) {

        String userAgent = userAgentService.getUserAgent();
        String affiliateUrl = requireText(adsMatrixAffiliateInfo.getAffiliteUrl(), "affiliateUrl is required");
        final String landingPageUrl = requireText(matrixInfo.getLandingPageUrl(), "landingPageUrl is required");
        List<AdsTaskLog> adsTaskLogList = Lists.newArrayList();
        AdsTaskLog adsTaskLog = new AdsTaskLog();
        adsTaskLogList.add(adsTaskLog);
        final OkHttpClient okHttpClient = ipProxyService.buildOkHttpClient(matrixInfo.getDynamicProxyInfo());
        //Verify Http client IP region
        IpVerificationDto ipVerificationDto = ipProxyService.ipVerification4OkHttpClient(okHttpClient, matrixInfo.getCampainCountry());
        buildAdsTaskLog(adsTaskLog, matrixInfo,
                ipVerificationDto.getIp(), ipVerificationDto.getCountryCode(),
                0L, userAgent, null);
        adsTaskLog.setPlatformName(adsMatrixAffiliateInfo.getPlatformName());
        if (ipVerificationDto.isMatched()) {
            AdsHttpRequestDto adsHttpRequestDto = new AdsHttpRequestDto(affiliateUrl,landingPageUrl,Constant.DEVICE_TYPE_DESK,userAgent);
            URI requestUri = toRequestUri(affiliateUrl, "affiliateUrl");
            try {
                RedirectResult result = null;
                // Redirect Start
                OkHttpClient redirectClient = okHttpClient.newBuilder()
                        .followRedirects(false)
                        .followSslRedirects(false)
                        .build();
                URI currentUri = requestUri;
                for (int hop = 0; hop < MAX_REDIRECT_HOPS; hop++) {
                    final long startTime = System.currentTimeMillis();
                    Request request = buildRequest(adsHttpRequestDto, currentUri.toString());
                    try (Response response = redirectClient.newCall(request).execute()) {
                        int statusCode = response.code();
                        URI responseUri = currentUri;
                        log.warn("Matrix Ads Apply Affiliate Ads. Request URL={} Response={} hop={} status={}", currentUri, responseUri, hop + 1, statusCode);
                        adsTaskLog = new AdsTaskLog();
                        adsTaskLogList.add(adsTaskLog);
                        buildAdsTaskLog(adsTaskLog, matrixInfo,
                                (null != ipVerificationDto) ? ipVerificationDto.getIp() : null,
                                (null != ipVerificationDto) ? ipVerificationDto.getCountryCode() : null,
                                (long)(hop + 1), userAgent, currentUri.toString());
                        adsTaskLog.setPlatformName(adsMatrixAffiliateInfo.getPlatformName());
                        final long durationMillis = System.currentTimeMillis() - startTime;
                        adsTaskLog.setDurationMillis(String.valueOf(durationMillis));
                        adsTaskLog.setStatusCode(String.valueOf(statusCode));
                        adsTaskLog.setResponseUrl(responseUri.toString());
                        if (statusCode >= 200 && statusCode < 300) {
                            result = new RedirectResult(statusCode, responseUri);
                            adsTaskLog.setErrMsg("");
                            adsTaskLog.setSuccess(true);
                            break;
                        }

                        if (statusCode >= 300 && statusCode < 400) {
                            String location = response.header("Location");
                            adsTaskLog.setLocation(location);
                            if (!StringUtils.hasText(location)) {
                                result = new RedirectResult(statusCode, responseUri);
                                adsTaskLog.setErrMsg("No Location found from header");
                                break;
                            }
                            URI nextUri = currentUri.resolve(location.trim());
                            currentUri = nextUri;
                            continue;
                        }
                        result = new RedirectResult(statusCode, responseUri);
                        break;
                    }
                }
                adsTaskLogRepository.saveAll(adsTaskLogList);
                // Redirect End
                if (result.statusCode >= 200 && result.statusCode < 300) {
                    if (isLandingPage(result.finalUri, adsHttpRequestDto.getLandingPageUrl())) {
                        return new AdsHttpResponseDto(StatusConstant.SUCCESS, result.statusCode, result.finalUriText(), "");
                    }
                    return new AdsHttpResponseDto(StatusConstant.FAILED, result.statusCode, result.finalUriText(), "Response URL does not match landing page prefix");
                }
                if (result.statusCode >= 300 && result.statusCode < 400) {
                    return new AdsHttpResponseDto(StatusConstant.FAILED, result.statusCode, result.finalUriText(), "Response URL does not match landing page prefix");
                }
                return new AdsHttpResponseDto(StatusConstant.FAILED, result.statusCode, result.finalUriText(), "Response status code is not a redirect or success code");
            } catch (IOException proxyIoException) {
                log.error("Apply Affiliate Ads. Request URL={} error={}", requestUri, proxyIoException.getMessage(), proxyIoException);
                return new AdsHttpResponseDto(StatusConstant.FAILED, -1, requestUri.toString(), proxyIoException.getMessage());
            }
        } else {
            adsTaskLog.setSuccess(false);
            adsTaskLog.setErrMsg("IP verification failed: expected country " + matrixInfo.getCampainCountry() + ", but got " + ipVerificationDto.getCountryCode());
        }
        return new AdsHttpResponseDto(StatusConstant.FAILED, -1, null,
                "IP verification failed: expected country " + matrixInfo.getCampainCountry() + ", but got " + ipVerificationDto.getCountryCode());
    }

    /**
     * Normal Ads Test
     * @param affiliateAds
     * @param targetRegion
     * @return
     */
    public AdsHttpResponseDto applyAffiliateAd(AffiliateAds affiliateAds, String targetRegion) {
        OkHttpClient httpClient = null;
        IpProxyInfo ipProxyInfo = null;
        IpVerificationDto ipVerification = null;
        final List<String> proxyFailures = new ArrayList<>();

        List<IpProxyInfo> proxies = ipProxyInfoRepository
                .findByAdsOwnerAndStatusIgnoreCaseAndTargetCountryIgnoreCaseAndProxyTypeAndProxyProtocolOrderByIdDesc(
                        affiliateAds.getAdsOwner(),
                        StatusConstant.ENABLED,
                        targetRegion,
                        Constant.PROXY_TYPE_DYNAMIC,
                        Constant.PROXY_PROTOCOL_SOCKETS5);
        if (proxies.isEmpty()) {
            log.warn("No ENABLED IP_PROXY_INFO found for adsOwner: {} with targetCountry: {}", affiliateAds.getAdsOwner(), targetRegion);
            throw new IllegalArgumentException("No ENABLED IP_PROXY_INFO found for adsOwner: " + affiliateAds.getAdsOwner());
        }
        for (IpProxyInfo proxy : proxies) {
            httpClient = ipProxyService.buildOkHttpClient(proxy.getProxyInfo());
            ipVerification = ipProxyService.ipVerification4OkHttpClient(httpClient, affiliateAds.getRegion());
            ipProxyInfo = proxy;
            if (ipVerification.isMatched()) {
                break;
            } else {
                proxyFailures.add("proxyId=" + ipProxyInfo.getId()
                        + " region mismatch expected=" + affiliateAds.getRegion()
                        + " actual=" + ipVerification.getCountryCode());
            }
        }
        String affiliateUrl = requireText(affiliateAds.getTrackingUrl(), "affiliateUrl is required");
        final String landingPageUrl = requireText(affiliateAds.getSiteUrl(), "landingPageUrl is required");

        if (null != ipVerification && ipVerification.isMatched()) {
            String userAgent = userAgentService.getUserAgent();
            log.info("AFFILIATE_TEST_TASK_PROXY_VERIFIEDproxyId={} proxyInfo={} region={}, ipVerification: {}",
                    ipProxyInfo.getId(), ipProxyInfo.getProxyInfo(), ipVerification);
            String enrichedAffiliateUrl = enrichAffiliateUrl(affiliateUrl);
            AdsHttpRequestDto adsHttpRequestDto = new AdsHttpRequestDto(enrichedAffiliateUrl,landingPageUrl,Constant.DEVICE_TYPE_DESK,userAgent);
            URI requestUri = toRequestUri(enrichedAffiliateUrl, "affiliateUrl");
            try {
                RedirectResult result = null;
                // Redirect Start
                OkHttpClient redirectClient = httpClient.newBuilder()
                        .followRedirects(false)
                        .followSslRedirects(false)
                        .build();
                URI currentUri = requestUri;
                for (int hop = 0; hop < MAX_REDIRECT_HOPS; hop++) {
                    Request request = buildRequest(adsHttpRequestDto, currentUri.toString());
                    try (Response response = redirectClient.newCall(request).execute()) {
                        int statusCode = response.code();
                        URI responseUri = currentUri;
                        log.warn("Apply Affiliate Ads. Request URL={} Response={} hop={} status={}", currentUri, responseUri, hop + 1, statusCode);
                        if (statusCode >= 200 && statusCode < 300) {
                            result = new RedirectResult(statusCode, responseUri);
                            break;
                        }

                        if (statusCode >= 300 && statusCode < 400) {
                            String location = response.header("Location");
                            if (!StringUtils.hasText(location)) {
                                result = new RedirectResult(statusCode, responseUri);
                                break;
                            }
                            URI nextUri = currentUri.resolve(location.trim());
                            currentUri = nextUri;
                            continue;
                        }
                        result = new RedirectResult(statusCode, responseUri);
                        break;
                    }
                }
                // Redirect End
                if (result.statusCode >= 200 && result.statusCode < 300) {
                    if (isLandingPage(result.finalUri, adsHttpRequestDto.getLandingPageUrl())) {
                        return new AdsHttpResponseDto(StatusConstant.SUCCESS, result.statusCode, result.finalUriText(), "");
                    }
                    return new AdsHttpResponseDto(StatusConstant.FAILED, result.statusCode, result.finalUriText(), "Response URL does not match landing page prefix");
                }
                if (result.statusCode >= 300 && result.statusCode < 400) {
                    return new AdsHttpResponseDto(StatusConstant.FAILED, result.statusCode, result.finalUriText(), "Response URL does not match landing page prefix");
                }
                return new AdsHttpResponseDto(StatusConstant.FAILED, result.statusCode, result.finalUriText(), "Response status code is not a redirect or success code");
            } catch (IOException proxyIoException) {
                log.error("Apply Affiliate Ads. Request URL={} error={}", requestUri, proxyIoException.getMessage(), proxyIoException);
                return new AdsHttpResponseDto(StatusConstant.FAILED, -1, requestUri.toString(), proxyIoException.getMessage());
            }
        }
        return new AdsHttpResponseDto(StatusConstant.FAILED, -1, null,
                "IP verification failed: expected country " + targetRegion + ", but got " + ipVerification.getCountryCode());

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
        adsTaskLog.setSuccess(false);
    }

    private void buildAdsTaskLog(AdsTaskLog adsTaskLog, AdsMatrixInfo matrixInfo,
                                 String ip, String countryCode,
                                 Long sequence,String userAgent, String requestUrl) {
        adsTaskLog.setAdsOwner(matrixInfo.getAdsOwner());
        adsTaskLog.setAdsName(matrixInfo.getCampainName());
        adsTaskLog.setAdsType(Constant.ADS_TYPE_MATRIX);
        adsTaskLog.setIp(ip);
        adsTaskLog.setCountryCode(countryCode);
        adsTaskLog.setDevice(Constant.DEVICE_TYPE_DESK);
        adsTaskLog.setUserAgent(userAgent);
        adsTaskLog.setSequence(sequence);
        adsTaskLog.setRequestUrl(requestUrl);
        adsTaskLog.setSuccess(false);
    }

    private String enrichAffiliateUrl(String affiliateUrL) {
        if (!StringUtils.hasText(affiliateUrL)) {
            throw new IllegalArgumentException("affiliateUrL is required");
        }
        return affiliateUrL.replace("{subid}", UUID.randomUUID().toString());
    }


    private boolean isLandingPage(final URI uri, final String landingPage) {
        return uri != null && StringUtils.hasText(landingPage) && uri.toString().startsWith(landingPage);
    }

    private Request buildRequest(AdsHttpRequestDto adsHttpRequestDto, String url) {
        return new Request.Builder()
                .url(url)
                .header("Accept", "text/html, application/json, text/plain, */*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .header("X-Device-Type", adsHttpRequestDto.getDeviceType())
                .header("User-Agent", adsHttpRequestDto.getUserAgent())
                .get()
                .build();
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


    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static final class RedirectResult {
        private final int statusCode;
        private final URI finalUri;

        private RedirectResult(int statusCode, URI finalUri) {
            this.statusCode = statusCode;
            this.finalUri = finalUri;
        }

        private String finalUriText() {
            return finalUri == null ? "" : finalUri.toString();
        }
    }
}
