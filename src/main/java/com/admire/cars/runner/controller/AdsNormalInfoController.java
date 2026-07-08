package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.AdsNormalInfo;
import com.admire.cars.runner.service.AdsNormalInfoService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/normal-ads")
public class AdsNormalInfoController {

    private final AdsNormalInfoService adsNormalInfoService;

    public AdsNormalInfoController(AdsNormalInfoService adsNormalInfoService) {
        this.adsNormalInfoService = adsNormalInfoService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody AdsNormalInfo adsNormalInfo) {
        try {
            AdsNormalInfo created = adsNormalInfoService.create(adsNormalInfo);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "ADS_NORMAL_INFO created successfully");
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
    public ResponseEntity<AdsNormalInfo> getById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(adsNormalInfoService.getById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping
    public ResponseEntity<Page<AdsNormalInfo>> search(
            @RequestParam(required = false) String campainName,
            @RequestParam(required = false) String platformName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String adsOwner,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        return ResponseEntity.ok(adsNormalInfoService.search(
                campainName,
                platformName,
                status,
                adsOwner,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id"))));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @RequestBody AdsNormalInfo updateData) {
        try {
            AdsNormalInfo updated = adsNormalInfoService.update(id, updateData);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "ADS_NORMAL_INFO updated successfully");
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
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        try {
            adsNormalInfoService.delete(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "ADS_NORMAL_INFO deleted successfully");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }
}
