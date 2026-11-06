package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.AdsNormalPostBack;
import com.admire.cars.runner.service.AdsApiConsumeService;
import com.admire.cars.runner.service.AdsNormalPostBackService;
import com.admire.cars.runner.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Slf4j
public class AdsApiConsumeController {

    private final UserService userService;

    private final AdsApiConsumeService adsApiConsumeService;

    private final AdsNormalPostBackService adsNormalPostBackService;

    private final ObjectMapper objectMapper;

    public AdsApiConsumeController(UserService userService, AdsApiConsumeService adsApiConsumeService, AdsNormalPostBackService adsNormalPostBackService, ObjectMapper objectMapper) {
        this.userService = userService;
        this.adsApiConsumeService = adsApiConsumeService;
        this.adsNormalPostBackService = adsNormalPostBackService;
        this.objectMapper = objectMapper;
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
            log.info("consumeNormalAds campaignName: {}", campaignName);
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
            log.info("consumeMatrixAds campaignName: {}", campaignName);
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
     * Post back for Bonus Arrive
     * @param apiKeyParam
     * @param advertiserShopId
     * @param advertiserShopName
     * @param signId
     * @param orderNo
     * @param orderTime
     * @param orderAmount
     * @param userCommissionAmount
     * @param status
     * @param subId
     * @param subId2
     * @param clickTime
     * @return
     */
    @PostMapping(value = "/postback")
    public ResponseEntity<Map<String, Object>> createByJson(
            @RequestParam(value = "api_key", required = false) String apiKeyParam,
            @RequestParam(value = "advertiser_shop_id", required = false) String advertiserShopId,
            @RequestParam(value = "advertiser_shop_name", required = false) String advertiserShopName,
            @RequestParam(value = "sign_id", required = false) String signId,
            @RequestParam(value = "order_no", required = false) String orderNo,
            @RequestParam(value = "order_time", required = false) String orderTime,
            @RequestParam(value = "order_amount", required = false) String orderAmount,
            @RequestParam(value = "user_commission_amount", required = false) String userCommissionAmount,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "sub_id", required = false) String subId,
            @RequestParam(value = "sub_id2", required = false) String subId2,
            @RequestParam(value = "click_time", required = false) String clickTime) {
        AdsNormalPostBack adsNormalPostBack = new AdsNormalPostBack();
        adsNormalPostBack.setAdvertiserShopId(advertiserShopId);
        adsNormalPostBack.setAdvertiserShopName(advertiserShopName);
        adsNormalPostBack.setSignId(signId);
        adsNormalPostBack.setOrderNo(orderNo);
        adsNormalPostBack.setOrderTime(orderTime);
        adsNormalPostBack.setOrderAmount(parseBigDecimal(orderAmount,"order_amount"));
        adsNormalPostBack.setUserCommissionAmount(parseBigDecimal(userCommissionAmount,"user_commission_amount"));
        adsNormalPostBack.setStatus(status);
        adsNormalPostBack.setSubId(subId);
        adsNormalPostBack.setSubId2(subId2);
        adsNormalPostBack.setClickTime(clickTime);
        adsNormalPostBack.setAffiliateSite("BonusArrive");
        log.info("postback with BonusArrive:{}", adsNormalPostBack);
        return createPostBack(apiKeyParam, adsNormalPostBack);
    }


    /**
     * Post back for Partnerboost
     * @param apiKeyParam
     * @param channelId
     * @param clickRef
     * @param commRate
     * @param mcid
     * @param merchantName
     * @param orderId
     * @param orderTime
     * @param orderUnit
     * @param prodId
     * @param saleAmount
     * @param saleComm
     * @param status
     * @param uid
     * @param uid2
     * @return
     */
    @PostMapping(value = "/pb/postback")
    public ResponseEntity<Map<String, Object>> createByJson(
            @RequestParam(value = "api_key", required = false) String apiKeyParam,
            @RequestParam(value = "channel_id", required = false) String channelId,
            @RequestParam(value = "click_ref", required = false) String clickRef,
            @RequestParam(value = "comm_rate", required = false) String commRate,
            @RequestParam(value = "mcid", required = false) String mcid,
            @RequestParam(value = "merchant_name", required = false) String merchantName,
            @RequestParam(value = "order_id", required = false) String orderId,
            @RequestParam(value = "order_time", required = false) String orderTime,
            @RequestParam(value = "order_unit", required = false) String orderUnit,
            @RequestParam(value = "prod_id", required = false) String prodId,
            @RequestParam(value = "sale_amount", required = false) String saleAmount,
            @RequestParam(value = "sale_comm", required = false) String saleComm,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "uid", required = false) String uid,
            @RequestParam(value = "uid2", required = false) String uid2) {

        AdsNormalPostBack adsNormalPostBack = new AdsNormalPostBack();
        adsNormalPostBack.setAdvertiserShopId(mcid);
        adsNormalPostBack.setAdvertiserShopName(merchantName);
        adsNormalPostBack.setSignId(channelId);
        adsNormalPostBack.setOrderNo(orderId);
        adsNormalPostBack.setOrderTime(orderTime);
        adsNormalPostBack.setOrderAmount(parseBigDecimal(saleAmount,"sale_amount"));
        adsNormalPostBack.setUserCommissionAmount(parseBigDecimal(saleComm,"sale_comm"));
        adsNormalPostBack.setStatus(status);
        adsNormalPostBack.setSubId(uid);
        adsNormalPostBack.setSubId2(uid2);
        adsNormalPostBack.setAffiliateSite("Partnerboost");
        log.info("postback with Partner boost:{}", adsNormalPostBack);
        return createPostBack(apiKeyParam, adsNormalPostBack);
    }

    /**
     * Post back for Yeahpromos
     * @param apiKeyParam
     * @param advertId
     * @param id
     * @param saleComm
     * @param amount
     * @param sku
     * @param orderTime
     * @param orderId
     * @param status
     * @param uid
     * @param uid2
     * @return
     */
    @PostMapping(value = "/yp/postback")
    public ResponseEntity<Map<String, Object>> createByJson(
            @RequestParam(value = "api_key", required = false) String apiKeyParam,
            @RequestParam(value = "Advert_ID", required = false) String advertId,
            @RequestParam(value = "ID", required = false) String id,
            @RequestParam(value = "Sale_Commission", required = false) String saleComm,
            @RequestParam(value = "Amount", required = false) String amount,
            @RequestParam(value = "sku", required = false) String sku,
            @RequestParam(value = "Creation_Date", required = false) String orderTime,
            @RequestParam(value = "Order_ID", required = false) String orderId,
            @RequestParam(value = "Status", required = false) String status,
            @RequestParam(value = "Tag_1", required = false) String uid,
            @RequestParam(value = "Tag_2", required = false) String uid2) {

        AdsNormalPostBack adsNormalPostBack = new AdsNormalPostBack();
        adsNormalPostBack.setAdvertiserShopId(advertId);
        adsNormalPostBack.setAdvertiserShopName(sku);
        adsNormalPostBack.setSignId(id);
        adsNormalPostBack.setOrderNo(orderId);
        adsNormalPostBack.setOrderTime(orderTime);
        adsNormalPostBack.setOrderAmount(parseBigDecimal(amount,"Amount"));
        adsNormalPostBack.setUserCommissionAmount(parseBigDecimal(saleComm,"Sale_Commission"));
        adsNormalPostBack.setStatus(status);
        adsNormalPostBack.setSubId(uid);
        adsNormalPostBack.setSubId2(uid2);
        adsNormalPostBack.setAffiliateSite("Yeahpromos");
        log.info("postback with Yeahpromos:{}", adsNormalPostBack);
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



    private BigDecimal parseBigDecimal(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " must be a valid decimal number");
        }
    }

}
