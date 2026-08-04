package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.AffiliateAdsSync;
import com.admire.cars.runner.service.AffiliateAdsSyncService;
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
@RequestMapping("/api/affiliate-ads-sync")
public class AffiliateAdsSyncController {

    private final AffiliateAdsSyncService affiliateAdsSyncService;

    public AffiliateAdsSyncController(AffiliateAdsSyncService affiliateAdsSyncService) {
        this.affiliateAdsSyncService = affiliateAdsSyncService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody AffiliateAdsSync affiliateAdsSync, HttpServletRequest request) {
        try {
            AffiliateAdsSync created = affiliateAdsSyncService.create(affiliateAdsSync, getUserId(request));
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "AFFILIATE_ADS_SYNC created successfully");
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
    public ResponseEntity<AffiliateAdsSync> getById(@PathVariable Long id, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(affiliateAdsSyncService.getById(id, getUserId(request)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping
    public ResponseEntity<Page<AffiliateAdsSync>> search(
            @RequestParam(required = false) String adsOwner,
            @RequestParam(required = false) String affiliateNetwork,
            @RequestParam(required = false) String siteName,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        Page<AffiliateAdsSync> result = affiliateAdsSyncService.search(
                adsOwner,
                affiliateNetwork,
                siteName,
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
            @RequestBody AffiliateAdsSync updateData,
            HttpServletRequest request) {
        try {
            AffiliateAdsSync updated = affiliateAdsSyncService.update(id, updateData, getUserId(request));
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "AFFILIATE_ADS_SYNC updated successfully");
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
            affiliateAdsSyncService.delete(id, getUserId(request));
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "AFFILIATE_ADS_SYNC deleted successfully");
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
