package com.admire.cars.runner.controller;

import com.admire.cars.runner.dto.ToolCloudPhoneDto;
import com.admire.cars.runner.entity.ToolCloudPhone;
import com.admire.cars.runner.service.ToolCloudPhoneService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tool-cloud-phones")
public class ToolCloudPhoneController {

    private final ToolCloudPhoneService service;

    public ToolCloudPhoneController(ToolCloudPhoneService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody ToolCloudPhoneDto dto, HttpServletRequest request) {
        try {
            ToolCloudPhone created = service.create(dto.toEntity(), getUserId(request));
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "TOOL_CLOUD_PHONE created successfully");
            resp.put("id", created.getId());
            resp.put("data", ToolCloudPhoneDto.fromEntity(created));
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);
        } catch (IllegalArgumentException e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ToolCloudPhoneDto> getById(@PathVariable Long id, HttpServletRequest request) {
        try {
            ToolCloudPhone e = service.getById(id, getUserId(request));
            return ResponseEntity.ok(ToolCloudPhoneDto.fromEntity(e));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping
    public ResponseEntity<Page<ToolCloudPhoneDto>> search(
            @RequestParam(required = false) String countryCd,
            @RequestParam(required = false) String adsOwner,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        Page<ToolCloudPhone> result = service.search(countryCd, adsOwner, getUserId(request), PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createDate")));
        List<ToolCloudPhoneDto> content = result.getContent().stream().map(ToolCloudPhoneDto::fromEntity).toList();
        return ResponseEntity.ok(new org.springframework.data.domain.PageImpl<>(content, result.getPageable(), result.getTotalElements()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @RequestBody ToolCloudPhoneDto dto, HttpServletRequest request) {
        try {
            ToolCloudPhone updated = service.update(id, dto.toEntity(), getUserId(request));
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "TOOL_CLOUD_PHONE updated successfully");
            resp.put("data", ToolCloudPhoneDto.fromEntity(updated));
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id, HttpServletRequest request) {
        try {
            service.delete(id, getUserId(request));
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "TOOL_CLOUD_PHONE deleted successfully");
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resp);
        }
    }

    @GetMapping("/phones")
    public ResponseEntity<List<String>> phones(HttpServletRequest request) {
        List<String> phones = service.findAllPhoneNumbers(getUserId(request));
        return ResponseEntity.ok(phones);
    }

    private Long getUserId(HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        if (uid == null) throw new IllegalArgumentException("userId not found in request");
        return (Long) uid;
    }
}
