package com.admire.cars.runner.controller;

import com.admire.cars.runner.service.TrackReportService;
import com.admire.cars.runner.service.TrackReportService.CampaignClicks;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 接收 Ads 脚本上报的 campaign 点击数 / receive click reports from the Google Ads script
 */
@RestController
@RequestMapping("/api/track")
@Slf4j
public class TrackReportController {

    private final TrackReportService trackReportService;

    public TrackReportController(TrackReportService trackReportService) {
        this.trackReportService = trackReportService;
    }

    @PostMapping("/report")
    public ResponseEntity<Map<String, Object>> report(
            @RequestParam(value = "api_key", required = false) String apiKey,
            @RequestBody(required = false) TrackReportRequest request) {
        try {
            if (!StringUtils.hasText(apiKey)) {
                throw new IllegalArgumentException("api_key is required");
            }
            List<CampaignClicks> rows = request == null || request.data() == null
                    ? List.of() : request.data();
            int updated = trackReportService.applyCampaignClicks(apiKey.trim(), rows);
            log.info("click report received rows={} updated={}", rows.size(), updated);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "received", rows.size(),
                    "updated", updated));
        } catch (IllegalArgumentException e) {
            String message = e.getMessage() == null ? "bad request" : e.getMessage();
            log.warn("click report rejected: {}", message);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", message));
        }
    }

    public record TrackReportRequest(List<CampaignClicks> data) {
    }
}
