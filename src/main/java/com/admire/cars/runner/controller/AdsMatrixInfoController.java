package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.AdsMatrixInfo;
import com.admire.cars.runner.service.AdsMatrixInfoService;
import com.admire.cars.runner.service.AdsAutoTaskSchedulerService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/matrix-ads")
public class AdsMatrixInfoController {

    private final AdsMatrixInfoService adsMatrixInfoService;
    private final AdsAutoTaskSchedulerService adsAutoTaskSchedulerService;

    public AdsMatrixInfoController(AdsMatrixInfoService adsMatrixInfoService, AdsAutoTaskSchedulerService adsAutoTaskSchedulerService) {
        this.adsMatrixInfoService = adsMatrixInfoService;
        this.adsAutoTaskSchedulerService = adsAutoTaskSchedulerService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody AdsMatrixInfo adsMatrixInfo, HttpServletRequest request) {
        try {
            Long userId = getUserId(request);
            AdsMatrixInfo created = adsMatrixInfoService.create(adsMatrixInfo, userId);
            enrichExecuteTimes(created);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "ADS_MATRIX_INFO created successfully");
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
    public ResponseEntity<AdsMatrixInfo> getById(@PathVariable Long id) {
        try {
            AdsMatrixInfo adsMatrixInfo = adsMatrixInfoService.getById(id);
            enrichExecuteTimes(adsMatrixInfo);
            return ResponseEntity.ok(adsMatrixInfo);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping
    public ResponseEntity<Page<AdsMatrixInfo>> search(
            @RequestParam(required = false) String campainName,
            @RequestParam(required = false) String platformName,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        Page<AdsMatrixInfo> adsMatrixInfos = adsMatrixInfoService.search(
                campainName,
                platformName,
                status,
                userId,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id")));
        return ResponseEntity.ok(new PageImpl<>(
                adsMatrixInfos.getContent().stream().map(this::enrichExecuteTimes).toList(),
                adsMatrixInfos.getPageable(),
                adsMatrixInfos.getTotalElements()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @RequestBody AdsMatrixInfo updateData, HttpServletRequest request) {
        try {
            Long userId = getUserId(request);
            AdsMatrixInfo updated = adsMatrixInfoService.update(id, updateData, userId);
            enrichExecuteTimes(updated);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "ADS_MATRIX_INFO updated successfully");
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
            adsMatrixInfoService.delete(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "ADS_MATRIX_INFO deleted successfully");
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

    private AdsMatrixInfo enrichExecuteTimes(AdsMatrixInfo adsMatrixInfo) {
        AdsAutoTaskSchedulerService.ExecuteTimeInfo executeTimeInfo =
                adsAutoTaskSchedulerService.getExecuteTimeInfo(adsMatrixInfo.getId(), adsMatrixInfo.getAdsOwner(), "Matrix");
        adsMatrixInfo.setLastExecuteTime(executeTimeInfo.lastExecuteTime());
        adsMatrixInfo.setNextExecuteTime(executeTimeInfo.nextExecuteTime());
        return adsMatrixInfo;
    }
}
