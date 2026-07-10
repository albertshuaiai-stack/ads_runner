package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.ShiftLinkAud;
import com.admire.cars.runner.service.ShiftLinkAudService;
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
@RequestMapping("/api/shift-link-audits")
public class ShiftLinkAuditController {

    private final ShiftLinkAudService shiftLinkAudService;

    public ShiftLinkAuditController(ShiftLinkAudService shiftLinkAudService) {
        this.shiftLinkAudService = shiftLinkAudService;
    }

    @GetMapping
    public ResponseEntity<Page<ShiftLinkAud>> searchShiftLinkAudits(
            @RequestParam(required = false) String adsType,
            @RequestParam(required = false) String platformName,
            @RequestParam(required = false) String campainName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        Page<ShiftLinkAud> audits = shiftLinkAudService.searchShiftLinkAudits(
                adsType,
                platformName,
                campainName,
                userId,
                PageRequest.of(
                        safePage,
                        safeSize,
                        Sort.by(Sort.Direction.DESC, "operationDate").and(Sort.by(Sort.Direction.DESC, "audId"))));
        return ResponseEntity.ok(audits);
    }

    private Long getUserId(HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        if (uid == null) {
            throw new IllegalArgumentException("userId not found in request");
        }
        return (Long) uid;
    }
}
