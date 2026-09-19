package com.admire.cars.runner.controller;

import com.admire.cars.runner.dto.ToolBrandsReviewDto;
import com.admire.cars.runner.entity.ToolBrandsReview;
import com.admire.cars.runner.service.ToolBrandsReviewService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/tool-brands-reviews")
public class ToolBrandsReviewController {

    private final ToolBrandsReviewService service;

    public ToolBrandsReviewController(ToolBrandsReviewService service) {
        this.service = service;
    }

    @GetMapping("/brands")
    public ResponseEntity<java.util.List<String>> getBrands(HttpServletRequest request) {
        java.util.List<String> brands = service.getAllBrands(getUserId(request));
        return ResponseEntity.ok(brands);
    }

    @PostMapping
    public ResponseEntity<Map<String,Object>> create(@RequestBody ToolBrandsReviewDto dto, HttpServletRequest request) {
        try {
            ToolBrandsReview created = service.create(dto.toEntity(), getUserId(request));
            Map<String,Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "TOOL_BRANDS_REVIEW created successfully");
            resp.put("id", created.getId());
            resp.put("data", ToolBrandsReviewDto.fromEntity(created));
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);
        } catch (IllegalArgumentException e) {
            Map<String,Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ToolBrandsReviewDto> getById(@PathVariable Long id, HttpServletRequest request) {
        try {
            ToolBrandsReview e = service.getById(id, getUserId(request));
            return ResponseEntity.ok(ToolBrandsReviewDto.fromEntity(e));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping
    public ResponseEntity<Page<ToolBrandsReviewDto>> search(
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) Long score,
            @RequestParam(required = false) String adsOwner,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        Page<ToolBrandsReview> results = service.search(brand, score, adsOwner, getUserIdOrNull(request), PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createDate").and(Sort.by(Sort.Direction.DESC, "id"))));
        List<ToolBrandsReviewDto> content = results.getContent().stream().map(ToolBrandsReviewDto::fromEntity).toList();
        return ResponseEntity.ok(new PageImpl<>(content, results.getPageable(), results.getTotalElements()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String,Object>> update(@PathVariable Long id, @RequestBody ToolBrandsReviewDto dto, HttpServletRequest request) {
        try {
            ToolBrandsReview updated = service.update(id, dto.toEntity(), getUserId(request));
            Map<String,Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "TOOL_BRANDS_REVIEW updated successfully");
            resp.put("data", ToolBrandsReviewDto.fromEntity(updated));
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            Map<String,Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String,Object>> delete(@PathVariable Long id, HttpServletRequest request) {
        try {
            service.delete(id, getUserId(request));
            Map<String,Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "TOOL_BRANDS_REVIEW deleted successfully");
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            Map<String,Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
        }
    }

    private Long getUserId(HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        if (uid == null) {
            throw new IllegalArgumentException("userId not found in request");
        }
        return (Long) uid;
    }

    private Long getUserIdOrNull(HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        if (uid == null) return null;
        return (Long) uid;
    }
}
