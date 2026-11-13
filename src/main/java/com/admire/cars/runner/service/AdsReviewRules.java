package com.admire.cars.runner.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 投后复盘规则: 按回本线判断继续/加预算/优化/观察/立即停, 并给出具体操作
 * Daily review rules: decide scale / continue / optimize / watch / stop against the break-even line, with actions
 */
public final class AdsReviewRules {

    public static final String STOP = "STOP";
    public static final String NO_DATA = "NO_DATA";
    public static final String OPTIMIZE = "OPTIMIZE";
    public static final String WATCH = "WATCH";
    public static final String CONTINUE = "CONTINUE";
    public static final String SCALE = "SCALE";
    public static final String PAUSED = "PAUSED";

    // 排序: 需要立刻处理的在前 / ordering: the most urgent first
    public static final List<String> PRIORITY = List.of(STOP, NO_DATA, OPTIMIZE, WATCH, SCALE, CONTINUE, PAUSED);

    static final double STOP_ROAS = 0.5;
    static final double BREAK_EVEN_ROAS = 1.0;
    static final double SCALE_ROAS = 1.3;
    static final long SCALE_MIN_ORDERS = 5;
    static final double MIN_DECIDED_FOR_RATE = 5;
    static final double HIGH_REJECTION = 0.4;
    static final double BAD_NETWORK_SHARE = 0.2;

    private static final Set<String> SMART_BIDDING = Set.of(
            "MAXIMIZE_CONVERSIONS", "MAXIMIZE_CONVERSION_VALUE", "TARGET_CPA", "TARGET_ROAS", "ENHANCED_CPC");
    private static final Set<String> MAX_CLICKS = Set.of("TARGET_SPEND", "MAXIMIZE_CLICKS");

    private AdsReviewRules() {
    }

    public record TaskMetrics(
            Long taskId, String campaignName, String taskStatus,
            long lifetimeClicks, Double lifetimeCostUsd,
            boolean hasDaily, LocalDate lastDailyDate, long clicks7d, long impressions7d, Double cost7dUsd,
            Map<String, Long> networkClicks7d,
            long orders, long approved, long pending, long rejected,
            double commissionApproved, double commissionPending, double commissionTotal,
            long orders7d, double commissionApproved7d, double commissionPending7d,
            Double planNetCommissionPerOrder, Double planApprovalRate,
            Boolean searchPartners, Boolean displayNetwork, String geoTargetType, String biddingStrategy,
            Double cpcCeilingUsd, Double budgetLostShare, Double rankLostShare,
            LocalDate today) {
    }

    public record Decision(String decision, List<String> reasons, List<String> actions, Map<String, Object> metrics) {
    }

    public static Decision decide(TaskMetrics m) {
        List<String> reasons = new ArrayList<>();
        List<String> actions = new ArrayList<>();
        Map<String, Object> metrics = new LinkedHashMap<>();

        double decided = m.approved() + m.rejected();
        double approvalRate = decided >= MIN_DECIDED_FOR_RATE
                ? m.approved() / decided
                : m.planApprovalRate() != null ? m.planApprovalRate() : AdsPlanEvaluator.DEFAULT_APPROVAL_RATE;
        double effective = m.commissionApproved() + m.commissionPending() * approvalRate;
        double effective7d = m.commissionApproved7d() + m.commissionPending7d() * approvalRate;
        Double net = m.planNetCommissionPerOrder() != null
                ? m.planNetCommissionPerOrder()
                : m.orders() > 0 ? m.commissionTotal() / m.orders() * approvalRate : null;
        Double stopSpend = net == null ? null : net * AdsPlanEvaluator.STOP_SPEND_MULTIPLIER;
        Double cost = m.lifetimeCostUsd();
        Double cpc = cost != null && m.lifetimeClicks() > 0 ? cost / m.lifetimeClicks() : null;
        Double roas = cost != null && cost > 0 ? effective / cost : null;
        Double roas7d = m.cost7dUsd() != null && m.cost7dUsd() > 0 ? effective7d / m.cost7dUsd() : null;
        Double breakEvenCpc = m.orders() > 0 && m.lifetimeClicks() > 0
                ? effective / m.lifetimeClicks()
                : net != null ? net * AdsPlanEvaluator.ASSUMED_BRAND_CVR : null;

        metrics.put("lifetimeClicks", m.lifetimeClicks());
        metrics.put("lifetimeCostUsd", r(cost));
        metrics.put("cpc", r(cpc));
        metrics.put("orders", m.orders());
        metrics.put("approved", m.approved());
        metrics.put("pending", m.pending());
        metrics.put("rejected", m.rejected());
        metrics.put("approvalRate", r(approvalRate));
        metrics.put("effectiveCommission", r(effective));
        metrics.put("roas", r(roas));
        metrics.put("epc", m.lifetimeClicks() > 0 ? r(effective / m.lifetimeClicks()) : null);
        metrics.put("breakEvenCpc", r(breakEvenCpc));
        metrics.put("netCommissionPerOrder", r(net));
        metrics.put("stopSpend", r(stopSpend));
        metrics.put("clicks7d", m.clicks7d());
        metrics.put("impressions7d", m.impressions7d());
        metrics.put("cost7dUsd", r(m.cost7dUsd()));
        metrics.put("orders7d", m.orders7d());
        metrics.put("roas7d", r(roas7d));
        metrics.put("networkClicks7d", m.networkClicks7d());

        boolean spentRecently = m.hasDaily() && m.cost7dUsd() != null && m.cost7dUsd() > 0;
        boolean active = "RUNNING".equalsIgnoreCase(m.taskStatus()) || spentRecently;
        if (!active) {
            reasons.add("Task is not running and had no spend in the last 7 days.");
            return new Decision(PAUSED, reasons, actions, metrics);
        }
        if (cost == null) {
            reasons.add("Ad cost is not reported or has no exchange rate, so profit cannot be judged.");
            actions.add("Check that the Google Ads campaign name equals the task name and the latest script is installed.");
            return new Decision(NO_DATA, reasons, actions, metrics);
        }

        String decision;
        if (m.orders() == 0) {
            if (stopSpend == null) {
                decision = WATCH;
                reasons.add("No order yet and no commission info, so the stop line is unknown.");
                actions.add("Add a pre-launch plan with commission per order to get an exact stop line.");
            } else if (cost >= stopSpend) {
                decision = STOP;
                reasons.add("Spent " + money(cost) + " with 0 orders. At break-even about "
                        + String.format(Locale.ROOT, "%.1f", cost / net) + " orders were expected by now, "
                        + "so the chance this campaign breaks even is under 5%.");
            } else {
                decision = WATCH;
                reasons.add("No order yet. " + money(stopSpend - cost) + " left before the stop line "
                        + money(stopSpend) + " (3x net commission per order).");
            }
        } else if (roas != null && roas >= SCALE_ROAS && m.orders() >= SCALE_MIN_ORDERS) {
            decision = SCALE;
            reasons.add("ROAS " + times(roas) + " with " + m.orders() + " orders is clearly profitable.");
            actions.add("Raise the daily budget by 20%-30% and re-check in 3 days.");
        } else if (roas != null && roas >= BREAK_EVEN_ROAS) {
            decision = CONTINUE;
            reasons.add("ROAS " + times(roas) + " is above break-even.");
        } else if (stopSpend != null && cost < stopSpend) {
            decision = WATCH;
            reasons.add("ROAS " + times(roas) + " but spend " + money(cost) + " is below the judging line "
                    + money(stopSpend) + ". Keep collecting data.");
        } else if (roas == null || roas < STOP_ROAS) {
            decision = STOP;
            reasons.add("ROAS " + times(roas) + " is below " + times(STOP_ROAS) + " after enough spend.");
        } else {
            decision = OPTIMIZE;
            reasons.add("ROAS " + times(roas) + " is between " + times(STOP_ROAS) + " and break-even: fix bids and keywords.");
        }

        // 近 7 天明显变差时降级 / downgrade when the last 7 days turn bad
        if ((CONTINUE.equals(decision) || SCALE.equals(decision) || WATCH.equals(decision))
                && stopSpend != null && m.cost7dUsd() != null && m.cost7dUsd() >= stopSpend
                && (roas7d == null || roas7d < STOP_ROAS)) {
            decision = OPTIMIZE;
            reasons.add("Last 7 days: spent " + money(m.cost7dUsd()) + " with ROAS " + times(roas7d)
                    + ", performance has dropped.");
        }

        if (STOP.equals(decision)) {
            actions.add(0, "Pause the Google Ads campaign and the normal task today.");
        }
        if (OPTIMIZE.equals(decision) && breakEvenCpc != null) {
            actions.add("Lower max CPC to " + money(breakEvenCpc * AdsPlanEvaluator.BID_SAFETY)
                    + " and add the negative keyword candidates below.");
        }
        settingActions(m, decision, cpc, breakEvenCpc, approvalRate, decided, actions);
        if (m.planNetCommissionPerOrder() == null) {
            actions.add("Add a pre-launch plan for this campaign so thresholds use the real commission.");
        }
        return new Decision(decision, reasons, actions, metrics);
    }

    private static void settingActions(TaskMetrics m, String decision, Double cpc, Double breakEvenCpc,
                                       double approvalRate, double decided, List<String> actions) {
        if (!m.hasDaily() || m.lastDailyDate() == null || m.lastDailyDate().isBefore(m.today().minusDays(1))) {
            actions.add("Daily data is missing or stale: copy the latest script from the Auto Script page into Google Ads.");
        }
        if (Boolean.TRUE.equals(m.searchPartners())) {
            actions.add("Turn off Google Search Partners in the campaign network settings.");
        }
        if (Boolean.TRUE.equals(m.displayNetwork())) {
            actions.add("Turn off the Display Network in the campaign network settings.");
        }
        if (m.networkClicks7d() != null && m.clicks7d() > 0) {
            m.networkClicks7d().forEach((network, clicks) -> {
                double share = (double) clicks / m.clicks7d();
                if (!"SEARCH".equals(network) && share >= BAD_NETWORK_SHARE) {
                    actions.add(pct(share) + " of last-7-day clicks came from " + network + ": exclude that network.");
                }
            });
        }
        if ("PRESENCE_OR_INTEREST".equalsIgnoreCase(m.geoTargetType())) {
            actions.add("Location option: target people in the country (Presence), not Presence or interest.");
        }
        String bidding = m.biddingStrategy() == null ? "" : m.biddingStrategy().toUpperCase(Locale.ROOT);
        if (SMART_BIDDING.contains(bidding)) {
            actions.add("Bidding " + bidding + " optimizes for conversions Google cannot see. Use Manual CPC or "
                    + "Maximize clicks with a max CPC limit.");
        }
        if (MAX_CLICKS.contains(bidding) && m.cpcCeilingUsd() == null && breakEvenCpc != null) {
            actions.add("Maximize clicks has no max CPC limit: set it to "
                    + money(breakEvenCpc * AdsPlanEvaluator.BID_SAFETY) + ".");
        }
        if (breakEvenCpc != null && m.cpcCeilingUsd() != null && m.cpcCeilingUsd() > breakEvenCpc) {
            actions.add("Max CPC limit " + money(m.cpcCeilingUsd()) + " is above break-even " + money(breakEvenCpc) + ".");
        }
        if (breakEvenCpc != null && cpc != null && cpc > breakEvenCpc && !STOP.equals(decision)) {
            actions.add("Average CPC " + money(cpc) + " is above break-even CPC " + money(breakEvenCpc) + ".");
        }
        if (decided >= MIN_DECIDED_FOR_RATE && 1 - approvalRate >= HIGH_REJECTION) {
            actions.add("Rejection rate " + pct(1 - approvalRate) + ": profit is counted at the "
                    + pct(approvalRate) + " approval rate.");
        }
        if (m.rankLostShare() != null && m.rankLostShare() > 0.5 && !STOP.equals(decision)) {
            actions.add(pct(m.rankLostShare()) + " of impressions lost to ad rank: put the brand in headlines "
                    + "and check keyword-to-ad relevance.");
        }
        if (SCALE.equals(decision) && m.budgetLostShare() != null && m.budgetLostShare() > 0.2) {
            actions.add(pct(m.budgetLostShare()) + " of impressions lost to budget: the budget raise will pay off.");
        }
    }

    public static int priority(String decision) {
        int index = PRIORITY.indexOf(decision);
        return index < 0 ? PRIORITY.size() : index;
    }

    private static Double r(Double value) {
        return value == null ? null : Math.round(value * 10000.0) / 10000.0;
    }

    private static String money(Double value) {
        return value == null ? "-" : String.format(Locale.ROOT, "$%.2f", value);
    }

    private static String times(Double value) {
        return value == null ? "-" : String.format(Locale.ROOT, "%.2fx", value);
    }

    private static String pct(double value) {
        return String.format(Locale.ROOT, "%.0f%%", value * 100);
    }
}
