package com.admire.cars.runner.controller;

import com.admire.cars.runner.dto.ToolIpDto;
import com.admire.cars.runner.entity.ToolIp;
import com.admire.cars.runner.service.ToolIpService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tool-ips")
public class ToolIpController {

    private final ToolIpService toolIpService;

    public ToolIpController(ToolIpService toolIpService) {
        this.toolIpService = toolIpService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody ToolIpDto dto, HttpServletRequest request) {
        try {
            ToolIp created = toolIpService.create(dto.toEntity(), getUserId(request));
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "TOOL_IP created successfully");
            response.put("id", created.getId());
            response.put("data", ToolIpDto.fromEntity(created));
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ToolIpDto> getById(@PathVariable Long id, HttpServletRequest request) {
        try {
            ToolIp e = toolIpService.getById(id, getUserId(request));
            return ResponseEntity.ok(ToolIpDto.fromEntity(e));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping
    public ResponseEntity<Page<ToolIp>> search(
            @RequestParam(required = false) String ipString,
            @RequestParam(required = false) String adsOwner,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        Page<ToolIp> results = toolIpService.search(
                ipString,
                adsOwner,
                getUserId(request),
                PageRequest.of(
                        safePage,
                        safeSize,
                        Sort.by(Sort.Direction.DESC, "createDate").and(Sort.by(Sort.Direction.DESC, "id"))));
        return ResponseEntity.ok(results);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @RequestBody ToolIpDto dto, HttpServletRequest request) {
        try {
            ToolIp updated = toolIpService.update(id, dto.toEntity(), getUserId(request));
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "TOOL_IP updated successfully");
            response.put("data", ToolIpDto.fromEntity(updated));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id, HttpServletRequest request) {
        try {
            toolIpService.delete(id, getUserId(request));
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "TOOL_IP deleted successfully");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @GetMapping("/ips")
    public ResponseEntity<List<String>> listIpStrings(HttpServletRequest request) {
        List<String> ips = toolIpService.findAllIpStringsForUser(getUserId(request));
        return ResponseEntity.ok(ips);
    }

    private Long getUserId(HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        if (uid == null) {
            throw new IllegalArgumentException("userId not found in request");
        }
        return (Long) uid;
    }
}
