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
        long orders = approved + pending + rejected;
        return new AdsReviewRules.TaskMetrics(
                1L, "brand", "RUNNING",
                clicks, cost,
                true, TODAY, clicks, clicks * 10, cost, Map.of("SEARCH", clicks),
                orders, approved, pending, rejected,
                commissionApproved, commissionPending, commissionApproved + commissionPending,
                orders7d, orders7d > 0 ? commissionApproved : 0, orders7d > 0 ? commissionPending : 0,
                planNet, null,
                partners, false, "PRESENCE", bidding,
                null, null, null,
                TODAY, daysRunning);
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
                null, null, null, null, null, null, null, TODAY, null);

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
    void stopsAtDaySevenWithoutAnyOrderEvenBelowStopLine() {
        AdsReviewRules.Decision decision = AdsReviewRules.decide(
                metrics(10.0, 50, 0, 0, 0, 0, 0, 7.0, false, "MANUAL_CPC", 7, 0));

        assertEquals(AdsReviewRules.STOP, decision.decision());
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

        assertEquals(10, AdsAdvisorService.daysRunning(null, null, List.of(task), List.of(ledger), TODAY));
        assertEquals(3, AdsAdvisorService.daysRunning(null, null, List.of(task), null, TODAY));
        assertNull(AdsAdvisorService.daysRunning(null, null, List.of(new AdsNormalInfo()), null, TODAY));
    }

    @Test
    void daysRunningUsesFirstSpendDateFromGoogleAds() {
        // task created 10 days ago, first spend 3 days ago, daily stats exist for 30 days
        AdsNormalInfo task = new AdsNormalInfo();
        task.setCreateDate(TODAY.minusDays(10).atTime(9, 0));

        assertEquals(4, AdsAdvisorService.daysRunning(TODAY.minusDays(3), TODAY.minusDays(30),
                List.of(task), null, TODAY));
    }

    @Test
    void daysRunningKeepsOlderTaskDateWhenSpendHistoryMayBeCut() {
        // first spend equals the owner's first stat date: older spend may be outside the first lookback window
        AdsNormalInfo task = new AdsNormalInfo();
        task.setCreateDate(TODAY.minusDays(20).atTime(9, 0));

        assertEquals(21, AdsAdvisorService.daysRunning(TODAY.minusDays(5), TODAY.minusDays(5),
                List.of(task), null, TODAY));
    }

    @Test
    void noDataWhenCostIsMissing() {
        AdsReviewRules.TaskMetrics m = new AdsReviewRules.TaskMetrics(
                1L, "fresh", "RUNNING", 49, null, false, null, 0, 0, null, Map.of(),
                11, 0, 11, 0, 0, 7.79, 7.79, 0, 0, 0, null, null,
                null, null, null, null, null, null, null, TODAY, null);

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
                null, null, null, null, null, null, null, TODAY, null);

        assertEquals(AdsReviewRules.PAUSED, AdsReviewRules.decide(m).decision());
    }
}
