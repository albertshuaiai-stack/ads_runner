package com.admire.cars.runner.controller;

import com.admire.cars.runner.dto.GmailPlatformLinkDto;
import com.admire.cars.runner.dto.GmailPlatformStatsResponse;
import com.admire.cars.runner.entity.GmailPlatformLink;
import com.admire.cars.runner.service.GmailPlatformLinkService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gmail-platform-links")
public class GmailPlatformLinkController {

    private final GmailPlatformLinkService service;

    public GmailPlatformLinkController(GmailPlatformLinkService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody GmailPlatformLinkDto dto, HttpServletRequest request) {
        try {
            GmailPlatformLink created = service.create(dto.toEntity(), getUserId(request));
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "GMAIL_PLATFORM_LINK created successfully");
            resp.put("id", created.getId());
            resp.put("data", GmailPlatformLinkDto.fromEntity(created));
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<GmailPlatformLinkDto> getById(@PathVariable Long id, HttpServletRequest request) {
        try {
            GmailPlatformLink e = service.getById(id, getUserId(request));
            return ResponseEntity.ok(GmailPlatformLinkDto.fromEntity(e));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping
    public ResponseEntity<Page<GmailPlatformLinkDto>> search(
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String platformName,
            @RequestParam(required = false) String adsOwner,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        Page<GmailPlatformLink> results = service.search(userName, platformName, adsOwner, getUserIdOrNull(request),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createDate").and(Sort.by(Sort.Direction.DESC, "id"))));
        List<GmailPlatformLinkDto> content = results.getContent().stream().map(GmailPlatformLinkDto::fromEntity).toList();
        return ResponseEntity.ok(new PageImpl<>(content, results.getPageable(), results.getTotalElements()));
    }

    // 按链接匹配归类规则 / match a classify rule by url
    @GetMapping("/match")
    public ResponseEntity<GmailPlatformLinkDto> match(@RequestParam String url, HttpServletRequest request) {
        GmailPlatformLink matched = service.matchByUrl(url, getUserId(request));
        return ResponseEntity.ok(GmailPlatformLinkDto.fromEntity(matched));
    }

    // Gmail -> 返利平台 -> 商家 统计 + 商家评级 / gmail -> platform -> merchant stats + merchant ratings
    @GetMapping("/stats")
    public ResponseEntity<GmailPlatformStatsResponse> stats(HttpServletRequest request) {
        return ResponseEntity.ok(service.getStats(getUserId(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @RequestBody GmailPlatformLinkDto dto, HttpServletRequest request) {
        try {
            GmailPlatformLink updated = service.update(id, dto.toEntity(), getUserId(request));
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "GMAIL_PLATFORM_LINK updated successfully");
            resp.put("data", GmailPlatformLinkDto.fromEntity(updated));
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id, HttpServletRequest request) {
        try {
            service.delete(id, getUserId(request));
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "GMAIL_PLATFORM_LINK deleted successfully");
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    private ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", false);
        resp.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
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
