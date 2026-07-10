package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.AdsNormalInfo;
import com.admire.cars.runner.service.AdsNormalInfoService;
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
@RequestMapping("/api/normal-ads")
public class AdsNormalInfoController {

    private final AdsNormalInfoService adsNormalInfoService;
    private final AdsAutoTaskSchedulerService adsAutoTaskSchedulerService;

    public AdsNormalInfoController(AdsNormalInfoService adsNormalInfoService, AdsAutoTaskSchedulerService adsAutoTaskSchedulerService) {
        this.adsNormalInfoService = adsNormalInfoService;
        this.adsAutoTaskSchedulerService = adsAutoTaskSchedulerService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody AdsNormalInfo adsNormalInfo, HttpServletRequest request) {
        try {
            Long userId = getUserId(request);
            AdsNormalInfo created = adsNormalInfoService.create(adsNormalInfo, userId);
            enrichExecuteTimes(created);
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
            AdsNormalInfo adsNormalInfo = adsNormalInfoService.getById(id);
            enrichExecuteTimes(adsNormalInfo);
            return ResponseEntity.ok(adsNormalInfo);
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
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        Page<AdsNormalInfo> adsNormalInfos = adsNormalInfoService.search(
                campainName,
                platformName,
                status,
                adsOwner,
                userId,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id")));
        return ResponseEntity.ok(new PageImpl<>(
                adsNormalInfos.getContent().stream().map(this::enrichExecuteTimes).toList(),
                adsNormalInfos.getPageable(),
                adsNormalInfos.getTotalElements()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @RequestBody AdsNormalInfo updateData, HttpServletRequest request) {
        try {
            Long userId = getUserId(request);
            AdsNormalInfo updated = adsNormalInfoService.update(id, updateData, userId);
            enrichExecuteTimes(updated);
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

    private Long getUserId(HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        if (uid == null) {
            throw new IllegalArgumentException("userId not found in request");
        }
        return (Long) uid;
    }

    private AdsNormalInfo enrichExecuteTimes(AdsNormalInfo adsNormalInfo) {
        AdsAutoTaskSchedulerService.ExecuteTimeInfo executeTimeInfo =
                adsAutoTaskSchedulerService.getExecuteTimeInfo(adsNormalInfo.getId(), adsNormalInfo.getAdsOwner(), "Normal");
        adsNormalInfo.setLastExecuteTime(executeTimeInfo.lastExecuteTime());
        adsNormalInfo.setNextExecuteTime(executeTimeInfo.nextExecuteTime());
        return adsNormalInfo;
    }
}
