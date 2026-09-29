package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.RanSiteIdPool;
import com.admire.cars.runner.entity.TrackCampaign;
import com.admire.cars.runner.entity.TrackClick;
import com.admire.cars.runner.entity.TrackConversion;
import com.admire.cars.runner.entity.TrackHealthCheck;
import com.admire.cars.runner.service.TrackCampaignService;
import com.admire.cars.runner.service.TrackComplianceService;
import com.admire.cars.runner.service.TrackPoolService;
import com.admire.cars.runner.service.TrackQueryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

// 追踪配置管理 / tracker configuration management
@RestController
@RequestMapping("/api/track")
public class TrackAdminController {

    private final TrackCampaignService campaignService;
    private final TrackPoolService poolService;
    private final TrackQueryService queryService;
    private final TrackComplianceService complianceService;

    public TrackAdminController(
            TrackCampaignService campaignService,
            TrackPoolService poolService,
            TrackQueryService queryService,
            TrackComplianceService complianceService) {
        this.campaignService = campaignService;
        this.poolService = poolService;
        this.queryService = queryService;
        this.complianceService = complianceService;
    }

    @PostMapping("/campaigns")
    public ResponseEntity<Map<String, Object>> createCampaign(@RequestBody TrackCampaign campaign, HttpServletRequest request) {
        try {
            TrackCampaign created = campaignService.create(campaign, getUserId(request));
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "TRACK_CAMPAIGN created successfully");
            response.put("id", created.getId());
            response.put("data", created);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    @GetMapping("/campaigns/{id}")
    public ResponseEntity<TrackCampaign> getCampaign(@PathVariable Long id, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(campaignService.getById(id, getUserId(request)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/campaigns")
    public ResponseEntity<Page<TrackCampaign>> searchCampaigns(
            @RequestParam(required = false) String slug,
            @RequestParam(required = false) String campaignName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String adsOwner,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        Page<TrackCampaign> results = campaignService.search(
                slug,
                campaignName,
                status,
                adsOwner,
                getUserId(request),
                defaultPage(page, size));
        return ResponseEntity.ok(results);
    }

    @PutMapping("/campaigns/{id}")
    public ResponseEntity<Map<String, Object>> updateCampaign(@PathVariable Long id, @RequestBody TrackCampaign campaign, HttpServletRequest request) {
        try {
            TrackCampaign updated = campaignService.update(id, campaign, getUserId(request));
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "TRACK_CAMPAIGN updated successfully");
            response.put("data", updated);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    @DeleteMapping("/campaigns/{id}")
    public ResponseEntity<Map<String, Object>> deleteCampaign(@PathVariable Long id, HttpServletRequest request) {
        try {
            campaignService.delete(id, getUserId(request));
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "TRACK_CAMPAIGN deleted successfully");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    @PostMapping("/pools")
    public ResponseEntity<Map<String, Object>> createPool(@RequestBody RanSiteIdPool pool, HttpServletRequest request) {
        try {
            RanSiteIdPool created = poolService.create(pool, getUserId(request));
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "RAN_SITE_ID_POOL created successfully");
            response.put("id", created.getId());
            response.put("data", created);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    @GetMapping("/pools/{id}")
    public ResponseEntity<RanSiteIdPool> getPool(@PathVariable Long id, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(poolService.getById(id, getUserId(request)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/pools")
    public ResponseEntity<Page<RanSiteIdPool>> searchPools(
            @RequestParam(required = false) String poolCode,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String adsOwner,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        Page<RanSiteIdPool> results = poolService.search(
                poolCode,
                siteId,
                status,
                adsOwner,
                getUserId(request),
                defaultPage(page, size));
        return ResponseEntity.ok(results);
    }

    @PutMapping("/pools/{id}")
    public ResponseEntity<Map<String, Object>> updatePool(@PathVariable Long id, @RequestBody RanSiteIdPool pool, HttpServletRequest request) {
        try {
            RanSiteIdPool updated = poolService.update(id, pool, getUserId(request));
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "RAN_SITE_ID_POOL updated successfully");
            response.put("data", updated);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    @DeleteMapping("/pools/{id}")
    public ResponseEntity<Map<String, Object>> deletePool(@PathVariable Long id, HttpServletRequest request) {
        try {
            poolService.delete(id, getUserId(request));
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "RAN_SITE_ID_POOL deleted successfully");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    @PostMapping("/pools/reset-daily")
    public ResponseEntity<Map<String, Object>> resetDailyUsage(
            @RequestParam(required = false) String poolCode,
            HttpServletRequest request) {
        try {
            int count = poolService.resetDailyUsage(poolCode, getUserId(request));
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "daily usage reset");
            response.put("count", count);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    @GetMapping("/clicks")
    public ResponseEntity<Page<TrackClick>> searchClicks(
            @RequestParam(required = false) String slug,
            @RequestParam(required = false) String clickId,
            @RequestParam(required = false) String campaignName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String adsOwner,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        Page<TrackClick> results = queryService.searchClicks(
                slug,
                clickId,
                campaignName,
                startDate,
                endDate,
                adsOwner,
                getUserId(request),
                defaultPage(page, size));
        return ResponseEntity.ok(results);
    }

    @GetMapping("/conversions")
    public ResponseEntity<Page<TrackConversion>> searchConversions(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String clickId,
            @RequestParam(required = false) String attributed,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String adsOwner,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        Page<TrackConversion> results = queryService.searchConversions(
                orderNo,
                clickId,
                attributed,
                startDate,
                endDate,
                adsOwner,
                getUserId(request),
                defaultPage(page, size));
        return ResponseEntity.ok(results);
    }

    // 触发一次链路合规巡检 / run one compliance check for a campaign
    @PostMapping("/campaigns/{id}/check")
    public ResponseEntity<Map<String, Object>> checkCampaign(@PathVariable Long id, HttpServletRequest request) {
        try {
            TrackHealthCheck result = complianceService.check(id, getUserId(request));
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", result.getMessage());
            response.put("passed", result.getPassed());
            response.put("data", result);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    @GetMapping("/checks")
    public ResponseEntity<Page<TrackHealthCheck>> searchChecks(
            @RequestParam(required = false) String slug,
            @RequestParam(required = false) String adsOwner,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        Page<TrackHealthCheck> results = queryService.searchHealthChecks(
                slug,
                adsOwner,
                getUserId(request),
                defaultPage(page, size));
        return ResponseEntity.ok(results);
    }

    private PageRequest defaultPage(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        return PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Direction.DESC, "id"));
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    private Long getUserId(HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        if (uid == null) {
            throw new IllegalArgumentException("userId not found in request");
        }
        return (Long) uid;
    }
}
