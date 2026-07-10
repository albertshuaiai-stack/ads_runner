package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.AdsRole;
import com.admire.cars.runner.service.AdsRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/roles")
public class AdsRoleController {

    @Autowired
    private AdsRoleService adsRoleService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody AdsRole adsRole) {
        try {
            AdsRole created = adsRoleService.create(adsRole);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "ADS_ROLE created successfully");
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
    public ResponseEntity<AdsRole> getById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(adsRoleService.getById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping
    public ResponseEntity<List<AdsRole>> getAll() {
        return ResponseEntity.ok(adsRoleService.getAll());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @RequestBody AdsRole updateData) {
        try {
            AdsRole updated = adsRoleService.update(id, updateData);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "ADS_ROLE updated successfully");
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
            adsRoleService.delete(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "ADS_ROLE deleted successfully");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }
}
