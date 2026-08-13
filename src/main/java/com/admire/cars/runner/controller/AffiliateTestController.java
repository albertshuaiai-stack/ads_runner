package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.AffiliateTest;
import com.admire.cars.runner.entity.AdsNormalInfo;
import com.admire.cars.runner.service.AffiliateTestService;
import com.admire.cars.runner.service.AdsNormalInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/affiliate-test")
@Tag(name = "Affiliate Test")
public class AffiliateTestController {

    private final AffiliateTestService affiliateTestService;
    private final AdsNormalInfoService adsNormalInfoService;

    public AffiliateTestController(AffiliateTestService affiliateTestService, AdsNormalInfoService adsNormalInfoService) {
        this.affiliateTestService = affiliateTestService;
        this.adsNormalInfoService = adsNormalInfoService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get affiliate test by id")
    public ResponseEntity<AffiliateTest> getById(@PathVariable Long id, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(affiliateTestService.getById(id, getUserId(request)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping
    @Operation(summary = "Query affiliate tests")
    public ResponseEntity<Page<AffiliateTest>> search(
            @RequestParam(required = false) String adsOwner,
            @RequestParam(required = false) String affiliateNetwork,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        Page<AffiliateTest> result = affiliateTestService.search(
                adsOwner,
                affiliateNetwork,
                region,
                status,
                getUserId(request),
                PageRequest.of(
                        safePage,
                        safeSize,
                        Sort.by(Sort.Direction.DESC, "createDate").and(Sort.by(Sort.Direction.DESC, "id"))));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/normal")
    @Operation(summary = "Convert successful affiliate test to normal ads task")
    public ResponseEntity<Map<String, Object>> convertToNormal(
            @RequestParam Long testAdId,
            HttpServletRequest request) {
        try {
            Long userId = getUserId(request);
            AdsNormalInfo normalInfo = affiliateTestService.convertTestToNormal(testAdId, userId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Affiliate test converted to normal ads successfully");
            response.put("id", normalInfo.getId());
            response.put("data", normalInfo);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to convert affiliate test: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
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
