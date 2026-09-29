package com.admire.cars.runner.controller;

import com.admire.cars.runner.dto.TrackClickMeta;
import com.admire.cars.runner.dto.TrackRedirectResult;
import com.admire.cars.runner.service.TrackLandingService;
import com.admire.cars.runner.service.TrackRedirectService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Locale;

/**
 * 追踪跳转端点 / Tracking redirect endpoint: /r/{slug}
 * <p>
 * 合规约束 / compliance constraints:
 * 1. 仅返回 302 + Location, 响应体为空, 不渲染任何页面 / 302 with empty body, never renders a page
 * 2. UA / IP / Referer 只写日志, 绝不参与跳转决策, 避免 cloak / visitor attributes are logged only, never branch
 */
@Slf4j
@RestController
public class TrackRedirectController {

    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String HEADER_CF_IP_COUNTRY = "CF-IPCountry";
    private static final String HEADER_REFERRER_POLICY = "Referrer-Policy";

    private final TrackRedirectService trackRedirectService;

    private final TrackLandingService trackLandingService;

    // 兜底地址, campaign 不可用时跳转 / fallback destination when campaign is unavailable
    @Value("${tracker.fallback-url:}")
    private String fallbackUrl;

    // 跳转时下发的引荐策略, 默认不带 Referer / referrer policy on redirect, defaults to no referrer
    @Value("${tracker.referrer-policy:no-referrer}")
    private String referrerPolicy;

    public TrackRedirectController(
            TrackRedirectService trackRedirectService,
            TrackLandingService trackLandingService) {
        this.trackRedirectService = trackRedirectService;
        this.trackLandingService = trackLandingService;
    }

    @GetMapping("/r/{slug}")
    public ResponseEntity<Void> redirect(
            @PathVariable String slug,
            @RequestParam(value = "gclid", required = false) String gclid,
            @RequestParam(value = "lpurl", required = false) String landingPageUrl,
            HttpServletRequest request) {

        TrackClickMeta meta = buildMeta(request);
        TrackRedirectResult result = trackRedirectService.resolve(slug, gclid, landingPageUrl, meta);

        if (result == null || result.getTargetUrl() == null || result.getTargetUrl().isBlank()) {
            log.warn("redirect fallback used, slug={}", slug);
            if (fallbackUrl == null || fallbackUrl.isBlank()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            return redirectTo(fallbackUrl);
        }

        return redirectTo(result.getTargetUrl());
    }

    /**
     * 按落地页路由 / route by landing page.
     * Google tracking template 形如 https://track.example.com/?url={lpurl}
     * url 参数带的是广告落地页, 在库里找已入库的联盟链接并 302 过去, 不区分归属.
     */
    @GetMapping("/")
    public ResponseEntity<Void> redirectByLanding(
            @RequestParam(value = "url", required = false) String url,
            @RequestParam(value = "gclid", required = false) String gclid,
            HttpServletRequest request) {

        if (url == null || url.isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        String requestedUrl = url.trim();
        if (!isSafeRedirectTarget(requestedUrl)) {
            log.warn("unsafe redirect target rejected, url={}", requestedUrl);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        TrackClickMeta meta = buildMeta(request);
        String target = trackLandingService.resolve(requestedUrl, gclid, meta);

        // 未命中时回落到原落地页, 保证访客仍能到达商家
        // fall back to the requested landing page so the visitor still reaches the merchant
        if (target == null || target.isBlank()) {
            log.warn("landing route fallback to original url={}", requestedUrl);
            return redirectTo(requestedUrl);
        }

        return redirectTo(target);
    }

    // 只允许 http/https, 避免 javascript: 等协议 / allow http and https only
    private boolean isSafeRedirectTarget(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private ResponseEntity<Void> redirectTo(String targetUrl) {
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(targetUrl));
        // 必须挂在 302 这一跳上, 浏览器跟随重定向时才会按该策略重算 Referer
        // must ride on the 302 itself so the browser recomputes the referrer for the next hop
        if (referrerPolicy != null && !referrerPolicy.isBlank()) {
            headers.set(HEADER_REFERRER_POLICY, referrerPolicy.trim());
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .cacheControl(CacheControl.noStore())
                .headers(headers)
                .build();
    }

    // 提取点击上下文, 仅用于记录 / extract click context, record only
    private TrackClickMeta buildMeta(HttpServletRequest request) {
        TrackClickMeta meta = new TrackClickMeta();
        meta.setIp(resolveClientIp(request));
        meta.setUserAgent(request.getHeader(HttpHeaders.USER_AGENT));
        meta.setReferer(request.getHeader(HttpHeaders.REFERER));
        meta.setCountry(request.getHeader(HEADER_CF_IP_COUNTRY));
        meta.setDevice(resolveDevice(meta.getUserAgent()));
        return meta;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader(HEADER_X_FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String resolveDevice(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "UNKNOWN";
        }
        String lower = userAgent.toLowerCase();
        if (lower.contains("mobile") || lower.contains("android") || lower.contains("iphone")) {
            return "MOBILE";
        }
        if (lower.contains("tablet") || lower.contains("ipad")) {
            return "TABLET";
        }
        return "DESKTOP";
    }
}
