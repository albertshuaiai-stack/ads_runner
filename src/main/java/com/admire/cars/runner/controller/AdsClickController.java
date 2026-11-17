package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.AdsClickTrack;
import com.admire.cars.runner.service.AdsClickTrackService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Optional;

/**
 * 可选的真实点击追踪: 记录点击ID后 302 到联盟落地页
 * Optional real-click tracking: count the click id then redirect to the affiliate landing page
 */
@RestController
@RequestMapping("/api/click")
@Slf4j
public class AdsClickController {

    private final AdsClickTrackService adsClickTrackService;

    public AdsClickController(AdsClickTrackService adsClickTrackService) {
        this.adsClickTrackService = adsClickTrackService;
    }

    @GetMapping
    public ResponseEntity<Void> click(
            @RequestParam(value = "cid", required = false) String clickId,
            @RequestParam(value = "lp", required = false) String landingPage,
            HttpServletRequest request) {
        Optional<AdsClickTrack> found = adsClickTrackService.recordRealClick(clickId);
        if (found.isEmpty()) {
            log.warn("CLICK_REDIRECT_REJECTED clickId={} reason=unknown_click_id", clickId);
            return ResponseEntity.notFound().build();
        }
        String target = buildTarget(found.get(), landingPage);
        if (!StringUtils.hasText(target)) {
            log.warn("CLICK_REDIRECT_REJECTED clickId={} reason=missing_full_url", clickId);
            return ResponseEntity.badRequest().build();
        }
        try {
            log.info("CLICK_REDIRECT clickId={} normalAdsId={} target={} ip={} ua={}",
                    clickId, found.get().getNormalAdsId(), target,
                    request.getRemoteAddr(), request.getHeader("User-Agent"));
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
        } catch (IllegalArgumentException e) {
            log.warn("CLICK_REDIRECT_REJECTED clickId={} reason=invalid_target", clickId);
            return ResponseEntity.badRequest().build();
        }
    }

    // lp 由 Google Ads 的 {lpurl} 宏注入, 替换规则与 tracking template 一致
    // lp comes from the Google Ads {lpurl} macro, replaced the same way as the tracking template
    private String buildTarget(AdsClickTrack track, String landingPage) {
        String fullUrl = track.getFullUrl();
        if (!StringUtils.hasText(fullUrl)) {
            return null;
        }
        if (!StringUtils.hasText(landingPage)
                || !(landingPage.startsWith("http://") || landingPage.startsWith("https://"))) {
            return fullUrl;
        }
        String landing = track.getLandingPageUrl();
        if (!StringUtils.hasText(landing)) {
            return fullUrl;
        }
        int index = fullUrl.indexOf(landing);
        if (index < 0) {
            return fullUrl;
        }
        return fullUrl.substring(0, index) + landingPage + fullUrl.substring(index + landing.length());
    }
}
