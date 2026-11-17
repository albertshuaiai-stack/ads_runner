package com.admire.cars.runner.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdsReviewRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);

    private AdsReviewRules.TaskMetrics metrics(double cost, long clicks, long approved, long pending, long rejected,
                                               double commissionApproved, double commissionPending,
                                               Double planNet, Boolean partners, String bidding) {
        long orders = approved + pending + rejected;
        return new AdsReviewRules.TaskMetrics(
                1L, "brand", "RUNNING",
                clicks, cost,
                true, TODAY, clicks, clicks * 10, cost, Map.of("SEARCH", clicks),
                orders, approved, pending, rejected,
                commissionApproved, commissionPending, commissionApproved + commissionPending,
                0, 0, 0,
                planNet, null,
                partners, false, "PRESENCE", bidding,
                null, null, null,
                TODAY);
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
                null, null, null, null, null, null, null, TODAY);

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
    void noDataWhenCostIsMissing() {
        AdsReviewRules.TaskMetrics m = new AdsReviewRules.TaskMetrics(
                1L, "fresh", "RUNNING", 49, null, false, null, 0, 0, null, Map.of(),
                11, 0, 11, 0, 0, 7.79, 7.79, 0, 0, 0, null, null,
                null, null, null, null, null, null, null, TODAY);

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
                null, null, null, null, null, null, null, TODAY);

        assertEquals(AdsReviewRules.PAUSED, AdsReviewRules.decide(m).decision());
    }
}
