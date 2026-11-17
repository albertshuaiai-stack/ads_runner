package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsCampaignPlan;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 投前评估只回答两件事: 1) 算账值不值得投; 2) 推广词能不能带来对的流量
 * Pre-launch evaluation answers two questions: 1) do the economics work; 2) do the keywords bring the right traffic
 */
@Component
public class AdsPlanEvaluator {

    public static final String GO = "GO";
    public static final String TEST_SMALL = "TEST_SMALL";
    public static final String NO_GO = "NO_GO";
    public static final String NEED_INFO = "NEED_INFO";

    public static final String OK = "OK";
    public static final String WARN = "WARN";
    public static final String ERROR = "ERROR";

    // 推广词流量类型 / keyword traffic categories
    public static final String BRAND_BUY = "BRAND_BUY";
    public static final String BRAND = "BRAND";
    public static final String GENERIC = "GENERIC";
    public static final String COUPON = "COUPON";
    public static final String NON_BUYING = "NON_BUYING";

    // 品牌词 / 泛词的经验转化率 / typical conversion rate of brand and generic search traffic
    static final double ASSUMED_BRAND_CVR = 0.08;
    static final double ASSUMED_GENERIC_CVR = 0.02;
    // 建议出价在回本出价上留 20% 余量 / suggested max CPC keeps a 20% margin under break-even
    static final double BID_SAFETY = 0.8;
    static final double DEFAULT_APPROVAL_RATE = 0.7;
    // 回本所需转化率阈值 / required conversion rate thresholds
    static final double GO_REQUIRED_CVR = 0.06;
    static final double NO_GO_REQUIRED_CVR = 0.15;
    // 未填预计 CPC 时按品牌词建议出价判断 / thresholds on the brand max CPC when no expected CPC is given
    static final double GO_MIN_MAX_CPC = 0.30;
    static final double TEST_MIN_MAX_CPC = 0.10;
    // 0 单时花费达到 3 倍净佣金即可在 95% 置信度判定不回本 / zero orders after 3x net commission => <5% chance of break-even
    static final double STOP_SPEND_MULTIPLIER = 3.0;
    static final double GENERIC_SHARE_LIMIT = 0.5;
    static final int MAX_TEST_KEYWORDS = 20;
    static final int KEYWORD_CHAR_LIMIT = 80;
    static final int KEYWORD_WORD_LIMIT = 10;

    private static final double[] CVR_SCENARIOS = {0.02, 0.05, 0.10, 0.20};

    public record ParsedKeyword(String raw, String text, String matchType) {
    }

    public Map<String, Object> evaluate(AdsCampaignPlan plan) {
        List<String> brandTokens = AdsKeywordRules.brandTokens(plan.getBrandName(), plan.getCampaignName(), null);
        Map<String, Object> economics = economics(plan);
        Map<String, Object> keywords = keywords(plan, brandTokens, (Double) economics.get("maxCpcGeneric"));

        List<Map<String, Object>> reasons = new ArrayList<>();
        String verdict = verdict(economics, keywords, reasons);
        int errors = countLevel(keywords, ERROR);
        int warns = countLevel(keywords, WARN);
        if (errors > 0) {
            reasons.add(reason(ERROR, "Fix " + errors + " keyword error(s) before launch."));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("verdict", verdict);
        result.put("readyToLaunch", errors == 0 && (GO.equals(verdict) || TEST_SMALL.equals(verdict)));
        result.put("reasons", reasons);
        result.put("economics", economics);
        result.put("keywords", keywords);
        result.put("brandTokens", brandTokens);
        result.put("errorCount", errors);
        result.put("warnCount", warns);
        result.put("markdown", markdown(plan, result));
        return result;
    }

    // ---------- 1. economics ----------

    private Map<String, Object> economics(AdsCampaignPlan plan) {
        Map<String, Object> result = new LinkedHashMap<>();
        Double commission = commissionPerOrder(plan);
        double approval = approvalRate(plan);
        result.put("commissionPerOrder", round(commission));
        result.put("approvalRate", round(approval));
        if (commission == null || commission <= 0) {
            return result;
        }
        double net = commission * approval;
        result.put("netCommissionPerOrder", round(net));
        result.put("maxCpcBrand", round(net * ASSUMED_BRAND_CVR * BID_SAFETY));
        result.put("maxCpcGeneric", round(net * ASSUMED_GENERIC_CVR * BID_SAFETY));
        Map<String, Object> breakEven = new LinkedHashMap<>();
        for (double cvr : CVR_SCENARIOS) {
            breakEven.put(Math.round(cvr * 100) + "%", round(net * cvr));
        }
        result.put("breakEvenCpc", breakEven);
        result.put("testBudget", round(Math.max(net * STOP_SPEND_MULTIPLIER, 1.0)));
        Double expectedCpc = toDouble(plan.getExpectedCpc());
        result.put("expectedCpc", round(expectedCpc));
        if (expectedCpc != null && expectedCpc > 0) {
            double required = expectedCpc / net;
            result.put("requiredCvr", round(required));
            result.put("minClicksToJudge", (long) Math.ceil(STOP_SPEND_MULTIPLIER / required));
        }
        result.put("stopRule", "Stop when spend reaches " + money(Math.max(net * STOP_SPEND_MULTIPLIER, 1.0))
                + " with no order (3x net commission per order).");
        return result;
    }

    static Double commissionPerOrder(AdsCampaignPlan plan) {
        Double value = toDouble(plan.getCommissionValue());
        if (value == null) {
            return null;
        }
        if (AdsCampaignPlan.COMMISSION_PERCENT.equalsIgnoreCase(String.valueOf(plan.getCommissionType()))) {
            Double aov = toDouble(plan.getAvgOrderValue());
            return aov == null ? null : aov * value / 100.0;
        }
        return value;
    }

    static double approvalRate(AdsCampaignPlan plan) {
        Double rate = toDouble(plan.getExpectedApprovalRate());
        return rate == null || rate <= 0 || rate > 1 ? DEFAULT_APPROVAL_RATE : rate;
    }

    private String verdict(Map<String, Object> economics, Map<String, Object> keywords, List<Map<String, Object>> reasons) {
        Object net = economics.get("netCommissionPerOrder");
        if (!(net instanceof Double netCommission)) {
            reasons.add(reason(WARN, "Commission per order is missing (fixed USD, or percent plus average order value)."));
            return NEED_INFO;
        }
        String verdict;
        Object required = economics.get("requiredCvr");
        if (required instanceof Double requiredCvr) {
            reasons.add(reason(OK, "At the expected CPC " + money((Double) economics.get("expectedCpc")) + " you need a "
                    + percent(requiredCvr) + " conversion rate to break even (brand searches usually convert 5%-15%)."));
            verdict = requiredCvr <= GO_REQUIRED_CVR ? GO : requiredCvr <= NO_GO_REQUIRED_CVR ? TEST_SMALL : NO_GO;
        } else {
            double maxCpc = (Double) economics.get("maxCpcBrand");
            reasons.add(reason(OK, "You can afford at most " + money(maxCpc) + " per click on brand keywords. "
                    + "Fill in Expected CPC for a sharper verdict."));
            verdict = maxCpc >= GO_MIN_MAX_CPC ? GO : maxCpc >= TEST_MIN_MAX_CPC ? TEST_SMALL : NO_GO;
        }
        if (NO_GO.equals(verdict)) {
            reasons.add(reason(ERROR, "Net commission " + money(netCommission)
                    + " per order is too small for the click price. Pick a higher-commission offer."));
            return verdict;
        }
        double genericShare = ((Number) keywords.get("genericShare")).doubleValue();
        if (GO.equals(verdict) && genericShare > GENERIC_SHARE_LIMIT) {
            verdict = TEST_SMALL;
            reasons.add(reason(WARN, "Most keywords are generic. They convert around 2%, so the math only works at "
                    + money((Double) economics.get("maxCpcGeneric")) + " per click or below."));
        }
        if (TEST_SMALL.equals(verdict)) {
            reasons.add(reason(WARN, "Launch with the test budget " + money((Double) economics.get("testBudget"))
                    + " and brand keywords in [exact] or \"phrase\" only."));
        }
        return verdict;
    }

    // ---------- 2. keywords ----------

    static List<ParsedKeyword> parseKeywords(String text) {
        List<ParsedKeyword> result = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return result;
        }
        for (String line : text.split("[\\r\\n]+")) {
            String raw = line.trim();
            if (raw.isEmpty()) {
                continue;
            }
            String matchType;
            String body;
            if (raw.startsWith("[") && raw.endsWith("]") && raw.length() > 2) {
                matchType = "EXACT";
                body = raw.substring(1, raw.length() - 1);
            } else if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() > 2) {
                matchType = "PHRASE";
                body = raw.substring(1, raw.length() - 1);
            } else {
                matchType = "BROAD";
                body = raw.replace("+", "");
            }
            result.add(new ParsedKeyword(raw, body.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "), matchType));
        }
        return result;
    }

    private Map<String, Object> keywords(AdsCampaignPlan plan, List<String> brandTokens, Double maxCpcGeneric) {
        List<ParsedKeyword> parsed = parseKeywords(plan.getKeywords());
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Integer> byCategory = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        int exact = 0;
        int phrase = 0;
        int broad = 0;
        for (ParsedKeyword keyword : parsed) {
            switch (keyword.matchType()) {
                case "EXACT" -> exact++;
                case "PHRASE" -> phrase++;
                default -> broad++;
            }
            Map<String, Object> item = classify(keyword, brandTokens, maxCpcGeneric, !seen.add(keyword.matchType() + ":" + keyword.text()));
            byCategory.merge((String) item.get("category"), 1, Integer::sum);
            items.add(item);
        }

        int generic = byCategory.getOrDefault(GENERIC, 0);
        boolean hasBrand = byCategory.getOrDefault(BRAND, 0) + byCategory.getOrDefault(BRAND_BUY, 0) > 0;
        double genericShare = parsed.isEmpty() ? 0 : (double) generic / parsed.size();
        List<Map<String, Object>> issues = new ArrayList<>();
        if (brandTokens.isEmpty()) {
            issues.add(issue(WARN, "Fill in the brand name so brand keywords can be recognised."));
        }
        if (parsed.isEmpty()) {
            issues.add(issue(WARN, "No keywords entered."));
        } else {
            if (!hasBrand && !brandTokens.isEmpty()) {
                issues.add(issue(WARN, "No brand keyword. Cashback buyers mostly search the brand; add the suggested keywords."));
            }
            if (genericShare > GENERIC_SHARE_LIMIT) {
                issues.add(issue(WARN, generic + " of " + parsed.size() + " keywords are generic. They pull shoppers who "
                        + "have not chosen the brand yet and compete with big retailers."));
            }
            if (parsed.size() > MAX_TEST_KEYWORDS) {
                issues.add(issue(WARN, parsed.size() + " keywords is a lot for a test budget. Keep the 5-15 strongest."));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", parsed.size());
        result.put("exact", exact);
        result.put("phrase", phrase);
        result.put("broad", broad);
        result.put("byCategory", byCategory);
        result.put("genericShare", round(genericShare));
        result.put("items", items);
        result.put("issues", issues);
        result.put("suggested", suggestKeywords(plan, brandTokens, seen));
        result.put("negativesToAdd", missingNegatives(plan.getNegativeKeywords()));
        return result;
    }

    // 判断这个词带来的是哪类流量 / which kind of traffic this keyword brings
    private Map<String, Object> classify(ParsedKeyword keyword, List<String> brandTokens, Double maxCpcGeneric, boolean duplicate) {
        String category;
        String level;
        List<String> advice = new ArrayList<>();
        String intent = AdsKeywordRules.findNegativeIntent(keyword.text());
        if (intent != null) {
            category = NON_BUYING;
            level = ERROR;
            advice.add("Searches for \"" + intent + "\" do not buy. Remove it and add it as a negative.");
        } else if (AdsKeywordRules.findCouponTerm(keyword.text()) != null) {
            category = COUPON;
            level = WARN;
            advice.add("Coupon searchers often already hold a code, and many offers forbid these terms. Confirm the terms first.");
        } else if (AdsKeywordRules.containsBrand(keyword.text(), brandTokens)) {
            boolean buying = AdsKeywordRules.findPurchaseTerm(keyword.text()) != null;
            category = buying ? BRAND_BUY : BRAND;
            level = OK;
            advice.add(buying ? "Brand plus buying intent: the best traffic." : "Brand search: high intent.");
        } else {
            category = GENERIC;
            level = WARN;
            advice.add("No brand: converts around 2%" + (maxCpcGeneric == null ? "" : ", bid at most " + money(maxCpcGeneric))
                    + " or drop it.");
        }
        if ("BROAD".equals(keyword.matchType()) && !NON_BUYING.equals(category)) {
            level = worse(level, WARN);
            advice.add("Broad match also shows on loosely related searches. Use [exact] or \"phrase\".");
        }
        if (keyword.text().length() > KEYWORD_CHAR_LIMIT || keyword.text().split(" ").length > KEYWORD_WORD_LIMIT) {
            level = ERROR;
            advice.add("Over Google's limit of 80 characters / 10 words.");
        }
        if (duplicate) {
            level = worse(level, WARN);
            advice.add("Duplicate keyword.");
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("keyword", keyword.raw());
        item.put("matchType", keyword.matchType());
        item.put("category", category);
        item.put("level", level);
        item.put("advice", String.join(" ", advice));
        return item;
    }

    private List<String> suggestKeywords(AdsCampaignPlan plan, List<String> brandTokens, Set<String> existing) {
        List<String> result = new ArrayList<>();
        if (brandTokens.isEmpty()) {
            return result;
        }
        String brand = StringUtils.hasText(plan.getBrandName())
                ? plan.getBrandName().split("[,/|;]")[0].trim().toLowerCase(Locale.ROOT)
                : brandTokens.get(0);
        for (String candidate : List.of("[" + brand + "]", "\"" + brand + "\"", "[" + brand + " sale]",
                "[" + brand + " discount]", "[" + brand + " deals]")) {
            ParsedKeyword keyword = parseKeywords(candidate).get(0);
            if (!existing.contains(keyword.matchType() + ":" + keyword.text())) {
                result.add(candidate);
            }
        }
        return result;
    }

    private List<String> missingNegatives(String negativeText) {
        Set<String> existing = new HashSet<>();
        for (ParsedKeyword keyword : parseKeywords(negativeText)) {
            existing.add(keyword.text().replaceFirst("^-", ""));
        }
        List<String> result = new ArrayList<>();
        for (String term : AdsKeywordRules.NEGATIVE_INTENT_TERMS) {
            if (!existing.contains(term)) {
                result.add(term);
            }
        }
        return result;
    }

    // ---------- markdown brief ----------

    @SuppressWarnings("unchecked")
    private String markdown(AdsCampaignPlan plan, Map<String, Object> result) {
        Map<String, Object> economics = (Map<String, Object>) result.get("economics");
        Map<String, Object> keywords = (Map<String, Object>) result.get("keywords");
        StringBuilder md = new StringBuilder();
        md.append("# Pre-launch brief: ").append(plan.getCampaignName()).append('\n');
        md.append("- Verdict: **").append(result.get("verdict")).append("**")
                .append(Boolean.TRUE.equals(result.get("readyToLaunch")) ? " (ready)" : " (not ready)").append('\n');
        md.append("- Brand: ").append(text(plan.getBrandName())).append(" | Country: ").append(text(plan.getCountry())).append('\n');
        md.append("- Commission: ").append(text(plan.getCommissionType())).append(' ').append(text(plan.getCommissionValue()))
                .append(" | AOV: ").append(text(plan.getAvgOrderValue()))
                .append(" | Approval: ").append(text(economics.get("approvalRate")))
                .append(" | Expected CPC: ").append(text(plan.getExpectedCpc())).append('\n');
        md.append("\n## 1. Economics\n");
        md.append("- Net commission per order: ").append(text(economics.get("netCommissionPerOrder"))).append('\n');
        md.append("- Max CPC brand / generic: ").append(text(economics.get("maxCpcBrand"))).append(" / ")
                .append(text(economics.get("maxCpcGeneric"))).append('\n');
        md.append("- Required CVR at expected CPC: ").append(text(economics.get("requiredCvr"))).append('\n');
        md.append("- Test budget (stop line): ").append(text(economics.get("testBudget"))).append('\n');
        ((List<Map<String, Object>>) result.get("reasons")).forEach(item ->
                md.append("- [").append(item.get("level")).append("] ").append(item.get("message")).append('\n'));
        md.append("\n## 2. Keywords\n");
        md.append("| Keyword | Match | Traffic | Level | Advice |\n|---|---|---|---|---|\n");
        ((List<Map<String, Object>>) keywords.get("items")).forEach(item ->
                md.append("| `").append(item.get("keyword")).append("` | ").append(item.get("matchType"))
                        .append(" | ").append(item.get("category")).append(" | ").append(item.get("level"))
                        .append(" | ").append(item.get("advice")).append(" |\n"));
        ((List<Map<String, Object>>) keywords.get("issues")).forEach(item ->
                md.append("- [").append(item.get("level")).append("] ").append(item.get("message")).append('\n'));
        List<String> suggested = (List<String>) keywords.get("suggested");
        if (!suggested.isEmpty()) {
            md.append("- Suggested: ").append(String.join(", ", suggested)).append('\n');
        }
        md.append("\n## Negative keywords\n```\n").append(text(plan.getNegativeKeywords())).append("\n```\n");
        if (StringUtils.hasText(plan.getNotes())) {
            md.append("\n## Notes\n").append(plan.getNotes()).append('\n');
        }
        return md.toString();
    }

    // ---------- helpers ----------

    @SuppressWarnings("unchecked")
    private int countLevel(Map<String, Object> keywords, String level) {
        long items = ((List<Map<String, Object>>) keywords.get("items")).stream()
                .filter(item -> level.equals(item.get("level"))).count();
        long issues = ((List<Map<String, Object>>) keywords.get("issues")).stream()
                .filter(item -> level.equals(item.get("level"))).count();
        return (int) (items + issues);
    }

    private static String worse(String current, String candidate) {
        List<String> order = List.of(OK, WARN, ERROR);
        return order.indexOf(candidate) > order.indexOf(current) ? candidate : current;
    }

    private static Map<String, Object> issue(String level, String message) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("level", level);
        item.put("message", message);
        return item;
    }

    private static Map<String, Object> reason(String level, String message) {
        return issue(level, message);
    }

    static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    static Double round(Double value) {
        return value == null ? null : Math.round(value * 10000.0) / 10000.0;
    }

    static String money(Double value) {
        return value == null ? "-" : String.format(Locale.ROOT, "$%.2f", value);
    }

    static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value * 100);
    }

    private static String text(Object value) {
        return value == null || String.valueOf(value).isBlank() ? "-" : String.valueOf(value);
    }
}
