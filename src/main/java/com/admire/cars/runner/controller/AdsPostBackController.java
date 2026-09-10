package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.AdsNormalPostBack;
import com.admire.cars.runner.service.AdsNormalPostBackService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/postback")
public class AdsPostBackController {

    private final AdsNormalPostBackService adsNormalPostBackService;

    public AdsPostBackController(AdsNormalPostBackService adsNormalPostBackService) {
        this.adsNormalPostBackService = adsNormalPostBackService;
    }

    @GetMapping
    public ResponseEntity<Page<AdsNormalPostBack>> search(
            @RequestParam(required = false) String adsOwner,
            @RequestParam(required = false) String affiliateSite,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        try {
            Page<AdsNormalPostBack> postBacks = adsNormalPostBackService.search(
                    adsOwner,
                    affiliateSite,
                    orderNo,
                    status,
                    getUserIdOrNull(request),
                    PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id")));
            return ResponseEntity.ok(postBacks);
        } catch (IllegalArgumentException ex) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private Long getUserIdOrNull(HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        if (uid == null) {
            return null;
        }
        return (Long) uid;
    }

}
