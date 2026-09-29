package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.TrackClick;
import com.admire.cars.runner.entity.TrackConversion;
import com.admire.cars.runner.repository.TrackClickRepository;
import com.admire.cars.runner.repository.TrackConversionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

/**
 * 联盟回传归因 / Affiliate postback attribution.
 * 用回传里的点击标识匹配 TRACK_CLICK, 补齐 campaign / siteId 等归因字段.
 * Matches the click id against TRACK_CLICK to fill campaign / siteId attribution fields.
 */
@Slf4j
@Service
@Transactional
public class TrackConversionService {

    // 各联盟平台参数名不统一, 按顺序取第一个有值的 / networks use different names, take the first present
    private static final String[] CLICK_ID_KEYS = {"click_id", "clickId", "clickid", "sub_id", "subid", "sub_id1", "u1", "sid", "aff_user_id", "uid"};
    private static final String[] ORDER_NO_KEYS = {"order_no", "orderNo", "order_id", "orderid", "oid", "transaction_id"};
    private static final String[] AMOUNT_KEYS = {"order_amount", "orderAmount", "amount", "sale_amount", "saleAmount", "total"};
    private static final String[] COMMISSION_KEYS = {"commission", "commission_amount", "sale_comm", "saleComm", "payout", "user_commission_amount"};
    private static final String[] STATUS_KEYS = {"status", "order_status", "orderStatus"};
    private static final String[] TIME_KEYS = {"conversion_time", "conversionTime", "order_time", "orderTime", "event_time", "date"};
    private static final String[] PLATFORM_KEYS = {"platform", "network", "affiliate_site"};
    private static final String[] ADVERTISER_KEYS = {"advertiser", "advertiser_name", "merchant", "merchant_name", "shop"};

    private final TrackConversionRepository conversionRepository;
    private final TrackClickRepository clickRepository;

    public TrackConversionService(
            TrackConversionRepository conversionRepository,
            TrackClickRepository clickRepository) {
        this.conversionRepository = conversionRepository;
        this.clickRepository = clickRepository;
    }

    public TrackConversion record(Map<String, String> params) {
        String clickId = firstPresent(params, CLICK_ID_KEYS);
        String orderNo = firstPresent(params, ORDER_NO_KEYS);

        if (orderNo == null && clickId == null) {
            throw new IllegalArgumentException("order_no or click_id is required");
        }

        // 同一订单重复回传时更新, 保证幂等 / idempotent: update when the same order is reported again
        TrackConversion conversion = orderNo == null
                ? new TrackConversion()
                : conversionRepository.findByOrderNo(orderNo).orElse(new TrackConversion());

        conversion.setClickId(clickId);
        conversion.setOrderNo(orderNo);
        conversion.setOrderAmount(parseDecimal(firstPresent(params, AMOUNT_KEYS)));
        conversion.setCommissionAmount(parseDecimal(firstPresent(params, COMMISSION_KEYS)));
        conversion.setStatus(firstPresent(params, STATUS_KEYS));
        conversion.setConversionTime(firstPresent(params, TIME_KEYS));
        conversion.setPlatform(firstPresent(params, PLATFORM_KEYS));
        conversion.setAdvertiser(firstPresent(params, ADVERTISER_KEYS));
        conversion.setRawParams(truncate(buildRaw(params), 2048));
        conversion.setAttributed(TrackConversion.ATTRIBUTED_NO);

        if (clickId != null) {
            clickRepository.findByClickId(clickId).ifPresent(click -> applyAttribution(conversion, click));
        } else {
            log.warn("postback without click id, cannot attribute, orderNo={}", orderNo);
        }

        return conversionRepository.save(conversion);
    }

    // 用点击记录补齐归因字段 / fill attribution fields from the click record
    private void applyAttribution(TrackConversion conversion, TrackClick click) {
        conversion.setSlug(click.getSlug());
        conversion.setCampaignName(click.getCampaignName());
        conversion.setSiteId(click.getSiteId());
        conversion.setAdsOwner(click.getAdsOwner());
        if (conversion.getPlatform() == null) {
            conversion.setPlatform(click.getPlatform());
        }
        if (conversion.getAdvertiser() == null) {
            conversion.setAdvertiser(click.getAdvertiser());
        }
        conversion.setAttributed(TrackConversion.ATTRIBUTED_YES);
    }

    private String firstPresent(Map<String, String> params, String[] keys) {
        for (String key : keys) {
            String value = params.get(key);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
            // 兼容大小写差异 / tolerate case differences
            String lowered = key.toLowerCase(Locale.ROOT);
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (entry.getKey() != null && entry.getKey().toLowerCase(Locale.ROOT).equals(lowered)) {
                    String candidate = entry.getValue();
                    if (candidate != null && !candidate.isBlank()) {
                        return candidate.trim();
                    }
                }
            }
        }
        return null;
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", ""));
        } catch (NumberFormatException e) {
            log.warn("invalid decimal in postback: {}", value);
            return null;
        }
    }

    private String buildRaw(Map<String, String> params) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
