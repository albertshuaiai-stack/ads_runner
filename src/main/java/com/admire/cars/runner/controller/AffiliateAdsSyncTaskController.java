package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.AffiliateAdsSyncTask;
import com.admire.cars.runner.service.AffiliateAdsSyncTaskAsyncService;
import com.admire.cars.runner.service.AffiliateAdsSyncTaskSchedulerService;
import com.admire.cars.runner.service.AffiliateAdsSyncTaskService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/affiliate-ads-sync-task")
public class AffiliateAdsSyncTaskController {

    private final AffiliateAdsSyncTaskService affiliateAdsSyncTaskService;
    private final AffiliateAdsSyncTaskAsyncService affiliateAdsSyncTaskAsyncService;
    private final AffiliateAdsSyncTaskSchedulerService affiliateAdsSyncTaskSchedulerService;

    public AffiliateAdsSyncTaskController(
            AffiliateAdsSyncTaskService affiliateAdsSyncTaskService,
            AffiliateAdsSyncTaskAsyncService affiliateAdsSyncTaskAsyncService,
            AffiliateAdsSyncTaskSchedulerService affiliateAdsSyncTaskSchedulerService) {
        this.affiliateAdsSyncTaskService = affiliateAdsSyncTaskService;
        this.affiliateAdsSyncTaskAsyncService = affiliateAdsSyncTaskAsyncService;
        this.affiliateAdsSyncTaskSchedulerService = affiliateAdsSyncTaskSchedulerService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody AffiliateAdsSyncTask task, HttpServletRequest request) {
        try {
            AffiliateAdsSyncTask created = affiliateAdsSyncTaskService.create(task, getUserId(request));
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "AFFILIATE_ADS_SYNC_TASK created successfully");
            response.put("id", created.getId());
            response.put("data", created);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<AffiliateAdsSyncTask> getById(@PathVariable Long id, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(affiliateAdsSyncTaskService.getById(id, getUserId(request)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping
    public ResponseEntity<Page<AffiliateAdsSyncTask>> search(
            @RequestParam(required = false) String adsOwner,
            @RequestParam(required = false) String affiliateAdsSyncConfigId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        Long parsedConfigId = parseConfigId(affiliateAdsSyncConfigId);
        Page<AffiliateAdsSyncTask> result = affiliateAdsSyncTaskService.search(
                adsOwner,
                parsedConfigId,
                getUserId(request),
                PageRequest.of(
                        safePage,
                        safeSize,
                        Sort.by(Sort.Direction.DESC, "createDate").and(Sort.by(Sort.Direction.DESC, "id"))));
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable Long id,
            @RequestBody AffiliateAdsSyncTask updateData,
            HttpServletRequest request) {
        try {
            AffiliateAdsSyncTask updated = affiliateAdsSyncTaskService.update(id, updateData, getUserId(request));
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "AFFILIATE_ADS_SYNC_TASK updated successfully");
            response.put("data", updated);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id, HttpServletRequest request) {
        try {
            affiliateAdsSyncTaskService.delete(id, getUserId(request));
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "AFFILIATE_ADS_SYNC_TASK deleted successfully");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @PostMapping("/{id}/syncAds")
    public ResponseEntity<Map<String, Object>> syncAds(@PathVariable Long id, HttpServletRequest request) {
        try {
            Map<String, Object> response = new HashMap<>();
            Long userId = getUserId(request);
            AffiliateAdsSyncTask inProgress = affiliateAdsSyncTaskService.markInProgress(id, userId);
            affiliateAdsSyncTaskAsyncService.syncAdsAsync(id);
            response.put("success", true);
            response.put("message", "AFFILIATE_ADS_SYNC_TASK started");
            response.put("status", "IN_PROGRESS");
            response.put("data", inProgress);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    private Long getUserId(HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        if (uid == null) {
            throw new IllegalArgumentException("userId not found in request");
        }
        return (Long) uid;
    }

    private Long parseConfigId(String affiliateAdsSyncConfigId) {
        if (affiliateAdsSyncConfigId == null) {
            return null;
        }
        String trimmed = affiliateAdsSyncConfigId.trim();
        if (trimmed.isEmpty() || "NaN".equalsIgnoreCase(trimmed)) {
            return null;
        }
        try {
            return Long.valueOf(trimmed);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
