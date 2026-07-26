package com.admire.cars.runner.controller;

import com.admire.cars.runner.service.AdsApiConsumeService;
import com.admire.cars.runner.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AdsApiConsumeController {

    private final UserService userService;
    private final AdsApiConsumeService adsApiConsumeService;

    public AdsApiConsumeController(UserService userService, AdsApiConsumeService adsApiConsumeService) {
        this.userService = userService;
        this.adsApiConsumeService = adsApiConsumeService;
    }

    /**
     * Normal Ads Shift link
     * @param campaignName
     * @param apiKeyParam
     * @return
     */
    @GetMapping(value ="/normal/ads", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> consumeNormalAds(
            @RequestParam(value = "campain_name", required = false) String campaignName,
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
            @RequestParam(value = "campain_name", required = false) String campaignName,
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

    private String resolveApiKey(String apiKeyParam) {
        if (StringUtils.hasText(apiKeyParam)) {
            return apiKeyParam.trim();
        }
        throw new IllegalArgumentException("api_key is required");
    }

}
