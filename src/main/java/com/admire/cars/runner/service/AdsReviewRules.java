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
    // 有转化的新广告满 7 天才按 ROAS 判停; 满 7 天且近 7 天零转化直接停
    // Campaigns with orders are not stopped on ROAS before day 7; from day 7, no order in the last 7 days = STOP
    static final int MIN_JUDGE_DAYS = 7;
    // 订单回传延迟: 最近 N 天的花费订单还没回来, 不参与判停 / postback lag: spend of the last N days is not judged yet
    static final int DEFAULT_REPORT_LAG_DAYS = 3;
    static final int MAX_REPORT_LAG_DAYS = 7;
    // 流量被污染: 非搜索网络点击占比或无效意图词花费占比超过阈值时, 先修流量再判 / polluted traffic is fixed before judging
    static final double POLLUTED_NETWORK_SHARE = 0.3;
    static final double POLLUTED_TERM_COST_SHARE = 0.4;
    // 无佣金信息且零单时, 按天数判停所需的最低已判花费 USD / judged spend needed for a time-based stop without commission info
    static final double NO_PLAN_STOP_SPEND = 15.0;
    // 今天或昨天有花费才算仍在花钱 (Google Ads 账户时区与服务器可能差一天) / spend today or yesterday = still spending
    static final int STILL_SPENDING_DAYS = 2;

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
            LocalDate today, Integer daysRunning,
            int reportLagDays, Double lagCostUsd,
            double commissionApprovedMatured, double commissionPendingMatured,
            Double nonBuyingTermCostShare, LocalDate lastSpendDate) {
    }

    // 方案未填时用默认延迟, 并限制在 0~7 天 / default lag when the plan has none, clamped to 0..7 days
    public static int reportLagDays(Integer planValue) {
        int lag = planValue == null ? DEFAULT_REPORT_LAG_DAYS : planValue;
        return Math.max(0, Math.min(MAX_REPORT_LAG_DAYS, lag));
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
        // 不用三元: double 与可能为 null 的 Double 混用会自动拆箱抛 NPE / no ternary: mixing double with a null Double unboxes and throws
        Double breakEvenCpc = null;
        if (m.orders() > 0 && m.lifetimeClicks() > 0) {
            breakEvenCpc = effective / m.lifetimeClicks();
        } else if (net != null) {
            breakEvenCpc = net * AdsPlanEvaluator.ASSUMED_BRAND_CVR;
        }

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
        metrics.put("daysRunning", m.daysRunning());

        // 任务已暂停且今天/昨天没花费 = 已停; 任务暂停但 Google Ads 仍在花钱时照常复盘并提醒去停
        // paused task with no spend today or yesterday = stopped; a paused task whose campaign still spends is reviewed
        boolean running = "RUNNING".equalsIgnoreCase(m.taskStatus());
        boolean spendingNow = m.lastSpendDate() != null
                && !m.lastSpendDate().isBefore(m.today().minusDays(STILL_SPENDING_DAYS - 1L));
        metrics.put("lastSpendDate", m.lastSpendDate() == null ? null : m.lastSpendDate().toString());
        if (!running && !spendingNow) {
            reasons.add("Task is not running and Google Ads has had no spend since "
                    + (m.lastSpendDate() == null ? "the last 7 days" : m.lastSpendDate()) + ".");
            return new Decision(PAUSED, reasons, actions, metrics);
        }
        if (!running) {
            reasons.add("Task is paused but the Google Ads campaign still spent on " + m.lastSpendDate() + ".");
            actions.add("Pause the campaign in Google Ads too: pausing the normal task does not stop the ad spend.");
        }
        if (cost == null) {
            reasons.add("Ad cost is not reported or has no exchange rate, so profit cannot be judged.");
            actions.add("Check that the Google Ads campaign name equals the task name and the latest script is installed.");
            return new Decision(NO_DATA, reasons, actions, metrics);
        }

        // 已判花费 = 去掉最近 N 天(订单未回齐)的花费, 流量被污染时再按干净流量比例折算
        // judged spend = spend older than the lag window, scaled to the clean share when traffic is polluted
        int lag = m.reportLagDays();
        double lagCost = m.lagCostUsd() == null ? 0 : Math.max(0, Math.min(m.lagCostUsd(), cost));
        double maturedCost = cost - lagCost;
        double junkShare = junkNetworkShare(m);
        double nonBuyingShare = m.nonBuyingTermCostShare() == null ? 0 : m.nonBuyingTermCostShare();
        boolean polluted = junkShare >= POLLUTED_NETWORK_SHARE || nonBuyingShare >= POLLUTED_TERM_COST_SHARE;
        double judgedCost = polluted ? maturedCost * (1 - junkShare) * (1 - nonBuyingShare) : maturedCost;
        double maturedEffective = m.commissionApprovedMatured() + m.commissionPendingMatured() * approvalRate;
        Double judgedRoas = judgedCost > 0 ? maturedEffective / judgedCost : null;
        String judgedNote = judgedNote(lag, lagCost, polluted);
        metrics.put("reportLagDays", lag);
        metrics.put("lagCostUsd", r(lagCost));
        metrics.put("judgedCostUsd", r(judgedCost));
        metrics.put("judgedRoas", r(judgedRoas));
        metrics.put("junkNetworkShare", r(junkShare));
        metrics.put("nonBuyingTermCostShare", r(nonBuyingShare));
        metrics.put("polluted", polluted);

        boolean young = m.daysRunning() != null && m.daysRunning() < MIN_JUDGE_DAYS;
        // 零转化判停要等延迟期过完 / the no-order rule waits until the lag window has passed
        boolean matureForSilence = m.daysRunning() != null && m.daysRunning() >= MIN_JUDGE_DAYS + lag;
        String decision;
        if (m.orders() == 0) {
            if (stopSpend == null) {
                decision = WATCH;
                reasons.add("No order yet and no commission info, so the stop line is unknown.");
                actions.add("Add a pre-launch plan with commission per order to get an exact stop line.");
            } else if (judgedCost >= stopSpend) {
                decision = STOP;
                reasons.add("Spent " + money(judgedCost) + judgedNote + " with 0 orders. At break-even about "
                        + String.format(Locale.ROOT, "%.1f", judgedCost / net) + " orders were expected by now, "
                        + "so the chance this campaign breaks even is under 5%.");
            } else {
                decision = WATCH;
                reasons.add("No order yet. Judged spend " + money(judgedCost) + judgedNote + ", "
                        + money(stopSpend - judgedCost) + " left before the stop line "
                        + money(stopSpend) + " (3x net commission per order).");
            }
        } else if (roas != null && roas >= SCALE_ROAS && m.orders() >= SCALE_MIN_ORDERS) {
            decision = SCALE;
            reasons.add("ROAS " + times(roas) + " with " + m.orders() + " orders is clearly profitable.");
            actions.add("Raise the daily budget by 20%-30% and re-check in 3 days.");
        } else if (roas != null && roas >= BREAK_EVEN_ROAS) {
            decision = CONTINUE;
            reasons.add("ROAS " + times(roas) + " is above break-even.");
        } else if (stopSpend != null && judgedCost < stopSpend) {
            decision = WATCH;
            reasons.add("ROAS " + times(roas) + " but judged spend " + money(judgedCost) + judgedNote
                    + " is below the judging line " + money(stopSpend) + ". Keep collecting data.");
        } else if ((roas == null || roas < STOP_ROAS) && young) {
            decision = WATCH;
            reasons.add("ROAS " + times(roas) + " is low, but the campaign has orders and is only on day "
                    + m.daysRunning() + ". Judge ROAS on day " + MIN_JUDGE_DAYS + ".");
        } else if (roas == null || roas < STOP_ROAS) {
            if (judgedRoas == null) {
                decision = WATCH;
                reasons.add("ROAS " + times(roas) + " is low, but there is no judged spend yet" + judgedNote
                        + ": orders for the recent spend are still coming in.");
            } else if (judgedRoas < STOP_ROAS) {
                decision = STOP;
                reasons.add("ROAS " + times(roas) + " is below " + times(STOP_ROAS) + " after enough spend (ROAS "
                        + times(judgedRoas) + " on judged spend " + money(judgedCost) + judgedNote + ").");
            } else {
                decision = OPTIMIZE;
                reasons.add("ROAS " + times(roas) + " looks low, but on judged spend " + money(judgedCost) + judgedNote
                        + " it is " + times(judgedRoas) + ": fix bids and keywords instead of stopping.");
            }
        } else {
            decision = OPTIMIZE;
            reasons.add("ROAS " + times(roas) + " is between " + times(STOP_ROAS) + " and break-even: fix bids and keywords.");
        }

        // 近 7 天明显变差时降级 / downgrade when the last 7 days turn bad
        if ((CONTINUE.equals(decision) || SCALE.equals(decision) || WATCH.equals(decision))
                && !young && stopSpend != null && m.cost7dUsd() != null && m.cost7dUsd() >= stopSpend
                && (roas7d == null || roas7d < STOP_ROAS)) {
            decision = OPTIMIZE;
            reasons.add("Last 7 days: spent " + money(m.cost7dUsd()) + " with ROAS " + times(roas7d)
                    + ", performance has dropped.");
        }

        // 满 7 天 + 延迟期且近 7 天零转化: 停. 无佣金信息或流量被污染时, 已判花费还要过线才停
        // day 7 + lag with no order in the last 7 days: stop; without commission info or with polluted
        // traffic the judged spend must also reach the line
        boolean spent7d = m.cost7dUsd() != null ? m.cost7dUsd() > 0 : m.orders() == 0 && cost > 0;
        if (!STOP.equals(decision) && matureForSilence && m.orders7d() == 0 && spent7d) {
            double silenceLine = stopSpend == null ? NO_PLAN_STOP_SPEND : polluted ? stopSpend : 0;
            String silence = "Running for " + m.daysRunning() + " days with no order in the last " + MIN_JUDGE_DAYS
                    + " days" + (m.cost7dUsd() == null ? "" : " (spent " + money(m.cost7dUsd()) + ")");
            if (judgedCost >= silenceLine) {
                decision = STOP;
                reasons.add(silence + ".");
            } else {
                reasons.add(silence + ", but judged spend " + money(judgedCost) + judgedNote + " is below "
                        + money(silenceLine) + (stopSpend == null ? " (no commission info)" : "")
                        + ". Keep testing.");
            }
        }

        if (polluted) {
            reasons.add("Traffic is polluted: " + pct(junkShare) + " of last-7-day clicks came from non-search "
                    + "networks and " + pct(nonBuyingShare) + " of search term spend had non-buying intent, "
                    + "so only the clean share of spend is judged.");
            if (WATCH.equals(decision)) {
                decision = OPTIMIZE;
            }
            if (!STOP.equals(decision)) {
                actions.add(0, "Fix traffic first: turn off Display and Search Partners and add the non-buying "
                        + "negatives below, then re-judge on clean traffic.");
            }
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

    private static double junkNetworkShare(TaskMetrics m) {
        if (m.networkClicks7d() == null || m.clicks7d() <= 0) {
            return 0;
        }
        long junk = m.networkClicks7d().entrySet().stream()
                .filter(e -> !"SEARCH".equals(e.getKey()))
                .mapToLong(e -> e.getValue() == null ? 0 : e.getValue())
                .sum();
        return Math.min(1.0, (double) junk / m.clicks7d());
    }

    private static String judgedNote(int lag, double lagCost, boolean polluted) {
        List<String> parts = new ArrayList<>();
        if (lagCost > 0) {
            parts.add("excluding " + money(lagCost) + " from the last " + lag + " days still awaiting orders");
        }
        if (polluted) {
            parts.add("clean traffic only");
        }
        return parts.isEmpty() ? "" : " (" + String.join(", ", parts) + ")";
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
