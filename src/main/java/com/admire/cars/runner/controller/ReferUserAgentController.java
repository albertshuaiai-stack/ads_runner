package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.ReferUserAgent;
import com.admire.cars.runner.service.ReferUserAgentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/refer-user-agents")
public class ReferUserAgentController {

    private final ReferUserAgentService referUserAgentService;

    public ReferUserAgentController(ReferUserAgentService referUserAgentService) {
        this.referUserAgentService = referUserAgentService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody ReferUserAgent referUserAgent) {
        try {
            ReferUserAgent created = referUserAgentService.create(referUserAgent);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "REFER_USER_AGENT created successfully");
            response.put("id", created.getId());
            response.put("data", created);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReferUserAgent> getById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(referUserAgentService.getById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping
    public ResponseEntity<List<ReferUserAgent>> getAll() {
        return ResponseEntity.ok(referUserAgentService.getAll());
    }

    @GetMapping("/by-device/{device}")
    public ResponseEntity<List<ReferUserAgent>> getByDevice(@PathVariable String device) {
        try {
            return ResponseEntity.ok(referUserAgentService.getByDevice(device));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @GetMapping("/by-device/{device}/user-agents")
    public ResponseEntity<List<String>> getUserAgentListByDevice(@PathVariable String device) {
        try {
            return ResponseEntity.ok(referUserAgentService.getUserAgentListByDevice(device));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @RequestBody ReferUserAgent updateData) {
        try {
            ReferUserAgent updated = referUserAgentService.update(id, updateData);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "REFER_USER_AGENT updated successfully");
            response.put("data", updated);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        try {
            referUserAgentService.delete(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "REFER_USER_AGENT deleted successfully");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }
}
