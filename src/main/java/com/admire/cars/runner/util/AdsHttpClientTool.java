package com.admire.cars.runner.util;

import com.admire.cars.runner.constant.StatusConstant;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.util.List;


@Slf4j
@Service
public class AdsHttpClientTool {




    private static final List<Integer> REDIRECT_STATUS_CODES = List.of(200,301, 302, 303, 307, 308);


    public AdsHttpResponseDto applyAffiliateAd(OkHttpClient httpClient,
                                                        AdsHttpRequestDto adsHttpRequestDto) {

        URI requestUri = toRequestUri(adsHttpRequestDto.getAffiliateUrl(), "affiliteUrl");
        String lastError = null;
        int lastStatusCode = -1;
        URI responseUri = null;
        Request request = buildRequest(adsHttpRequestDto);
        try (Response response = httpClient.newCall(request).execute()) {
            lastStatusCode = response.code();
            if (null != response.request()
                    && null != response.request().url()) {
                responseUri = URI.create(response.request().url().toString());
            }
            log.warn("Apply Affiliate Ads. Request URL={} Response={}", requestUri, responseUri);
            if (REDIRECT_STATUS_CODES.contains(lastStatusCode)) {
                if (isLandingPage(responseUri, adsHttpRequestDto.getLandingPageUrl())
                        && lastStatusCode >= 200
                       && lastStatusCode < 300) {
                    return new AdsHttpResponseDto(StatusConstant.SUCCESS, lastStatusCode,responseUri.toString(), "");
                } else {
                    return new AdsHttpResponseDto(StatusConstant.FAILED, lastStatusCode,responseUri.toString(), "Response URL does not match landing page prefix");
                }
            } else {
                return new AdsHttpResponseDto(StatusConstant.FAILED, lastStatusCode,responseUri.toString(), "Response status code is not a redirect or success code");
            }
        } catch (IOException proxyIoException) {
            lastError = proxyIoException.getMessage();
            log.error("Apply Affiliate Ads. Request URL={} Response={}, error={}",
                    requestUri, responseUri, lastError);
        }
        return new AdsHttpResponseDto(StatusConstant.FAILED, lastStatusCode,responseUri.toString(), lastError);
    }


    private boolean isLandingPage(final URI uri, final String landingPage) {
        return uri.toString().startsWith(landingPage);
    }

    private Request buildRequest (AdsHttpRequestDto adsHttpRequestDto) {
        return new Request.Builder()
                .url(adsHttpRequestDto.getAffiliateUrl())
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
}
