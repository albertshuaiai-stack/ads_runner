package com.admire.cars.runner.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/secure")
public class SecureController {

    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping(HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", true);
        resp.put("userId", uid);
        resp.put("message", "Authenticated access successful");
        return ResponseEntity.ok(resp);
    }
}
