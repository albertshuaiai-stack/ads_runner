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
        try {
            String currentUrl = affiliateUrl;
            for (int sequence = 1; sequence <= adsConfig.getMaxRedirects(); sequence++) {
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

                Response response;
                try {
                    response = httpClient.newCall(request).execute();
                } catch (IOException proxyIoException) {
                    String message = proxyIoException.getMessage();
                    log.error("Affiliate Ads Test Exception. proxy protocol={} proxy info={} Invoke URL={} message={}",
                            ipProxyInfo.getProxyProtocol(), ipProxyInfo.getProxyInfo(), currentUrl, message);
                    return new AffiliateAdsTestResponseDto(
                            "500", "", message);
                }

                int statusCode = response.code();
                String locationHeader = response.header("Location");
                response.close();

                if (statusCode < 300 || statusCode >= 400) {
                    if (isLandingPage(currentUrl, landingPageUrl) && statusCode >= 200 && statusCode < 300) {
                        return new AffiliateAdsTestResponseDto("200", currentUrl, "");
                    }
                    return new AffiliateAdsTestResponseDto(
                            "400", "", "Non-redirect status code received: " + statusCode);
                }

                if (!StringUtils.hasText(locationHeader)) {
                    return new AffiliateAdsTestResponseDto(
                            "400", "", "Redirect status code received but no Location header found");
                }

                // Resolve relative URLs
                currentUrl = resolveUrl(currentUrl, locationHeader);

                if (isLandingPage(currentUrl, landingPageUrl)) {
                    return new AffiliateAdsTestResponseDto(
                            "200", currentUrl, "");
                }
            }
            return new AffiliateAdsTestResponseDto(
                    "400", "", "Maximum redirects reached without reaching the landing page");
        } catch (IOException e) {
            log.error("Affiliate Ads Test failed. proxy protocol={} proxy info={} message={}",
                    ipProxyInfo.getProxyProtocol(), ipProxyInfo.getProxyInfo(), e.getMessage());
            return new AffiliateAdsTestResponseDto("500", "", e.getMessage());
        }
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
