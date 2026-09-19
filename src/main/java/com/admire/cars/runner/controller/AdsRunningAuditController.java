package com.admire.cars.runner.controller;

import com.admire.cars.runner.dto.AdsRunningAuditDto;
import com.admire.cars.runner.entity.AdsRunningAudit;
import com.admire.cars.runner.service.AdsRunningAuditService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ads-running-audits")
public class AdsRunningAuditController {

    private final AdsRunningAuditService adsRunningAuditService;

    public AdsRunningAuditController(AdsRunningAuditService adsRunningAuditService) {
        this.adsRunningAuditService = adsRunningAuditService;
    }

    private Long getCurrentUserId(HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        if (uid == null) {
            throw new IllegalArgumentException("userId not found in request");
        }
        return (Long) uid;
    }

    @GetMapping("/query")
    @Operation(summary = "Query AdsRunningAudit entries (no pagination)")
    public ResponseEntity<List<AdsRunningAuditDto>> query(
            @RequestParam(required = false) String adsOwner,
            @RequestParam(required = false) String createDateBegin,
            @RequestParam(required = false) String createDateEnd,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        List<AdsRunningAudit> results = adsRunningAuditService.query(adsOwner, createDateBegin, createDateEnd, userId);
        List<AdsRunningAuditDto> dtos = results.stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    private AdsRunningAuditDto toDto(AdsRunningAudit a) {
        AdsRunningAuditDto d = new AdsRunningAuditDto();
        d.setId(a.getId());
        d.setBrand(a.getBrand());
        d.setPlatform(a.getPlatform());
        d.setEmail(a.getEmail());
        d.setAdsOwner(a.getAdsOwner());
        d.setCreateDate(a.getCreateDate());
        return d;
    }
}
