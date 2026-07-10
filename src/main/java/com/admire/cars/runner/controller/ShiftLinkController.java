package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.ShiftLink;
import com.admire.cars.runner.service.ShiftLinkService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shift-links")
public class ShiftLinkController {

    private final ShiftLinkService shiftLinkService;

    public ShiftLinkController(ShiftLinkService shiftLinkService) {
        this.shiftLinkService = shiftLinkService;
    }

    private Long getUserId(HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        if (uid == null) {
            throw new IllegalArgumentException("userId not found in request");
        }
        return (Long) uid;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createShiftLink(@RequestBody ShiftLink shiftLink, HttpServletRequest request) {
        try {
            Long userId = getUserId(request);
            ShiftLink created = shiftLinkService.createShiftLink(shiftLink, userId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "SHIFT_LINK created successfully");
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
    public ResponseEntity<ShiftLink> getShiftLink(@PathVariable Long id) {
        try {
            ShiftLink shiftLink = shiftLinkService.getShiftLinkById(id);
            return ResponseEntity.ok(shiftLink);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping
    public ResponseEntity<Page<ShiftLink>> searchShiftLinks(
            @RequestParam(required = false) String platformName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String adsOwner,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        Page<ShiftLink> links = shiftLinkService.searchShiftLinks(
                platformName,
                status,
                adsOwner,
                userId,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id")));
        return ResponseEntity.ok(links);
    }

    @GetMapping("/all")
    public ResponseEntity<List<ShiftLink>> getAllShiftLinks(HttpServletRequest request) {
        Long userId = getUserId(request);
        List<ShiftLink> links = shiftLinkService.getAllShiftLinks(userId);
        return ResponseEntity.ok(links);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateShiftLink(@PathVariable Long id, @RequestBody ShiftLink updateData, HttpServletRequest request) {
        try {
            Long userId = getUserId(request);
            ShiftLink updated = shiftLinkService.updateShiftLink(id, updateData, userId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "SHIFT_LINK updated successfully");
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
    public ResponseEntity<Map<String, Object>> deleteShiftLink(@PathVariable Long id, HttpServletRequest request) {
        try {
            Long userId = getUserId(request);
            shiftLinkService.deleteShiftLink(id, userId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "SHIFT_LINK deleted successfully");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @PostMapping("/{id}/increment-display")
    public ResponseEntity<Map<String, Object>> incrementDisplayTimes(@PathVariable Long id, HttpServletRequest request) {
        try {
            Long userId = getUserId(request);
            shiftLinkService.incrementDisplayTimes(id);
            ShiftLink updated = shiftLinkService.getShiftLinkById(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("displayTimes", updated.getDisplayTimes());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @PostMapping(value = "/bulk-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> bulkUploadByExcel(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        try {
            Long userId = getUserId(request);
            ShiftLinkService.BulkUploadResult result = shiftLinkService.bulkUploadByExcel(file, userId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "SHIFT_LINK bulk upload completed");
            response.put("rowCount", result.rowCount());
            response.put("insertedCount", result.insertedCount());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @GetMapping(value = "/template", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<ByteArrayResource> downloadTemplate() {
        byte[] content = shiftLinkService.createTemplateWorkbook();
        ByteArrayResource resource = new ByteArrayResource(content);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=shift_link_template.xlsx")
                .body(resource);
    }
}
