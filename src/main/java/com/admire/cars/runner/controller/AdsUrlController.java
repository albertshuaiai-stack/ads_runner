package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.AdsUrl;
import com.admire.cars.runner.entity.AdsUrlAud;
import com.admire.cars.runner.service.AdsUrlService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ads-urls")
public class AdsUrlController {

    private final AdsUrlService adsUrlService;

    public AdsUrlController(AdsUrlService adsUrlService) {
        this.adsUrlService = adsUrlService;
    }

    private Long getUserId(HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        if (uid == null) {
            throw new IllegalArgumentException("userId not found in request");
        }
        return (Long) uid;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createAdsUrl(@RequestBody AdsUrl adsUrl, HttpServletRequest request) {
        try {
            Long userId = getUserId(request);
            AdsUrl created = adsUrlService.createAdsUrl(adsUrl, userId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "ADS_URL created successfully");
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
    public ResponseEntity<AdsUrl> getAdsUrl(@PathVariable Long id) {
        try {
            AdsUrl adsUrl = adsUrlService.getAdsUrlById(id);
            return ResponseEntity.ok(adsUrl);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping
    public ResponseEntity<Page<AdsUrl>> searchAdsUrls(
            @RequestParam(required = false) String platformName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String adsOwner,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        Page<AdsUrl> urls = adsUrlService.searchAdsUrls(
                platformName,
                status,
                adsOwner,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id")));
        return ResponseEntity.ok(urls);
    }

    @GetMapping("/all")
    public ResponseEntity<List<AdsUrl>> getAllAdsUrls() {
        List<AdsUrl> urls = adsUrlService.getAllAdsUrls();
        return ResponseEntity.ok(urls);
    }

    @GetMapping("/campaign/{campaignOwner}")
    public ResponseEntity<List<AdsUrl>> getAdsUrlsByOwner(@PathVariable Long campaignOwner) {
        List<AdsUrl> urls = adsUrlService.getAdsUrlsByCampaignOwner(campaignOwner);
        return ResponseEntity.ok(urls);
    }

    @GetMapping("/platform/{platform}")
    public ResponseEntity<List<AdsUrl>> getAdsUrlsByPlatform(@PathVariable String platform) {
        List<AdsUrl> urls = adsUrlService.getAdsUrlsByPlatform(platform);
        return ResponseEntity.ok(urls);
    }

    @GetMapping("/campaign-name/{capMainName}")
    public ResponseEntity<List<AdsUrl>> getAdsUrlsByCapMainName(@PathVariable String capMainName, HttpServletRequest request) {
        Long userId = getUserId(request);
        List<AdsUrl> urls = adsUrlService.getAdsUrlsByCapMainName(capMainName, userId);
        return ResponseEntity.ok(urls);
    }

    @PostMapping(value = "/bulk-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> bulkUploadByExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "adsType", required = false) String adsType,
            @RequestParam(value = "campainId", required = false) Long campainId,
            HttpServletRequest request) {
        try {
            Long userId = getUserId(request);
            AdsUrlService.BulkUploadResult result = adsUrlService.replaceByExcel(file, userId, adsType, campainId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "ADS_URL bulk upload completed");
            response.put("campaignCount", result.campaignCount());
            response.put("rowCount", result.rowCount());
            response.put("deletedCount", result.deletedCount());
            response.put("insertedCount", result.insertedCount());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @GetMapping(value = "/template", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<ByteArrayResource> downloadTemplate() {
        byte[] content = adsUrlService.createTemplateWorkbook();
        ByteArrayResource resource = new ByteArrayResource(content);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=ads_url_template.xlsx")
                .body(resource);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateAdsUrl(@PathVariable Long id, @RequestBody AdsUrl updateData, HttpServletRequest request) {
        try {
            Long userId = getUserId(request);
            AdsUrl existing = adsUrlService.getAdsUrlById(id);
            if (!existing.getCampaignOwner().equals(userId)) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Unauthorized: you can only update your own URLs");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            AdsUrl updated = adsUrlService.updateAdsUrl(id, updateData, userId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "ADS_URL updated successfully");
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
    public ResponseEntity<Map<String, Object>> deleteAdsUrl(@PathVariable Long id, HttpServletRequest request) {
        try {
            Long userId = getUserId(request);
            adsUrlService.deleteAdsUrl(id, userId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "ADS_URL deleted successfully");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @GetMapping("/{id}/audit")
    public ResponseEntity<Map<String, Object>> getAuditHistory(@PathVariable Long id) {
        try {
            adsUrlService.getAdsUrlById(id);
            List<AdsUrlAud> auditHistory = adsUrlService.getAdsUrlAuditHistory(id);
            Map<String, Object> response = new HashMap<>();
            response.put("id", id);
            response.put("auditHistory", auditHistory);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PostMapping("/{id}/increment-display")
    public ResponseEntity<Map<String, Object>> incrementDisplayTimes(@PathVariable Long id, HttpServletRequest request) {
        try {
            Long userId = getUserId(request);
            AdsUrl existing = adsUrlService.getAdsUrlById(id);
            if (!existing.getCampaignOwner().equals(userId)) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Unauthorized");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            adsUrlService.incrementDisplayTimes(id);
            AdsUrl updated = adsUrlService.getAdsUrlById(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("displayTimes", updated.getDisplayTimes());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }
}
