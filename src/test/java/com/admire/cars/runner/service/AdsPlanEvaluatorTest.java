package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsCampaignPlan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdsPlanEvaluatorTest {

    private final AdsPlanEvaluator evaluator = new AdsPlanEvaluator();

    private AdsCampaignPlan basePlan() {
        AdsCampaignPlan plan = new AdsCampaignPlan();
        plan.setCampaignName("sharkninja");
        plan.setBrandName("SharkNinja, Ninja");
        plan.setCountry("AU");
        plan.setCommissionType(AdsCampaignPlan.COMMISSION_FIXED);
        plan.setCommissionValue(new BigDecimal("10"));
        plan.setExpectedApprovalRate(new BigDecimal("0.8"));
        plan.setKeywords("[sharkninja]\n\"ninja air fryer\"\n[sharkninja sale]");
        return plan;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> result, String key) {
        return (Map<String, Object>) result.get(key);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> item(Map<String, Object> result, String keyword) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) section(result, "keywords").get("items");
        return items.stream().filter(i -> keyword.equals(i.get("keyword"))).findFirst().orElseThrow();
    }

    @Test
    void goWhenNetCommissionSupportsTheExpectedCpc() {
        AdsCampaignPlan plan = basePlan();
        plan.setExpectedCpc(new BigDecimal("0.30"));

        Map<String, Object> result = evaluator.evaluate(plan);

        assertEquals(AdsPlanEvaluator.GO, result.get("verdict"));
        assertEquals(Boolean.TRUE, result.get("readyToLaunch"));
        Map<String, Object> economics = section(result, "economics");
        assertEquals(8.0, economics.get("netCommissionPerOrder"));
        assertEquals(0.0375, economics.get("requiredCvr"));
        assertEquals(0.512, economics.get("maxCpcBrand"));
        assertEquals(0.128, economics.get("maxCpcGeneric"));
        assertEquals(24.0, economics.get("testBudget"));
    }

    @Test
    void noGoWhenCommissionIsTooSmallForTheClickPrice() {
        AdsCampaignPlan plan = basePlan();
        plan.setCommissionValue(new BigDecimal("0.75"));
        plan.setExpectedCpc(new BigDecimal("0.24"));

        Map<String, Object> result = evaluator.evaluate(plan);

        assertEquals(AdsPlanEvaluator.NO_GO, result.get("verdict"));
        assertEquals(Boolean.FALSE, result.get("readyToLaunch"));
    }

    @Test
    void needInfoWithoutCommission() {
        AdsCampaignPlan plan = basePlan();
        plan.setCommissionValue(null);

        assertEquals(AdsPlanEvaluator.NEED_INFO, evaluator.evaluate(plan).get("verdict"));
    }

    @Test
    void percentCommissionUsesAverageOrderValue() {
        AdsCampaignPlan plan = basePlan();
        plan.setCommissionType(AdsCampaignPlan.COMMISSION_PERCENT);
        plan.setCommissionValue(new BigDecimal("5"));
        plan.setAvgOrderValue(new BigDecimal("150"));

        assertEquals(7.5, AdsPlanEvaluator.commissionPerOrder(plan));
    }

    @Test
    void classifiesKeywordTraffic() {
        AdsCampaignPlan plan = basePlan();
        plan.setKeywords("[sharkninja sale]\n[ninja]\n[air fryer]\nninja login\n[sharkninja promo code]\nsharkninja");

        Map<String, Object> result = evaluator.evaluate(plan);

        assertEquals(AdsPlanEvaluator.BRAND_BUY, item(result, "[sharkninja sale]").get("category"));
        assertEquals(AdsPlanEvaluator.OK, item(result, "[sharkninja sale]").get("level"));
        assertEquals(AdsPlanEvaluator.BRAND, item(result, "[ninja]").get("category"));
        assertEquals(AdsPlanEvaluator.GENERIC, item(result, "[air fryer]").get("category"));
        assertEquals(AdsPlanEvaluator.WARN, item(result, "[air fryer]").get("level"));
        assertEquals(AdsPlanEvaluator.NON_BUYING, item(result, "ninja login").get("category"));
        assertEquals(AdsPlanEvaluator.ERROR, item(result, "ninja login").get("level"));
        assertEquals(AdsPlanEvaluator.COUPON, item(result, "[sharkninja promo code]").get("category"));
        assertEquals(AdsPlanEvaluator.WARN, item(result, "sharkninja").get("level"));
        assertTrue(String.valueOf(item(result, "sharkninja").get("advice")).contains("Broad match"));
        assertEquals(Boolean.FALSE, result.get("readyToLaunch"));
        assertFalse(((List<?>) section(result, "keywords").get("negativesToAdd")).isEmpty());
    }

    @Test
    void mostlyGenericKeywordsDowngradeGoToTestSmall() {
        AdsCampaignPlan plan = basePlan();
        plan.setExpectedCpc(new BigDecimal("0.30"));
        plan.setKeywords("[air fryer]\n[best blender]\n[robot vacuum]\n[sharkninja]");

        Map<String, Object> result = evaluator.evaluate(plan);

        assertEquals(AdsPlanEvaluator.TEST_SMALL, result.get("verdict"));
        assertEquals(0.75, section(result, "keywords").get("genericShare"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void suggestsMissingBrandKeywords() {
        AdsCampaignPlan plan = basePlan();
        plan.setKeywords("[sharkninja]");

        List<String> suggested = (List<String>) section(evaluator.evaluate(plan), "keywords").get("suggested");

        assertFalse(suggested.contains("[sharkninja]"));
        assertTrue(suggested.contains("[sharkninja sale]"));
        assertFalse(suggested.stream().anyMatch(s -> s.contains("official")));
    }
}
