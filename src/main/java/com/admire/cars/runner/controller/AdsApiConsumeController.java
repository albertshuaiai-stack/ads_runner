package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.AdsNormalPostBack;
import com.admire.cars.runner.service.AdsApiConsumeService;
import com.admire.cars.runner.service.AdsNormalPostBackService;
import com.admire.cars.runner.service.PostBackAttributionService;
import com.admire.cars.runner.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Slf4j
public class AdsApiConsumeController {

    private final UserService userService;

    private final AdsApiConsumeService adsApiConsumeService;

    private final AdsNormalPostBackService adsNormalPostBackService;

    private final PostBackAttributionService postBackAttributionService;

    private final ObjectMapper objectMapper;

    public AdsApiConsumeController(UserService userService, AdsApiConsumeService adsApiConsumeService,
                                   AdsNormalPostBackService adsNormalPostBackService,
                                   PostBackAttributionService postBackAttributionService,
                                   ObjectMapper objectMapper) {
        this.userService = userService;
        this.adsApiConsumeService = adsApiConsumeService;
        this.adsNormalPostBackService = adsNormalPostBackService;
        this.postBackAttributionService = postBackAttributionService;
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
            @RequestParam(value = "click_time", required = false) String clickTime,
            HttpServletRequest request) {
        AdsNormalPostBack adsNormalPostBack = new AdsNormalPostBack();
        adsNormalPostBack.setAdvertiserShopId(advertiserShopId);
        adsNormalPostBack.setAdvertiserShopName(advertiserShopName);
        adsNormalPostBack.setSignId(signId);
        adsNormalPostBack.setOrderNo(orderNo);
        adsNormalPostBack.setOrderTime(orderTime);
        adsNormalPostBack.setOrderAmount(parseBigDecimal(orderAmount,"order_amount"));
        adsNormalPostBack.setUserCommissionAmount(parseBigDecimal(userCommissionAmount,"user_commission_amount"));
        adsNormalPostBack.setStatus(status);
        // 后台 Global Postback 默认把点击ID放在 web_id/web_id2, 这里兼容常见别名
        // the Global Postback page sends the click id as web_id/web_id2; accept the common aliases too
        // 只收 BonusArrive 实际会用的参数名, 其它原始参数由 POSTBACK_RAW_PARAMS 记录
        // only accept names BonusArrive actually sends; everything else is logged by POSTBACK_RAW_PARAMS
        adsNormalPostBack.setSubId(resolveSubId(subId, request, "sub_id", "subid", "web_id"));
        adsNormalPostBack.setSubId2(resolveSubId(subId2, request, "sub_id2", "subid2", "sub2id", "web_id2"));
        adsNormalPostBack.setClickTime(clickTime);
        adsNormalPostBack.setAffiliateSite("BonusArrive");
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
        // 记录联盟真正回传的原始字段, 便于核对 sub_id / click_id 对不对 / log the raw callback fields
        log.info("POSTBACK_RECEIVED site={} orderNo={} status={} shopId={} shopName={} signId={} subId={} subId2={} clickTime={} orderTime={} amount={} commission={}",
                adsNormalPostBack.getAffiliateSite(), adsNormalPostBack.getOrderNo(),
                adsNormalPostBack.getStatus(), adsNormalPostBack.getAdvertiserShopId(),
                adsNormalPostBack.getAdvertiserShopName(), adsNormalPostBack.getSignId(),
                adsNormalPostBack.getSubId(), adsNormalPostBack.getSubId2(),
                adsNormalPostBack.getClickTime(), adsNormalPostBack.getOrderTime(),
                adsNormalPostBack.getOrderAmount(), adsNormalPostBack.getUserCommissionAmount());
        // 打印平台发来的全部原始参数, 参数名或宏值有偏差时一眼可见 / log every raw callback parameter
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest rawRequest = attributes.getRequest();
                Map<String, String> rawParams = new LinkedHashMap<>();
                rawRequest.getParameterMap().forEach((key, values) ->
                        rawParams.put(key, String.join(",", values)));
                log.info("POSTBACK_RAW_PARAMS site={} params={}",
                        adsNormalPostBack.getAffiliateSite(), rawParams);
            }
        } catch (Exception e) {
            log.warn("POSTBACK_RAW_PARAMS_FAILED error={}", e.getMessage());
        }
        try {
            String apiKey = resolveApiKey(apiKeyParam);
            adsNormalPostBackService.create(adsNormalPostBack, apiKey);
            try {
                postBackAttributionService.attribute(adsNormalPostBack.getAdsOwner(), adsNormalPostBack.getOrderNo());
            } catch (Exception e) {
                // 归因失败不能让联盟平台重试回调 / attribution failure must not make the network retry the postback
                log.error("POSTBACK_ATTRIBUTION_FAILED orderNo={} error={}",
                        adsNormalPostBack.getOrderNo(), e.getMessage(), e);
            }
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

    private String resolveSubId(String primary, HttpServletRequest request, String... aliases) {
        if (StringUtils.hasText(primary)) {
            return primary.trim();
        }
        if (request != null) {
            for (String alias : aliases) {
                String value = request.getParameter(alias);
                if (StringUtils.hasText(value)) {
                    return value.trim();
                }
            }
        }
        return null;
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
