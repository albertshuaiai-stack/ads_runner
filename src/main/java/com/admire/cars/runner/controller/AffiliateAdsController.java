package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.AffiliateAds;
import com.admire.cars.runner.service.AffiliateAdsService;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/affiliate-ads")
@Tag(name = "Affiliate Ads")
public class AffiliateAdsController {

    private final AffiliateAdsService affiliateAdsService;

    public AffiliateAdsController(
            AffiliateAdsService affiliateAdsService) {
        this.affiliateAdsService = affiliateAdsService;
    }

    @GetMapping
    @Operation(summary = "Query affiliate ads")
    public ResponseEntity<Page<AffiliateAds>> search(
            @RequestParam(required = false) String adsOwner,
            @RequestParam(required = false) String affiliateNetwork,
            @RequestParam(required = false) String siteName,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        Page<AffiliateAds> result = affiliateAdsService.search(
                adsOwner,
                affiliateNetwork,
                siteName,
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
