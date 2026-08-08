package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.AdsTaskLog;
import com.admire.cars.runner.service.AdsTaskLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ads-task-logs")
public class AdsTaskLogController {

    private final AdsTaskLogService adsTaskLogService;

    public AdsTaskLogController(AdsTaskLogService adsTaskLogService) {
        this.adsTaskLogService = adsTaskLogService;
    }

    @GetMapping
    public ResponseEntity<Page<AdsTaskLog>> queryAdsTaskLogs(
            @RequestParam(required = false) String adsOwner,
            @RequestParam(required = false) String adsType,
            @RequestParam(required = false) String adsName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        Page<AdsTaskLog> logs = adsTaskLogService.queryLogs(
                adsOwner,
                adsType,
                adsName,
                userId,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id")));
        return ResponseEntity.ok(logs);
    }

    private Long getUserId(HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        if (uid == null) {
            throw new IllegalArgumentException("userId not found in request");
        }
        return (Long) uid;
    }
}
