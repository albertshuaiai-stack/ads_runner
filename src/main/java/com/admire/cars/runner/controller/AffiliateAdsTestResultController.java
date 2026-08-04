package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.AffiliateAdsTestResult;
import com.admire.cars.runner.service.AffiliateAdsTestResultService;
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
@RequestMapping("/api/affiliate-ads-test-result")
public class AffiliateAdsTestResultController {

    private final AffiliateAdsTestResultService affiliateAdsTestResultService;

    public AffiliateAdsTestResultController(AffiliateAdsTestResultService affiliateAdsTestResultService) {
        this.affiliateAdsTestResultService = affiliateAdsTestResultService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody AffiliateAdsTestResult result, HttpServletRequest request) {
        try {
            AffiliateAdsTestResult created = affiliateAdsTestResultService.create(result, getUserId(request));
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "AFFILIATE_ADS_TEST_RESULT created successfully");
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
    public ResponseEntity<AffiliateAdsTestResult> getById(@PathVariable Long id, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(affiliateAdsTestResultService.getById(id, getUserId(request)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping
    public ResponseEntity<Page<AffiliateAdsTestResult>> search(
            @RequestParam(required = false) String adsOwner,
            @RequestParam(required = false) String affiliateNetwork,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        Page<AffiliateAdsTestResult> result = affiliateAdsTestResultService.search(
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
            @RequestBody AffiliateAdsTestResult updateData,
            HttpServletRequest request) {
        try {
            AffiliateAdsTestResult updated = affiliateAdsTestResultService.update(id, updateData, getUserId(request));
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "AFFILIATE_ADS_TEST_RESULT updated successfully");
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
            affiliateAdsTestResultService.delete(id, getUserId(request));
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "AFFILIATE_ADS_TEST_RESULT deleted successfully");
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
}
