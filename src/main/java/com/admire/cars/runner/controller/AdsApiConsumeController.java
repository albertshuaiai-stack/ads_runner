package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.service.AdsApiConsumeService;
import com.admire.cars.runner.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class AdsApiConsumeController {

    private final UserService userService;
    private final AdsApiConsumeService adsApiConsumeService;

    public AdsApiConsumeController(UserService userService, AdsApiConsumeService adsApiConsumeService) {
        this.userService = userService;
        this.adsApiConsumeService = adsApiConsumeService;
    }

    @GetMapping("/normal/ads")
    public ResponseEntity<Map<String, Object>> consumeNormalAds(
            @RequestParam("campain_name") String campainName,
            @RequestParam(value = "api_key", required = false) String apiKeyParam) {
        return consume("normal", campainName, apiKeyParam);
    }

    @GetMapping("/matrix/ads")
    public ResponseEntity<Map<String, Object>> consumeMatrixAds(
            @RequestParam("campain_name") String campainName,
            @RequestParam(value = "api_key", required = false) String apiKeyParam) {
        try {
            String apiKey = resolveApiKey(apiKeyParam);
            AdsApiConsumeService.MatrixConsumeResult result = adsApiConsumeService.consumeMatrixAds(campainName, apiKey);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("adsType", "matrix");
            response.put("campainName", result.campainName());
            response.put("adsOwner", result.adsOwner());
            response.put("seqNumber", result.seqNumber());
            response.put("fullUrl", result.fullUrl());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    private ResponseEntity<Map<String, Object>> consume(
            String adsType,
            String campainName,
            String apiKeyParam) {
        try {
            if (!StringUtils.hasText(campainName)) {
                throw new IllegalArgumentException("campain_name is required");
            }

            String apiKey = resolveApiKey(apiKeyParam);
            User user = userService.getEnabledUserByApiKey(apiKey);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("adsType", adsType);
            response.put("campainName", campainName.trim());
            response.put("adsOwner", user.getUserPhoneNumber());
            response.put("uniqueUrl", buildUniqueUrl(adsType, campainName));
            response.put("detail", "");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    private String resolveApiKey(String apiKeyParam) {
        if (StringUtils.hasText(apiKeyParam)) {
            return apiKeyParam.trim();
        }
        throw new IllegalArgumentException("api_key is required");
    }

    private String buildUniqueUrl(String adsType, String campainName) {
        String encodedCampainName = UriUtils.encodePathSegment(campainName.trim(), StandardCharsets.UTF_8);
        return "https://ads.local/" + adsType + "/" + encodedCampainName + "/" + UUID.randomUUID();
    }
}
