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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


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
    private static final Pattern JS_REDIRECT_PATTERN = Pattern.compile(
            "(?is)(?:window|document|top|self)\\s*\\.\\s*location(?:\\.href)?\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern LOCATION_REPLACE_PATTERN = Pattern.compile(
            "(?is)location\\.replace\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");
    private static final Pattern META_REFRESH_PATTERN = Pattern.compile(
            "(?is)<meta[^>]+http-equiv\\s*=\\s*['\"]?refresh['\"]?[^>]+content\\s*=\\s*['\"][^'\"]*url\\s*=\\s*([^'\"\\s>]+)");
    private static final Pattern REFRESH_HEADER_PATTERN = Pattern.compile("(?i)\\burl\\s*=\\s*(.+)$");
    private static final Pattern SEARCH_PARAM_REDIRECT_PATTERN = Pattern.compile(
            "(?is)searchParams\\.get\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");
    private static final Pattern PRIORITY_SEARCH_PARAM_REDIRECT_PATTERN = Pattern.compile(
            "(?is)searchParams\\.get\\(\\s*['\"]((?:store_)?url|redirect(?:_url)?|target|dest(?:ination)?|link|tracking(?:_link)?)['\"]\\s*\\)");
    private static final List<String> REFER_LINKS = Arrays.asList(
            "https://www.instagram.com/",
            "https://www.facebook.com/",
            "https://www.youtube.com/",
            "https://admirecars.com/",
            "https://x.com/",
            "https://www.reddit.com/");


    /**
     * Normal Ads Task
     * @param adsNormalInfo
     * @return
     */
    public AdsHttpResponseDto applyAffiliateAd(AdsNormalInfo adsNormalInfo) {

        AdsHttpResponseDto adsHttpResponseDto = new AdsHttpResponseDto();
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
                        URI responseUri = response.request() != null && response.request().url() != null
                                ? response.request().url().uri()
                                : currentUri;
                        log.warn("Apply Normal Affiliate Ads. Request URL={} Response={} hop={} status={}", currentUri, responseUri, hop + 1, statusCode);
                        adsTaskLog = new AdsTaskLog();
                        adsTaskLogList.add(adsTaskLog);
                        buildAdsTaskLog(adsTaskLog, adsNormalInfo,
                                (null != ipVerificationDto) ? ipVerificationDto.getIp() : null,
                                (null != ipVerificationDto) ? ipVerificationDto.getCountryCode() : null,
                                (long)(hop + 1), userAgent, currentUri.toString());

                        final long durationMillis = (System.currentTimeMillis() - startTime)/1000;
                        adsTaskLog.setDurationMillis(String.valueOf(durationMillis));
                        adsTaskLog.setStatusCode(String.valueOf(statusCode));
                        adsTaskLog.setResponseUrl(responseUri.toString());
                        if (statusCode >= 200 && statusCode < 300) {
                            if (isLandingPage(responseUri, adsHttpRequestDto.getLandingPageUrl())){
                                adsTaskLog.setErrMsg("");
                                adsTaskLog.setSuccess(true);
                                adsHttpResponseDto = new AdsHttpResponseDto(StatusConstant.SUCCESS, statusCode, responseUri.toString(), "");
                                break;
                            }
                            String redirectTarget = null;
                            if (response.body() != null) {
                                String responseBody = response.body().string();
                                redirectTarget = extractClientRedirectTarget(responseBody, currentUri);
                            }
                            if (!StringUtils.hasText(redirectTarget)) {
                                redirectTarget = extractRefreshHeaderTarget(response.header("Refresh"));
                            }
                            if (!StringUtils.hasText(redirectTarget)) {
                                redirectTarget = response.header("Location");
                            }
                            if (StringUtils.hasText(redirectTarget)) {
                                URI nextUri = resolveRedirectUri(currentUri, redirectTarget);
                                adsTaskLog.setLocation(nextUri.toString());
                                currentUri = nextUri;
                                continue;
                            }
                            adsHttpResponseDto = new AdsHttpResponseDto(StatusConstant.FAILED, statusCode, responseUri.toString(), "Response body does not contain a redirect target");
                            break;
                        } else if (statusCode >= 300 && statusCode < 400) {
                            String location = response.header("Location");
                            adsTaskLog.setLocation(location);
                            if (!StringUtils.hasText(location)) {
                                adsTaskLog.setErrMsg("No Location found from header");
                                adsHttpResponseDto = new AdsHttpResponseDto(StatusConstant.FAILED, statusCode, responseUri.toString(), "No Location found from header.");
                                break;
                            }
                            URI nextUri = resolveRedirectUri(currentUri, location);
                            currentUri = nextUri;
                            continue;
                        } else {
                            adsHttpResponseDto = new AdsHttpResponseDto(StatusConstant.FAILED, statusCode, responseUri.toString(), "Response status code is not a redirect or success code");
                            break;
                        }

                    }
                }
                if (!StatusConstant.SUCCESS.equals(adsHttpResponseDto.getStatus())) {
                    adsHttpResponseDto = new AdsHttpResponseDto(StatusConstant.FAILED, -1, currentUri.toString(), "Response URL does not match landing page prefix with max redirect times.");
                }
                // Redirect End
            } catch (IOException proxyIoException) {
                log.error("Apply Normal Affiliate Ads. Request URL={} error={}", requestUri, proxyIoException.getMessage(), proxyIoException);
                adsTaskLog = new AdsTaskLog();
                adsTaskLogList.add(adsTaskLog);
                buildAdsTaskLog(adsTaskLog, adsNormalInfo,
                        (null != ipVerificationDto) ? ipVerificationDto.getIp() : null,
                        (null != ipVerificationDto) ? ipVerificationDto.getCountryCode() : null,
                        (long)-1 , userAgent, "");
                adsTaskLog.setErrMsg(proxyIoException.getMessage());
                adsHttpResponseDto = new AdsHttpResponseDto(StatusConstant.FAILED, -1, requestUri.toString(), proxyIoException.getMessage());
            }
        } else {
            adsTaskLog.setErrMsg("IP verification failed: expected country " + adsNormalInfo.getCampainCountry() + ", but got " + ipVerificationDto.getCountryCode());
        }
        adsTaskLogRepository.saveAll(adsTaskLogList);
        return adsHttpResponseDto;
    }


    /**
     * Matrix Ads Task
     * @param matrixInfo
     * @param adsMatrixAffiliateInfo
     * @return
     */
    public AdsHttpResponseDto applyAffiliateAd(AdsMatrixInfo matrixInfo, AdsMatrixAffiliateInfo adsMatrixAffiliateInfo) {
        AdsHttpResponseDto adsHttpResponseDto = new AdsHttpResponseDto();
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
                        URI responseUri = response.request() != null && response.request().url() != null
                                ? response.request().url().uri()
                                : currentUri;
                        log.warn("Apply Matrix Affiliate Ads. Request URL={} Response={} hop={} status={}", currentUri, responseUri, hop + 1, statusCode);
                        adsTaskLog = new AdsTaskLog();
                        adsTaskLogList.add(adsTaskLog);
                        buildAdsTaskLog(adsTaskLog, matrixInfo,
                                (null != ipVerificationDto) ? ipVerificationDto.getIp() : null,
                                (null != ipVerificationDto) ? ipVerificationDto.getCountryCode() : null,
                                (long)(hop + 1), userAgent, currentUri.toString());
                        adsTaskLog.setPlatformName(adsMatrixAffiliateInfo.getPlatformName());
                        final long durationMillis = (System.currentTimeMillis() - startTime)/1000;
                        adsTaskLog.setDurationMillis(String.valueOf(durationMillis));
                        adsTaskLog.setStatusCode(String.valueOf(statusCode));
                        adsTaskLog.setResponseUrl(responseUri.toString());
                        if (statusCode >= 200 && statusCode < 300) {
                            if (isLandingPage(responseUri, adsHttpRequestDto.getLandingPageUrl())){
                                adsTaskLog.setErrMsg("");
                                adsTaskLog.setSuccess(true);
                                adsHttpResponseDto = new AdsHttpResponseDto(StatusConstant.SUCCESS, statusCode, responseUri.toString(), "");
                                break;
                            }
                            String responseBody = response.body() != null ? response.body().string() : "";
                            String redirectTarget = extractClientRedirectTarget(responseBody, currentUri);
                            if (!StringUtils.hasText(redirectTarget)) {
                                redirectTarget = extractRefreshHeaderTarget(response.header("Refresh"));
                            }
                            if (StringUtils.hasText(redirectTarget)) {
                                URI nextUri = resolveRedirectUri(currentUri, redirectTarget);
                                adsTaskLog.setLocation(nextUri.toString());
                                currentUri = nextUri;
                                continue;
                            }
                            adsHttpResponseDto = new AdsHttpResponseDto(StatusConstant.FAILED, statusCode, responseUri.toString(), "Response body does not contain a redirect target");
                            break;
                        } else if (statusCode >= 300 && statusCode < 400) {
                            String location = response.header("Location");
                            adsTaskLog.setLocation(location);
                            if (!StringUtils.hasText(location)) {
                                adsTaskLog.setErrMsg("No Location found from header");
                                adsHttpResponseDto = new AdsHttpResponseDto(StatusConstant.FAILED, statusCode, responseUri.toString(), "No Location found from header.");
                                break;
                            }
                            URI nextUri = resolveRedirectUri(currentUri, location);
                            currentUri = nextUri;
                            continue;
                        } else {
                            adsHttpResponseDto = new AdsHttpResponseDto(StatusConstant.FAILED, statusCode, responseUri.toString(), "Response status code is not a redirect or success code");
                            break;
                        }
                    }
                }
                if (!StatusConstant.SUCCESS.equals(adsHttpResponseDto.getStatus())) {
                    adsHttpResponseDto = new AdsHttpResponseDto(StatusConstant.FAILED, -1, currentUri.toString(), "Response URL does not match landing page prefix with max redirect times.");
                }
            } catch (IOException proxyIoException) {
                log.error("Apply Matrix Affiliate Ads. Request URL={} error={}", requestUri, proxyIoException.getMessage(), proxyIoException);
                adsHttpResponseDto = new AdsHttpResponseDto(StatusConstant.FAILED, -1, requestUri.toString(), proxyIoException.getMessage());
                adsTaskLog = new AdsTaskLog();
                adsTaskLogList.add(adsTaskLog);
                buildAdsTaskLog(adsTaskLog, matrixInfo,
                        (null != ipVerificationDto) ? ipVerificationDto.getIp() : null,
                        (null != ipVerificationDto) ? ipVerificationDto.getCountryCode() : null,
                        (long)-1 , userAgent, "");
                adsTaskLog.setErrMsg(proxyIoException.getMessage());
            }
        } else {
            adsTaskLog.setErrMsg("IP verification failed: expected country " + matrixInfo.getCampainCountry() + ", but got " + ipVerificationDto.getCountryCode());
        }
        adsTaskLogRepository.saveAll(adsTaskLogList);
        return adsHttpResponseDto;
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
                        URI responseUri = response.request() != null && response.request().url() != null
                                ? response.request().url().uri()
                                : currentUri;
                        log.warn("Apply Normal Affiliate Ads. Request URL={} Response={} hop={} status={}", currentUri, responseUri, hop + 1, statusCode);
                        if (statusCode >= 200 && statusCode < 300) {
                            if (isLandingPage(responseUri, adsHttpRequestDto.getLandingPageUrl())){
                                return new AdsHttpResponseDto(StatusConstant.SUCCESS, statusCode, responseUri.toString(), "");
                            }
                            String redirectTarget = null;
                            if (response.body() != null) {
                                String responseBody = response.body().string();
                                redirectTarget = extractClientRedirectTarget(responseBody);
                            }
                            if (!StringUtils.hasText(redirectTarget)) {
                                redirectTarget = extractRefreshHeaderTarget(response.header("Refresh"));
                            }
                            if (!StringUtils.hasText(redirectTarget)) {
                                redirectTarget = response.header("Location");
                            }
                            if (StringUtils.hasText(redirectTarget)) {
                                currentUri = resolveRedirectUri(currentUri, redirectTarget);
                                continue;
                            }
                            return new AdsHttpResponseDto(StatusConstant.FAILED, statusCode, responseUri.toString(), "Response body does not contain a redirect target");
                        } else if (statusCode >= 300 && statusCode < 400) {
                            String location = response.header("Location");
                            if (!StringUtils.hasText(location)) {
                                return new AdsHttpResponseDto(StatusConstant.FAILED, statusCode, responseUri.toString(), "No Location found from header.");
                            }
                            URI nextUri = currentUri.resolve(location.trim());
                            currentUri = nextUri;
                            continue;
                        } else {
                            return new AdsHttpResponseDto(StatusConstant.FAILED, statusCode, responseUri.toString(), "Response status code is not a redirect or success code");
                        }
                    }
                }
                // Redirect End
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

    private String extractClientRedirectTarget(String responseBody) {
        return extractClientRedirectTarget(responseBody, null);
    }

    private String extractClientRedirectTarget(String responseBody, URI currentUri) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        String target = findPatternMatch(JS_REDIRECT_PATTERN, responseBody);
        if (StringUtils.hasText(target)) {
            return target;
        }
        target = findPatternMatch(LOCATION_REPLACE_PATTERN, responseBody);
        if (StringUtils.hasText(target)) {
            return target;
        }
        target = findPatternMatch(META_REFRESH_PATTERN, responseBody);
        if (StringUtils.hasText(target)) {
            return target;
        }
        target = extractSearchParamRedirectTarget(responseBody, currentUri);
        if (StringUtils.hasText(target)) {
            return target;
        }
        return null;
    }

    private String extractSearchParamRedirectTarget(String responseBody, URI currentUri) {
        if (currentUri == null || !StringUtils.hasText(currentUri.getRawQuery())) {
            return null;
        }
        Matcher matcher = PRIORITY_SEARCH_PARAM_REDIRECT_PATTERN.matcher(responseBody);
        while (matcher.find()) {
            String paramName = matcher.group(1).trim();
            String paramValue = getQueryParameter(currentUri, paramName);
            if (looksLikeRedirectTarget(paramValue)) {
                return paramValue;
            }
        }
        matcher = SEARCH_PARAM_REDIRECT_PATTERN.matcher(responseBody);
        while (matcher.find()) {
            String paramName = matcher.group(1).trim();
            String paramValue = getQueryParameter(currentUri, paramName);
            if (looksLikeRedirectTarget(paramValue)) {
                return paramValue;
            }
        }
        return null;
    }

    private String getQueryParameter(URI uri, String paramName) {
        String rawQuery = uri == null ? null : uri.getRawQuery();
        if (!StringUtils.hasText(rawQuery) || !StringUtils.hasText(paramName)) {
            return null;
        }
        for (String pair : rawQuery.split("&")) {
            int idx = pair.indexOf('=');
            String key = idx >= 0 ? pair.substring(0, idx) : pair;
            if (paramName.equals(urlDecode(key))) {
                String value = idx >= 0 ? pair.substring(idx + 1) : "";
                return urlDecode(value);
            }
        }
        return null;
    }

    private String urlDecode(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private boolean looksLikeRedirectTarget(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return value.startsWith("http://")
                || value.startsWith("https://")
                || value.startsWith("//")
                || value.startsWith("/");
    }

    private String findPatternMatch(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private String extractRefreshHeaderTarget(String refreshHeader) {
        if (!StringUtils.hasText(refreshHeader)) {
            return null;
        }
        Matcher matcher = REFRESH_HEADER_PATTERN.matcher(refreshHeader.trim());
        if (!matcher.find()) {
            return null;
        }
        String target = matcher.group(1).trim();
        if (target.startsWith("\"") || target.startsWith("'")) {
            target = target.substring(1).trim();
        }
        if (target.endsWith("\"") || target.endsWith("'")) {
            target = target.substring(0, target.length() - 1).trim();
        }
        if (target.startsWith(";")) {
            target = target.substring(1).trim();
        }
        return target;
    }

    private URI resolveRedirectUri(URI currentUri, String redirectTarget) {
        if (!StringUtils.hasText(redirectTarget)) {
            throw new IllegalArgumentException("redirectTarget is required");
        }
        URI targetUri = toUri(redirectTarget, "redirectTarget");
        return currentUri.resolve(targetUri);
    }

    private Request buildRequest(AdsHttpRequestDto adsHttpRequestDto, String url) {
        String referLink = getRandomReferLink();
        log.info("Using referer link: {}, User-Agent:{}, user device:{}",
                referLink, adsHttpRequestDto.getUserAgent(), adsHttpRequestDto.getDeviceType());
        return new Request.Builder()
                .url(url)
                .header("Accept", "text/html, application/json, text/plain, */*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .header("Referer", referLink)
                .header("X-Device-Type", adsHttpRequestDto.getDeviceType())
                .header("User-Agent", adsHttpRequestDto.getUserAgent())
                .get()
                .build();
    }

    private String getRandomReferLink() {
        return REFER_LINKS.get(ThreadLocalRandom.current().nextInt(REFER_LINKS.size()));
    }

    private URI toRequestUri(String value, String fieldName) {
        String normalized = requireText(value, fieldName + " is required");
        return toUri(normalized, fieldName);
    }

    private URI toUri(String value, String fieldName) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException ex) {
            String sanitized = sanitizeUriValue(value);
            try {
                return URI.create(sanitized);
            } catch (IllegalArgumentException nested) {
                throw new IllegalArgumentException(fieldName + " is invalid URL: " + value, nested);
            }
        }
    }

    private String sanitizeUriValue(String value) {
        StringBuilder sanitized = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '%' && !hasValidPercentEncoding(value, i)) {
                sanitized.append("%25");
            } else {
                sanitized.append(ch);
            }
        }
        return sanitized.toString().replace(" ", "%20").replace("|", "%7C");
    }

    private boolean hasValidPercentEncoding(String value, int index) {
        if (index + 2 >= value.length()) {
            return false;
        }
        return isHexDigit(value.charAt(index + 1)) && isHexDigit(value.charAt(index + 2));
    }

    private boolean isHexDigit(char ch) {
        return (ch >= '0' && ch <= '9')
                || (ch >= 'a' && ch <= 'f')
                || (ch >= 'A' && ch <= 'F');
    }


    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

}
