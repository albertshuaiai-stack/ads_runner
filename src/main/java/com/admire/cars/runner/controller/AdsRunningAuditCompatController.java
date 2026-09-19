package com.admire.cars.runner.controller;

import com.admire.cars.runner.dto.AdsRunningAuditDto;
import com.admire.cars.runner.entity.AdsRunningAudit;
import com.admire.cars.runner.service.AdsRunningAuditService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ads-running-audit")
public class AdsRunningAuditCompatController {

    private final AdsRunningAuditService adsRunningAuditService;

    public AdsRunningAuditCompatController(AdsRunningAuditService adsRunningAuditService) {
        this.adsRunningAuditService = adsRunningAuditService;
    }

    @GetMapping("/query")
    @Operation(summary = "Compatibility: Query AdsRunningAudit entries (startDate/endDate)")
    public ResponseEntity<List<AdsRunningAuditDto>> queryCompat(
            @RequestParam(name = "startDate", required = false) String startDate,
            @RequestParam(name = "endDate", required = false) String endDate,
            @RequestParam(name = "adsOwner", required = false) String adsOwner,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        List<AdsRunningAudit> results = adsRunningAuditService.query(adsOwner, startDate, endDate, userId);
        List<AdsRunningAuditDto> dtos = results.stream().map(a -> {
            AdsRunningAuditDto d = new AdsRunningAuditDto();
            d.setId(a.getId());
            d.setBrand(a.getBrand());
            d.setPlatform(a.getPlatform());
            d.setEmail(a.getEmail());
            d.setAdsOwner(a.getAdsOwner());
            d.setCreateDate(a.getCreateDate());
            return d;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }
}
