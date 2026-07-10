package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.ShiftLinkLog;
import com.admire.cars.runner.service.ShiftLinkLogService;
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
@RequestMapping("/api/shift-link-logs")
public class ShiftLinkLogController {

    private final ShiftLinkLogService shiftLinkLogService;

    public ShiftLinkLogController(ShiftLinkLogService shiftLinkLogService) {
        this.shiftLinkLogService = shiftLinkLogService;
    }

    @GetMapping
    public ResponseEntity<Page<ShiftLinkLog>> queryShiftLinkLogs(
            @RequestParam(required = false) String adsType,
            @RequestParam(required = false) String platformName,
            @RequestParam(required = false) String adsName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        Page<ShiftLinkLog> logs = shiftLinkLogService.queryLogs(
                adsType,
                platformName,
                adsName,
                userId,
                PageRequest.of(
                        safePage,
                        safeSize,
                        Sort.by(Sort.Direction.DESC, "createDate").and(Sort.by(Sort.Direction.DESC, "id"))));
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
