package com.admire.cars.runner.service;

import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 投前/投后共用的关键词规则: 否定意图词、优惠码词、品牌词识别
 * Keyword rules shared by pre-launch evaluation and daily review: negative intent, coupon terms, brand detection
 */
public final class AdsKeywordRules {

    // 不会产生订单的搜索意图, 建议加为否定关键词 / search intents that do not buy, suggested negatives
    public static final List<String> NEGATIVE_INTENT_TERMS = List.of(
            "login", "log in", "sign in", "signin", "account", "customer service", "customer care",
            "phone number", "contact", "support", "track order", "order status", "tracking",
            "return", "returns", "refund", "cancel", "unsubscribe", "job", "jobs", "career", "careers",
            "hiring", "salary", "near me", "store hours", "store locator", "address", "manual",
            "warranty", "recall", "lawsuit", "stock price", "reddit", "wiki", "download", "apk",
            "used", "second hand", "repair", "complaint", "complaints", "scam");

    // 优惠码类词, 多数联盟单独约束 / coupon intent, often restricted by network terms
    public static final List<String> COUPON_TERMS = List.of(
            "coupon", "coupons", "promo", "promo code", "promocode", "discount code", "voucher", "vouchers",
            "code", "codes");

    // 明确的购买意图修饰词 / explicit purchase-intent modifiers
    public static final List<String> PURCHASE_TERMS = List.of(
            "sale", "sales", "discount", "discounts", "deal", "deals", "buy", "shop", "price", "prices", "offer",
            "offers", "cheap", "clearance", "outlet", "black friday", "cyber monday", "free shipping", "order online");

    // 识别品牌所在的二级域名时跳过的后缀 / suffix labels skipped when reading the brand from a host
    private static final Set<String> HOST_SUFFIXES = Set.of(
            "www", "com", "net", "org", "co", "au", "uk", "ca", "us", "de", "fr", "jp", "sg", "nz", "ie", "in",
            "io", "shop", "store", "hk", "tw", "my", "ph", "es", "it", "nl", "eu");

    private static final Map<String, Pattern> NEGATIVE_INTENT_PATTERNS = compileAll(NEGATIVE_INTENT_TERMS);
    private static final Map<String, Pattern> COUPON_PATTERNS = compileAll(COUPON_TERMS);
    private static final Map<String, Pattern> PURCHASE_PATTERNS = compileAll(PURCHASE_TERMS);

    private AdsKeywordRules() {
    }

    public static String findNegativeIntent(String text) {
        return findTerm(text, NEGATIVE_INTENT_PATTERNS);
    }

    public static String findCouponTerm(String text) {
        return findTerm(text, COUPON_PATTERNS);
    }

    public static String findPurchaseTerm(String text) {
        return findTerm(text, PURCHASE_PATTERNS);
    }

    private static String findTerm(String text, Map<String, Pattern> patterns) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String value = text.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Pattern> entry : patterns.entrySet()) {
            if (entry.getValue().matcher(value).find()) {
                return entry.getKey();
            }
        }
        return null;
    }

    // 按词边界匹配, 避免 "code" 命中 "barcode" / word-boundary match so "code" does not hit "barcode"
    private static Map<String, Pattern> compileAll(List<String> terms) {
        Map<String, Pattern> patterns = new LinkedHashMap<>();
        for (String term : terms) {
            patterns.put(term, Pattern.compile("(^|[^\\p{L}\\p{N}])" + Pattern.quote(term) + "($|[^\\p{L}\\p{N}])"));
        }
        return patterns;
    }

    // 品牌名支持逗号/斜杠分隔多个别名, 缺省时取广告系列名和落地页域名 / brand aliases, falls back to campaign and host
    public static List<String> brandTokens(String brandName, String campaignName, String landingUrl) {
        Set<String> tokens = new LinkedHashSet<>();
        if (StringUtils.hasText(brandName)) {
            for (String alias : brandName.split("[,/|;]")) {
                addToken(tokens, alias);
            }
        }
        if (tokens.isEmpty()) {
            addToken(tokens, campaignName);
            addToken(tokens, hostBrand(landingUrl));
        }
        return new ArrayList<>(tokens);
    }

    public static boolean containsBrand(String text, List<String> brandTokens) {
        if (!StringUtils.hasText(text) || brandTokens == null || brandTokens.isEmpty()) {
            return false;
        }
        String compact = compact(text);
        return brandTokens.stream().anyMatch(compact::contains);
    }

    public static String hostBrand(String url) {
        String host = hostOf(url);
        if (host == null) {
            return null;
        }
        String[] labels = host.split("\\.");
        for (int i = labels.length - 1; i >= 0; i--) {
            if (!HOST_SUFFIXES.contains(labels[i])) {
                return labels[i];
            }
        }
        return null;
    }

    public static String hostOf(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        try {
            String value = url.trim();
            if (!value.contains("://")) {
                value = "https://" + value;
            }
            String host = URI.create(value).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void addToken(Set<String> tokens, String value) {
        String token = compact(value);
        if (token.length() >= 3) {
            tokens.add(token);
        }
    }

    private static String compact(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
