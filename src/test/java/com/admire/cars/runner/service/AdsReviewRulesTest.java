package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsCampaignCost;
import com.admire.cars.runner.entity.AdsNormalInfo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdsReviewRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);

    private AdsReviewRules.TaskMetrics metrics(double cost, long clicks, long approved, long pending, long rejected,
                                               double commissionApproved, double commissionPending,
                                               Double planNet, Boolean partners, String bidding) {
        return metrics(cost, clicks, approved, pending, rejected, commissionApproved, commissionPending,
                planNet, partners, bidding, null, approved + pending + rejected);
    }

    private AdsReviewRules.TaskMetrics metrics(double cost, long clicks, long approved, long pending, long rejected,
                                               double commissionApproved, double commissionPending,
                                               Double planNet, Boolean partners, String bidding,
                                               Integer daysRunning, long orders7d) {
        return metrics(cost, clicks, approved, pending, rejected, commissionApproved, commissionPending,
                planNet, partners, bidding, daysRunning, orders7d,
                null, commissionApproved, commissionPending, Map.of("SEARCH", clicks), null);
    }

    private AdsReviewRules.TaskMetrics metrics(double cost, long clicks, long approved, long pending, long rejected,
                                               double commissionApproved, double commissionPending,
                                               Double planNet, Boolean partners, String bidding,
                                               Integer daysRunning, long orders7d,
                                               Double lagCost, double approvedMatured, double pendingMatured,
                                               Map<String, Long> networkClicks, Double nonBuyingShare) {
        long orders = approved + pending + rejected;
        return new AdsReviewRules.TaskMetrics(
                1L, "brand", "RUNNING",
                clicks, cost,
                true, TODAY, clicks, clicks * 10, cost, networkClicks,
                orders, approved, pending, rejected,
                commissionApproved, commissionPending, commissionApproved + commissionPending,
                orders7d, orders7d > 0 ? commissionApproved : 0, orders7d > 0 ? commissionPending : 0,
                planNet, null,
                partners, false, "PRESENCE", bidding,
                null, null, null,
                TODAY, daysRunning,
                AdsReviewRules.DEFAULT_REPORT_LAG_DAYS, lagCost, approvedMatured, pendingMatured, nonBuyingShare);
    }

    @Test
    void watchesTaskWithoutPlanAndWithoutOrders() {
        AdsReviewRules.Decision decision = AdsReviewRules.decide(metrics(10.0, 50, 0, 0, 0, 0, 0, null, false, "MANUAL_CPC"));

        assertEquals(AdsReviewRules.WATCH, decision.decision());
        assertEquals(null, decision.metrics().get("breakEvenCpc"));
    }

    @Test
    void noDataWithoutPlanOrdersOrCost() {
        AdsReviewRules.TaskMetrics m = new AdsReviewRules.TaskMetrics(
                1L, "new", "RUNNING", 0, null, false, null, 0, 0, null, Map.of(),
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, null,
                null, null, null, null, null, null, null, TODAY, null,
                3, null, 0, 0, null);

        assertEquals(AdsReviewRules.NO_DATA, AdsReviewRules.decide(m).decision());
    }

    @Test
    void stopsAfterThreeTimesNetCommissionWithoutOrders() {
        AdsReviewRules.Decision decision = AdsReviewRules.decide(metrics(19.0, 368, 0, 0, 0, 0, 0, 0.525, false, "MANUAL_CPC"));

        assertEquals(AdsReviewRules.STOP, decision.decision());
        assertTrue(decision.actions().get(0).startsWith("Pause"));
    }

    @Test
    void watchesBeforeTheStopLine() {
        AdsReviewRules.Decision decision = AdsReviewRules.decide(metrics(10.0, 50, 0, 0, 0, 0, 0, 7.0, false, "MANUAL_CPC"));

        assertEquals(AdsReviewRules.WATCH, decision.decision());
    }

    @Test
    void scalesWhenClearlyProfitable() {
        AdsReviewRules.Decision decision = AdsReviewRules.decide(metrics(20.0, 100, 5, 1, 0, 40, 8, null, false, "MANUAL_CPC"));

        assertEquals(AdsReviewRules.SCALE, decision.decision());
    }

    @Test
    void optimizesBetweenHalfAndBreakEven() {
        // effective = 3 + 2 * 0.7 = 4.4, cost 6 => ROAS 0.73, stop line = 3 * (5/2 * 0.7) = 5.25
        AdsReviewRules.Decision decision = AdsReviewRules.decide(metrics(6.0, 40, 1, 1, 0, 3, 2, null, false, "MANUAL_CPC"));

        assertEquals(AdsReviewRules.OPTIMIZE, decision.decision());
        assertTrue(decision.actions().stream().anyMatch(a -> a.startsWith("Lower max CPC")));
    }

    @Test
    void rejectedOrdersLowerTheApprovalRate() {
        // 3 approved, 20 rejected: approval 13%, commission counted at that rate
        AdsReviewRules.Decision decision = AdsReviewRules.decide(metrics(30.0, 332, 3, 0, 20, 2.2, 0, null, false, "MANUAL_CPC"));

        assertEquals(AdsReviewRules.STOP, decision.decision());
        assertTrue(decision.actions().stream().anyMatch(a -> a.startsWith("Rejection rate")));
    }

    @Test
    void lowRoasWithOrdersWatchesBeforeDaySeven() {
        // 1 pending order worth 0.3, spent 30 => ROAS 0.007, far below 0.5, but only day 2
        AdsReviewRules.Decision decision = AdsReviewRules.decide(
                metrics(30.0, 100, 0, 1, 0, 0, 0.3, 0.1, false, "MANUAL_CPC", 2, 1));

        assertEquals(AdsReviewRules.WATCH, decision.decision());
        assertTrue(decision.reasons().stream().anyMatch(r -> r.contains("day 2")));
    }

    @Test
    void lowRoasWithOrdersStopsFromDaySeven() {
        AdsReviewRules.Decision decision = AdsReviewRules.decide(
                metrics(30.0, 100, 0, 1, 0, 0, 0.3, 0.1, false, "MANUAL_CPC", 7, 1));

        assertEquals(AdsReviewRules.STOP, decision.decision());
    }

    @Test
    void zeroOrdersAboveStopLineStillStopsEarly() {
        AdsReviewRules.Decision decision = AdsReviewRules.decide(
                metrics(19.0, 368, 0, 0, 0, 0, 0, 0.525, false, "MANUAL_CPC", 1, 0));

        assertEquals(AdsReviewRules.STOP, decision.decision());
    }

    @Test
    void stopsAfterSevenDaysWithoutRecentOrders() {
        // lifetime ROAS 2.0 from old orders, but nothing in the last 7 days
        AdsReviewRules.Decision decision = AdsReviewRules.decide(
                metrics(10.0, 100, 2, 0, 0, 20, 0, 7.0, false, "MANUAL_CPC", 20, 0));

        assertEquals(AdsReviewRules.STOP, decision.decision());
        assertTrue(decision.reasons().stream().anyMatch(r -> r.contains("no order in the last 7 days")));
    }

    @Test
    void stopsAtDaySevenPlusLagWithoutAnyOrderEvenBelowStopLine() {
        AdsReviewRules.Decision decision = AdsReviewRules.decide(
                metrics(10.0, 50, 0, 0, 0, 0, 0, 7.0, false, "MANUAL_CPC", 10, 0));

        assertEquals(AdsReviewRules.STOP, decision.decision());
    }

    @Test
    void noOrderRuleWaitsForTheLagWindow() {
        // day 7 with a 3-day lag: only 4 days of spend have had time to convert
        AdsReviewRules.Decision decision = AdsReviewRules.decide(
                metrics(10.0, 50, 0, 0, 0, 0, 0, 7.0, false, "MANUAL_CPC", 7, 0));

        assertEquals(AdsReviewRules.WATCH, decision.decision());
    }

    @Test
    void recentSpendIsNotJudgedForTheStopLine() {
        // stop line 3 x 0.525 = 1.575; spent 19 but 18 of it in the lag window => judged 1.0
        AdsReviewRules.Decision decision = AdsReviewRules.decide(
                metrics(19.0, 368, 0, 0, 0, 0, 0, 0.525, false, "MANUAL_CPC", 2, 0,
                        18.0, 0, 0, Map.of("SEARCH", 368L), null));

        assertEquals(AdsReviewRules.WATCH, decision.decision());
        assertEquals(1.0, (Double) decision.metrics().get("judgedCostUsd"), 1e-9);
        assertTrue(decision.reasons().stream().anyMatch(r -> r.contains("awaiting orders")));
    }

    @Test
    void lowLifetimeRoasIsNotStoppedWhenJudgedSpendIsProfitable() {
        // 8 pending orders worth 48, effective 33.6 on 78 spend => ROAS 0.43;
        // 30 spent in the lag window and 2 orders (12) are recent => judged ROAS (36 x 0.7) / 48 = 0.525
        AdsReviewRules.Decision decision = AdsReviewRules.decide(
                metrics(78.0, 382, 0, 8, 0, 0, 48.0, null, false, "MANUAL_CPC", 7, 8,
                        30.0, 0, 36.0, Map.of("SEARCH", 382L), null));

        assertEquals(AdsReviewRules.OPTIMIZE, decision.decision());
        assertTrue(decision.reasons().stream().anyMatch(r -> r.contains("instead of stopping")));
    }

    @Test
    void pollutedTrafficIsOptimizedNotStopped() {
        // 1 order worth 12 at 0.7 => 8.4 on 27.4 spend => ROAS 0.31, but 80% of term spend is "login"
        AdsReviewRules.Decision decision = AdsReviewRules.decide(
                metrics(27.4, 175, 0, 1, 0, 0, 12.0, null, false, "MANUAL_CPC", 8, 1,
                        null, 0, 12.0, Map.of("SEARCH", 175L), 0.8));

        assertEquals(AdsReviewRules.OPTIMIZE, decision.decision());
        assertEquals(Boolean.TRUE, decision.metrics().get("polluted"));
        assertTrue(decision.actions().get(0).startsWith("Fix traffic first"));
    }

    @Test
    void pollutedTrafficStillStopsWhenCleanSpendIsBad() {
        // 76% display clicks, but 0.3 commission on the clean 20 spend is still far below 0.5x
        AdsReviewRules.Decision decision = AdsReviewRules.decide(
                metrics(83.0, 200, 0, 1, 0, 0, 0.3, null, false, "MANUAL_CPC", 9, 1,
                        null, 0, 0.3, Map.of("SEARCH", 48L, "CONTENT", 152L), null));

        assertEquals(AdsReviewRules.STOP, decision.decision());
    }

    @Test
    void noOrderWithoutCommissionInfoNeedsMinimumSpendToStopOnTime() {
        // day 10, no plan, no order, only 9.5 spent: keep testing instead of a time-only stop
        AdsReviewRules.Decision small = AdsReviewRules.decide(
                metrics(9.5, 54, 0, 0, 0, 0, 0, null, false, "MANUAL_CPC", 10, 0));
        AdsReviewRules.Decision big = AdsReviewRules.decide(
                metrics(25.4, 162, 0, 0, 0, 0, 0, null, false, "MANUAL_CPC", 10, 0));

        assertEquals(AdsReviewRules.WATCH, small.decision());
        assertTrue(small.reasons().stream().anyMatch(r -> r.contains("no commission info")));
        assertEquals(AdsReviewRules.STOP, big.decision());
    }

    @Test
    void reportLagDaysDefaultsAndClamps() {
        assertEquals(AdsReviewRules.DEFAULT_REPORT_LAG_DAYS, AdsReviewRules.reportLagDays(null));
        assertEquals(0, AdsReviewRules.reportLagDays(-2));
        assertEquals(AdsReviewRules.MAX_REPORT_LAG_DAYS, AdsReviewRules.reportLagDays(30));
    }

    @Test
    void watchesBeforeDaySevenWithoutAnyOrderBelowStopLine() {
        AdsReviewRules.Decision decision = AdsReviewRules.decide(
                metrics(10.0, 50, 0, 0, 0, 0, 0, 7.0, false, "MANUAL_CPC", 6, 0));

        assertEquals(AdsReviewRules.WATCH, decision.decision());
    }

    @Test
    void daysRunningFallsBackToEarliestTaskOrLedgerDate() {
        AdsNormalInfo task = new AdsNormalInfo();
        task.setCreateDate(TODAY.minusDays(2).atTime(9, 0));
        AdsCampaignCost ledger = new AdsCampaignCost();
        ledger.setCreateDate(TODAY.minusDays(9).atTime(23, 0));

        assertEquals(10, days(null, null, null, List.of(task), List.of(ledger)));
        assertEquals(AdsAdvisorService.LAUNCH_FIRST_REPORT,
                AdsAdvisorService.launch(null, null, null, List.of(task), List.of(ledger)).source());
        assertEquals(3, days(null, null, null, List.of(task), null));
        assertEquals(AdsAdvisorService.LAUNCH_TASK_CREATED,
                AdsAdvisorService.launch(null, null, null, List.of(task), null).source());
        assertNull(days(null, null, null, List.of(new AdsNormalInfo()), null));
    }

    @Test
    void daysRunningUsesFirstSpendDateFromGoogleAds() {
        // task created 10 days ago, first spend 3 days ago, daily stats exist for 30 days
        AdsNormalInfo task = new AdsNormalInfo();
        task.setCreateDate(TODAY.minusDays(10).atTime(9, 0));

        assertEquals(4, days(null, TODAY.minusDays(3), TODAY.minusDays(30), List.of(task), null));
    }

    @Test
    void daysRunningIgnoresTaskCreationWhenGoogleAdsDataExists() {
        // task created 20 days ago, but Google Ads only started spending 5 days ago
        AdsNormalInfo task = new AdsNormalInfo();
        task.setCreateDate(TODAY.minusDays(20).atTime(9, 0));

        assertEquals(6, days(null, TODAY.minusDays(5), TODAY.minusDays(5), List.of(task), null));
    }

    @Test
    void daysRunningPrefersScriptFirstSpendDate() {
        // the script saw the first spend 12 days ago; daily stats only start 5 days ago; task is 20 days old
        AdsNormalInfo task = new AdsNormalInfo();
        task.setCreateDate(TODAY.minusDays(20).atTime(9, 0));
        AdsCampaignCost ledger = new AdsCampaignCost();
        ledger.setCreateDate(TODAY.minusDays(15).atTime(9, 0));

        assertEquals(13, days(TODAY.minusDays(12), TODAY.minusDays(5), TODAY.minusDays(5), List.of(task), List.of(ledger)));
        assertEquals(AdsAdvisorService.LAUNCH_GOOGLE_ADS, AdsAdvisorService.launch(TODAY.minusDays(12),
                TODAY.minusDays(5), TODAY.minusDays(5), List.of(task), List.of(ledger)).source());
    }

    private static Integer days(LocalDate scriptFirstSpend, LocalDate firstSpend, LocalDate ownerFirstStat,
                                List<AdsNormalInfo> tasks, List<AdsCampaignCost> ledger) {
        return AdsAdvisorService.daysRunning(
                AdsAdvisorService.launch(scriptFirstSpend, firstSpend, ownerFirstStat, tasks, ledger), TODAY);
    }

    @Test
    void noDataWhenCostIsMissing() {
        AdsReviewRules.TaskMetrics m = new AdsReviewRules.TaskMetrics(
                1L, "fresh", "RUNNING", 49, null, false, null, 0, 0, null, Map.of(),
                11, 0, 11, 0, 0, 7.79, 7.79, 0, 0, 0, null, null,
                null, null, null, null, null, null, null, TODAY, null,
                3, null, 0, 7.79, null);

        assertEquals(AdsReviewRules.NO_DATA, AdsReviewRules.decide(m).decision());
    }

    @Test
    void flagsSearchPartnersAndSmartBidding() {
        AdsReviewRules.Decision decision = AdsReviewRules.decide(metrics(5.0, 50, 0, 0, 0, 0, 0, 7.0, true, "MAXIMIZE_CONVERSIONS"));

        assertTrue(decision.actions().stream().anyMatch(a -> a.contains("Search Partners")));
        assertTrue(decision.actions().stream().anyMatch(a -> a.contains("MAXIMIZE_CONVERSIONS")));
    }

    @Test
    void pausedTaskWithoutRecentSpendIsSkipped() {
        AdsReviewRules.TaskMetrics m = new AdsReviewRules.TaskMetrics(
                1L, "old", "PAUSED", 10, 3.0, true, TODAY, 0, 0, 0.0, Map.of(),
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1.0, null,
                null, null, null, null, null, null, null, TODAY, null,
                3, null, 0, 0, null);

        assertEquals(AdsReviewRules.PAUSED, AdsReviewRules.decide(m).decision());
    }
}
