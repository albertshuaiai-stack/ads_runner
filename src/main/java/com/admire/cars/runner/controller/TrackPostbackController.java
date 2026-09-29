package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.TrackConversion;
import com.admire.cars.runner.service.TrackConversionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 联盟 S2S 回传端点 / Affiliate S2S postback endpoint.
 * 联盟平台回传参数命名不统一, 这里统一接收全部参数交给服务层按候选键提取.
 * Networks use different parameter names, all params are forwarded to the service for extraction.
 */
@Slf4j
@RestController
@RequestMapping("/api/track")
public class TrackPostbackController {

    private final TrackConversionService trackConversionService;

    public TrackPostbackController(TrackConversionService trackConversionService) {
        this.trackConversionService = trackConversionService;
    }

    // 部分联盟平台用 GET 回传 / some networks post back via GET
    @GetMapping("/postback")
    public ResponseEntity<Map<String, Object>> postbackByGet(HttpServletRequest request) {
        return handle(request);
    }

    @PostMapping("/postback")
    public ResponseEntity<Map<String, Object>> postbackByPost(HttpServletRequest request) {
        return handle(request);
    }

    private ResponseEntity<Map<String, Object>> handle(HttpServletRequest request) {
        Map<String, String> params = flatten(request.getParameterMap());
        try {
            TrackConversion saved = trackConversionService.record(params);
            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("message", "conversion received.");
            body.put("attributed", saved.getAttributed());
            body.put("orderNo", saved.getOrderNo());
            log.info("track postback received, orderNo={} attributed={}", saved.getOrderNo(), saved.getAttributed());
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } catch (IllegalArgumentException e) {
            Map<String, Object> body = new HashMap<>();
            body.put("success", false);
            body.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        }
    }

    private Map<String, String> flatten(Map<String, String[]> source) {
        Map<String, String> flat = new LinkedHashMap<>();
        source.forEach((key, values) -> {
            if (values != null && values.length > 0) {
                flat.put(key, values[0]);
            } else {
                flat.put(key, "");
            }
        });
        return flat;
    }
}
