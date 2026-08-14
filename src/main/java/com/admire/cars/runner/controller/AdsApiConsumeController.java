package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.AdsNormalPostBack;
import com.admire.cars.runner.service.AdsApiConsumeService;
import com.admire.cars.runner.service.AdsNormalPostBackService;
import com.admire.cars.runner.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AdsApiConsumeController {

    private final UserService userService;

    private final AdsApiConsumeService adsApiConsumeService;

    private final AdsNormalPostBackService adsNormalPostBackService;

    public AdsApiConsumeController(UserService userService, AdsApiConsumeService adsApiConsumeService, AdsNormalPostBackService adsNormalPostBackService) {
        this.userService = userService;
        this.adsApiConsumeService = adsApiConsumeService;
        this.adsNormalPostBackService = adsNormalPostBackService;
    }

    /**
     * Normal Ads Shift link
     * @param campaignName
     * @param apiKeyParam
     * @return
     */
    @GetMapping(value ="/normal/ads", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> consumeNormalAds(
            @RequestParam(value = "campaign_name", required = false) String campaignName,
            @RequestParam(value = "api_key", required = false) String apiKeyParam) {
        try {
            String apiKey = resolveApiKey(apiKeyParam);
            String result = adsApiConsumeService.consumeNormalAds(campaignName, apiKey);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(e.getMessage());
        }
    }

    /**
     * Matrix Ads Shift link
     * @param campaignName
     * @param apiKeyParam
     * @return
     */
    @GetMapping(value = "/matrix/ads", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> consumeMatrixAds(
            @RequestParam(value = "campaign_name", required = false) String campaignName,
            @RequestParam(value = "api_key", required = false) String apiKeyParam) {
        try {
            String apiKey = resolveApiKey(apiKeyParam);
            String result = adsApiConsumeService.consumeMatrixAds(campaignName, apiKey);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(e.getMessage());
        }
    }


    /**
     * Post back
     * @param apiKeyParam
     * @param adsNormalPostBack
     * @return
     */
    @PostMapping(value = "/postback", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> create(
            @RequestParam(value = "api_key", required = false) String apiKeyParam,
            @RequestBody AdsNormalPostBack adsNormalPostBack) {
        return createPostBack(apiKeyParam, adsNormalPostBack);
    }

    @PostMapping(value = "/postback", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> createByForm(
            @RequestParam(value = "api_key", required = false) String apiKeyParam,
            @ModelAttribute AdsNormalPostBack adsNormalPostBack) {
        return createPostBack(apiKeyParam, adsNormalPostBack);
    }

    private ResponseEntity<Map<String, Object>> createPostBack(String apiKeyParam, AdsNormalPostBack adsNormalPostBack) {
        try {
            String apiKey = resolveApiKey(apiKeyParam);
            adsNormalPostBackService.create(adsNormalPostBack, apiKey);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "post received.");
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
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

}
