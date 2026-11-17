package com.admire.cars.runner.controller;

import com.admire.cars.runner.dto.MiddleLinkDto;
import com.admire.cars.runner.entity.MiddleLink;
import com.admire.cars.runner.service.MiddleLinkService;
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
@RequestMapping("/api/middle-links")
public class MiddleLinkController {

    private final MiddleLinkService service;

    public MiddleLinkController(MiddleLinkService service) {
        this.service = service;
    }

    // 批量上传中间链接 / batch upload middle links
    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> batchCreate(@RequestBody BatchCreateRequest body, HttpServletRequest request) {
        try {
            List<MiddleLink> created = service.batchCreate(
                    body.brandName(), body.urls(), body.matchId(), getUserId(request));
            long classified = created.stream().filter(link -> link.getUserName() != null).count();
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "MIDDLE_LINK created successfully");
            resp.put("createdCount", created.size());
            resp.put("classifiedCount", classified);
            resp.put("data", created.stream().map(MiddleLinkDto::fromEntity).toList());
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    @GetMapping
    public ResponseEntity<Page<MiddleLinkDto>> search(
            @RequestParam(required = false) String brandName,
            @RequestParam(required = false) String resolveStatus,
            @RequestParam(required = false) String adsOwner,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        Page<MiddleLink> results = service.search(brandName, resolveStatus, adsOwner, getUserIdOrNull(request),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createDate").and(Sort.by(Sort.Direction.DESC, "id"))));
        List<MiddleLinkDto> content = results.getContent().stream().map(MiddleLinkDto::fromEntity).toList();
        return ResponseEntity.ok(new PageImpl<>(content, results.getPageable(), results.getTotalElements()));
    }

    // 启动限时解析 / start resolving landing pages within a time window
    @PostMapping("/{id}/resolve")
    public ResponseEntity<Map<String, Object>> resolve(@PathVariable Long id,
                                                       @RequestBody(required = false) ResolveRequest body,
                                                       HttpServletRequest request) {
        try {
            MiddleLink started = service.startResolve(
                    id,
                    body != null ? body.targetCount() : null,
                    body != null ? body.windowSeconds() : null,
                    body != null ? body.intervalSeconds() : null,
                    getUserId(request));
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "Resolve started");
            resp.put("data", MiddleLinkDto.fromEntity(started));
            return ResponseEntity.accepted().body(resp);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    // 手动停止解析 / stop resolving manually
    @PostMapping("/{id}/stop")
    public ResponseEntity<Map<String, Object>> stop(@PathVariable Long id, HttpServletRequest request) {
        try {
            MiddleLink stopped = service.stopResolve(id, getUserId(request));
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "Resolve stopped");
            resp.put("data", MiddleLinkDto.fromEntity(stopped));
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
            resp.put("message", "MIDDLE_LINK deleted successfully");
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

    public record BatchCreateRequest(String brandName, List<String> urls, String matchId) {
    }

    public record ResolveRequest(Long targetCount, Long windowSeconds, Long intervalSeconds) {
    }
}
