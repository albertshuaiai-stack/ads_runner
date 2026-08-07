package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.AffiliateTest;
import com.admire.cars.runner.service.AffiliateTestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/affiliate-test")
@Tag(name = "Affiliate Test")
public class AffiliateTestController {

    private final AffiliateTestService affiliateTestService;

    public AffiliateTestController(AffiliateTestService affiliateTestService) {
        this.affiliateTestService = affiliateTestService;
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

    private Long getUserId(HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        if (uid == null) {
            throw new IllegalArgumentException("userId not found in request");
        }
        return (Long) uid;
    }
}
