package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.ToolOutcome;
import com.admire.cars.runner.service.ToolOutcomeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/tool-outcomes")
public class ToolOutcomeController {

    private final ToolOutcomeService toolOutcomeService;

    public ToolOutcomeController(ToolOutcomeService toolOutcomeService) {
        this.toolOutcomeService = toolOutcomeService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody com.admire.cars.runner.dto.ToolOutcomeDto dto, HttpServletRequest request) {
        try {
            com.admire.cars.runner.entity.ToolOutcome created = toolOutcomeService.create(dto.toEntity(), getUserId(request));
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "TOOL_OUTCOME created successfully");
            response.put("id", created.getId());
            response.put("data", com.admire.cars.runner.dto.ToolOutcomeDto.fromEntity(created));
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<com.admire.cars.runner.dto.ToolOutcomeDto> getById(@PathVariable Long id, HttpServletRequest request) {
        try {
            com.admire.cars.runner.entity.ToolOutcome e = toolOutcomeService.getById(id, getUserId(request));
            return ResponseEntity.ok(com.admire.cars.runner.dto.ToolOutcomeDto.fromEntity(e));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping
    public ResponseEntity<org.springframework.data.domain.Page<com.admire.cars.runner.dto.ToolOutcomeDto>> search(
            @RequestParam(required = false) String outcomeType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate payDateBegin,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate payDateEnd,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        org.springframework.data.domain.Page<com.admire.cars.runner.entity.ToolOutcome> toolOutcomes = toolOutcomeService.search(
                outcomeType,
                payDateBegin,
                payDateEnd,
                getUserId(request),
                PageRequest.of(
                        safePage,
                        safeSize,
                        Sort.by(Sort.Direction.DESC, "createDate").and(Sort.by(Sort.Direction.DESC, "id"))));
        java.util.List<com.admire.cars.runner.dto.ToolOutcomeDto> content = toolOutcomes.getContent().stream()
                .map(com.admire.cars.runner.dto.ToolOutcomeDto::fromEntity)
                .toList();
        return ResponseEntity.ok(new org.springframework.data.domain.PageImpl<>(content, toolOutcomes.getPageable(), toolOutcomes.getTotalElements()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable Long id,
            @RequestBody com.admire.cars.runner.dto.ToolOutcomeDto dto,
            HttpServletRequest request) {
        try {
            com.admire.cars.runner.entity.ToolOutcome updated = toolOutcomeService.update(id, dto.toEntity(), getUserId(request));
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "TOOL_OUTCOME updated successfully");
            response.put("data", com.admire.cars.runner.dto.ToolOutcomeDto.fromEntity(updated));
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
            toolOutcomeService.delete(id, getUserId(request));
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "TOOL_OUTCOME deleted successfully");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    private Long getUserId(HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        if (uid == null) {
            throw new IllegalArgumentException("userId not found in request");
        }
        return (Long) uid;
    }
}
