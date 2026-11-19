package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.service.LandingImportService;
import com.admire.cars.runner.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 落地页采集与导入 / landing collector and import
 * /api/collector/** 给本地脚本用, 不走 JWT, 由 api_key 自校验(已加白名单)
 * /api/collector/** is for local scripts: no JWT, authenticated by api_key here (whitelisted)
 * /api/landing-import 给管理后台 Import from Folder 用, 走 JWT
 * /api/landing-import is for the admin Import from Folder button and uses JWT
 */
@RestController
public class LandingCollectorController {

    @Data
    public static class ImportRequest {
        private String adsType;
        private Long displayNumber;
        private List<LandingImportService.Entry> entries;
    }

    private final LandingImportService landingImportService;
    private final UserService userService;

    public LandingCollectorController(LandingImportService landingImportService, UserService userService) {
        this.landingImportService = landingImportService;
        this.userService = userService;
    }

    @GetMapping("/api/collector/accounts")
    public ResponseEntity<?> accounts(@RequestParam(value = "api_key", required = false) String apiKey,
                                      @RequestParam(value = "userNames", required = false) String userNames) {
        try {
            User user = userByApiKey(apiKey);
            List<String> names = StringUtils.hasText(userNames) ? Arrays.asList(userNames.split(",")) : List.of();
            return ResponseEntity.ok(landingImportService.collectorAccounts(user.getId(), names));
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    @PostMapping("/api/collector/landing-import")
    public ResponseEntity<?> importByApiKey(@RequestParam(value = "api_key", required = false) String apiKey,
                                            @RequestBody ImportRequest body) {
        try {
            User user = userByApiKey(apiKey);
            return ResponseEntity.ok(landingImportService.importEntries(
                    user.getId(), body.getAdsType(), body.getDisplayNumber(), body.getEntries()));
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    @PostMapping("/api/landing-import")
    public ResponseEntity<?> importByToken(@RequestBody ImportRequest body, HttpServletRequest request) {
        try {
            Object uid = request.getAttribute("userId");
            if (uid == null) {
                throw new IllegalArgumentException("Unauthorized");
            }
            return ResponseEntity.ok(landingImportService.importEntries(
                    (Long) uid, body.getAdsType(), body.getDisplayNumber(), body.getEntries()));
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    private User userByApiKey(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalArgumentException("api_key is required");
        }
        return userService.getEnabledUserByApiKey(apiKey.trim());
    }

    private ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
