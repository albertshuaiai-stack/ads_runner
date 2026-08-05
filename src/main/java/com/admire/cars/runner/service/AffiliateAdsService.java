package com.admire.cars.runner.service;


import com.admire.cars.runner.config.AdsConfig;
import com.admire.cars.runner.constant.Constant;
import com.admire.cars.runner.dto.AffiliateAdsTestResponseDto;
import com.admire.cars.runner.dto.IpVerificationDto;
import com.admire.cars.runner.dto.ProxyConfigurationDto;
import com.admire.cars.runner.entity.AffiliateAdsSync;
import com.admire.cars.runner.entity.IpProxyInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Service
public class AffiliateAdsService {

    private static final Logger log = LoggerFactory.getLogger(AffiliateAdsService.class);


    @Autowired
    private AdsConfig adsConfig;


    public AffiliateAdsTestResponseDto getAffiliateAds(OkHttpClient httpClient,
                                                       AffiliateAdsSync affiliateAdsSync,
                                                       IpProxyInfo ipProxyInfo) {
        final String landingPageUrl = requireText(affiliateAdsSync.getSiteUrl(), "landingPageUrl is required");
        String affiliateUrl = requireText(affiliateAdsSync.getTrackingUrl(), "tracking id is required");
        String currentUrl = affiliateUrl;
        String lastError = null;
        int lastStatusCode = -1;
        final int maxRequests = 10;

        for (int requestCount = 1; requestCount <= maxRequests; requestCount++) {
            Request request = new Request.Builder()
                    .url(currentUrl)
                    .header("Accept", "text/html, application/json, text/plain, */*")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Cache-Control", "no-cache")
                    .header("Pragma", "no-cache")
                    .header("X-Device-Type", Constant.DEVICE_TYPE_DESK)
                    .header("User-Agent", Constant.DEFAULT_DESKTOP_USER_AGENT)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                lastStatusCode = response.code();
                String locationHeader = response.header("Location");

                if (isLandingPage(currentUrl, landingPageUrl)) {
                    return new AffiliateAdsTestResponseDto("200", currentUrl, "");
                }

                if (lastStatusCode >= 300 && lastStatusCode < 400) {
                    if (!StringUtils.hasText(locationHeader)) {
                        lastError = "Redirect status code received but no Location header found";
                        continue;
                    }
                    try {
                        currentUrl = resolveUrl(currentUrl, locationHeader);
                    } catch (IOException resolveException) {
                        lastError = resolveException.getMessage();
                        continue;
                    }

                    if (isLandingPage(currentUrl, landingPageUrl)) {
                        return new AffiliateAdsTestResponseDto("200", currentUrl, "");
                    }
                } else {
                    lastError = "Non-redirect status code received: " + lastStatusCode;
                }
            } catch (IOException proxyIoException) {
                lastError = proxyIoException.getMessage();
                log.warn("Affiliate Ads Test request failed (attempt {}/{}). proxy protocol={} proxy info={} Invoke URL={} message={}",
                        requestCount, maxRequests,
                        ipProxyInfo.getProxyProtocol(), ipProxyInfo.getProxyInfo(), currentUrl, lastError);
            }
        }

        String error = "Maximum request limit reached (" + maxRequests + ") without reaching landing page prefix";
        if (lastStatusCode > 0) {
            error = error + ". Last status=" + lastStatusCode;
        }
        if (StringUtils.hasText(lastError)) {
            error = error + ". Last error=" + lastError;
        }
        return new AffiliateAdsTestResponseDto("400", "", error);
    }

    private String resolveUrl(String baseUrl, String relativeUrl) throws IOException {
        if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
            return relativeUrl;
        }
        try {
            java.net.URI base = java.net.URI.create(baseUrl);
            java.net.URI resolved = base.resolve(relativeUrl);
            return resolved.toString();
        } catch (Exception e) {
            throw new IOException("Failed to resolve URL: " + e.getMessage(), e);
        }
    }

    private boolean isLandingPage(final String url, final String landingPage) {
        return url.startsWith(landingPage);
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

}
