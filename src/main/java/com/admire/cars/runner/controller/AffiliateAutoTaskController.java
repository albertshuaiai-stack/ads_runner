package com.admire.cars.runner.controller;

import com.admire.cars.runner.constant.Constant;
import com.admire.cars.runner.constant.StatusConstant;
import com.admire.cars.runner.entity.AffiliateAutoTask;
import com.admire.cars.runner.service.AffiliateAutoTaskService;
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
@RequestMapping("/api/affiliate-auto-task")
public class AffiliateAutoTaskController {

    private final AffiliateAutoTaskService affiliateAutoTaskService;

    public AffiliateAutoTaskController(AffiliateAutoTaskService affiliateAutoTaskService) {
        this.affiliateAutoTaskService = affiliateAutoTaskService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody AffiliateAutoTask task, HttpServletRequest request) {
        try {
            AffiliateAutoTask created = affiliateAutoTaskService.create(task, getUserId(request));
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "AFFILIATE_AUTO_TASK created successfully");
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
    public ResponseEntity<AffiliateAutoTask> getById(@PathVariable Long id, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(affiliateAutoTaskService.getById(id, getUserId(request)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping
    public ResponseEntity<Page<AffiliateAutoTask>> search(
            @RequestParam(required = false) String adsOwner,
            @RequestParam(required = false) String affiliateNetwork,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        Page<AffiliateAutoTask> result = affiliateAutoTaskService.search(
                adsOwner,
                affiliateNetwork,
                region,
                status,
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
            @RequestBody AffiliateAutoTask updateData,
            HttpServletRequest request) {
        try {
            AffiliateAutoTask updated = affiliateAutoTaskService.update(id, updateData, getUserId(request));
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "AFFILIATE_AUTO_TASK updated successfully");
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
            affiliateAutoTaskService.delete(id, getUserId(request));
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "AFFILIATE_AUTO_TASK deleted successfully");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    /**
     * Auto Sync affiliate ads
     * @param id
     * @param request
     * @return
     */
    @PostMapping("/{id}/syncAds")
    public ResponseEntity<Map<String, Object>> syncAds(@PathVariable Long id, HttpServletRequest request) {
        try {
            Map<String, Object> response = new HashMap<>();
            Long userId = getUserId(request);
            AffiliateAutoTask inProgress = affiliateAutoTaskService.markInProgress(id, userId);
            affiliateAutoTaskService.syncAdsAsync(id);
            response.put("success", true);
            response.put("message", "AFFILIATE_AUTO_TASK sync started");
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

    /**
     * Auto test affiliate ads
     * @param id
     * @param request
     * @return
     */
    @PostMapping("/{id}/testAds")
    public ResponseEntity<Map<String, Object>> testAds(@PathVariable Long id, HttpServletRequest request) {
        try {
            Long userId = getUserId(request);
            AffiliateAutoTask inProgress = affiliateAutoTaskService.markInProgress(id, userId);
            affiliateAutoTaskService.testAdsAsync(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "AFFILIATE_AUTO_TASK test started");
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



    @PostMapping("/{id}/testAd")
    public ResponseEntity<Map<String, Object>> testAd(@PathVariable Long id, HttpServletRequest request) {
        try {
            affiliateAutoTaskService.testAd(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "AFFILIATE_AUTO_TASK test started");
            response.put("status", StatusConstant.IN_PROGRESS);
            return ResponseEntity.accepted().body(response);
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




}
