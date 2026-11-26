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
            "used", "second hand", "repair", "complaint", "complaints", "scam",
            // 售后/设置类 / setup and troubleshooting
            "how to", "set up", "setup", "setting up", "not working", "troubleshooting", "unboxing",
            "instructions", "size chart",
            // 去第三方零售商购买, 不记佣金 / buying at a third-party retailer earns no commission
            "where to buy", "amazon", "walmart", "costco", "target", "home depot", "lowes", "lowe's",
            "best buy", "ebay",
            // 投资者/公司信息 / investor and company info
            "valuation", "net worth");

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

    // 广告系列名/商家名末尾的非品牌词, 如 MelissaAffiliatePartners、CastleryUS、VevorManyGEOs
    // trailing non-brand words in campaign / shop names, e.g. MelissaAffiliatePartners, CastleryUS, VevorManyGEOs
    private static final Set<String> NAME_SUFFIX_WORDS = Set.of(
            "affiliate", "affiliates", "partner", "partners", "program", "programs", "commission", "network",
            "many", "geo", "geos", "global", "worldwide", "international", "intl", "official", "store", "shop",
            "online", "onelink", "app", "web", "inc", "llc", "ltd", "co", "corp", "company",
            "north", "america", "us", "usa", "uk", "ca", "au", "eu", "de", "fr", "nz", "usca");

    // 驼峰/数字/符号处拆词 / split on camel case, letter-digit boundaries and symbols
    private static final Pattern NAME_WORD_SPLIT = Pattern.compile(
            "(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|(?<=[0-9])(?=[A-Za-z])|[^A-Za-z0-9]+");

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

    // 品牌名支持逗号/斜杠分隔多个别名, 缺省时取广告系列名、商家名和落地页域名
    // brand aliases; falls back to the campaign name, advertiser shop names and the landing host
    public static List<String> brandTokens(String brandName, String campaignName, String landingUrl,
                                           String... shopNames) {
        Set<String> tokens = new LinkedHashSet<>();
        if (StringUtils.hasText(brandName)) {
            for (String alias : brandName.split("[,/|;]")) {
                addToken(tokens, alias);
            }
        }
        if (tokens.isEmpty()) {
            addToken(tokens, campaignName);
            addToken(tokens, stripNameSuffix(campaignName));
            if (shopNames != null) {
                for (String shopName : shopNames) {
                    addToken(tokens, stripNameSuffix(shopName));
                }
            }
            addToken(tokens, hostBrand(landingUrl));
        }
        return new ArrayList<>(tokens);
    }

    // 去掉末尾的联盟/地区后缀词, 至少保留一个词 / drop trailing affiliate / region words, keep at least one word
    static String stripNameSuffix(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        // GEOs -> GEOS: 缩写的小写复数 s 不单独拆词 / keep the plural s of an acronym in the same word
        String cleaned = name.replaceAll("\\[[^\\]]*\\]|\\([^)]*\\)", " ")
                .replaceAll("([A-Z]{2,})s(?![a-z])", "$1S").trim();
        List<String> words = new ArrayList<>();
        for (String word : NAME_WORD_SPLIT.split(cleaned)) {
            if (!word.isEmpty()) {
                words.add(word.toLowerCase(Locale.ROOT));
            }
        }
        int end = words.size();
        while (end > 1 && NAME_SUFFIX_WORDS.contains(words.get(end - 1))) {
            end--;
        }
        return String.join("", words.subList(0, end));
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
