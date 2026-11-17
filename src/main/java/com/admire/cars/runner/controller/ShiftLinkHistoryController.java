package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.ShiftLinkHistory;
import com.admire.cars.runner.service.ShiftLinkHistoryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 历史投放数据录入接口 / history data entry endpoints
@RestController
@RequestMapping("/api/shift-link-history")
public class ShiftLinkHistoryController {

    private final ShiftLinkHistoryService service;

    public ShiftLinkHistoryController(ShiftLinkHistoryService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<ShiftLinkHistory>> list(HttpServletRequest request) {
        return ResponseEntity.ok(service.list(getUserId(request)));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody ShiftLinkHistory body, HttpServletRequest request) {
        try {
            ShiftLinkHistory saved = service.save(body, getUserId(request));
            return ok("History saved", saved, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importText(@RequestBody ImportRequest body, HttpServletRequest request) {
        try {
            ShiftLinkHistoryService.ImportResult result = service.importText(
                    body == null ? null : body.text(), getUserId(request));
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("createdCount", result.createdCount());
            resp.put("updatedCount", result.updatedCount());
            resp.put("errors", result.errors());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @RequestBody ShiftLinkHistory body,
                                                      HttpServletRequest request) {
        try {
            ShiftLinkHistory saved = service.update(id, body, getUserId(request));
            return ok("History updated", saved, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id, HttpServletRequest request) {
        try {
            service.delete(id, getUserId(request));
            return ok("History deleted", null, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    private ResponseEntity<Map<String, Object>> ok(String message, Object data, HttpStatus status) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", message);
        resp.put("data", data);
        return ResponseEntity.status(status).body(resp);
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

    public record ImportRequest(String text) {
    }
}
