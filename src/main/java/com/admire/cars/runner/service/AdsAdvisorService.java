package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsCampaignPlan;
import com.admire.cars.runner.entity.AdsCampaignSnapshot;
import com.admire.cars.runner.entity.AdsDailyStat;
import com.admire.cars.runner.entity.AdsNormalInfo;
import com.admire.cars.runner.entity.AdsNormalPostBack;
import com.admire.cars.runner.entity.AdsSearchTermStat;
import com.admire.cars.runner.repository.AdsCampaignPlanRepository;
import com.admire.cars.runner.repository.AdsCampaignSnapshotRepository;
import com.admire.cars.runner.repository.AdsDailyStatRepository;
import com.admire.cars.runner.repository.AdsNormalInfoRepository;
import com.admire.cars.runner.repository.AdsNormalPostBackRepository;
import com.admire.cars.runner.repository.AdsSearchTermStatRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * 投前方案管理 + 每日复盘 / pre-launch plans and the daily review
 */
@Slf4j
@Service
public class AdsAdvisorService {

    static final int REVIEW_DAYS = 7;
    static final int TERM_CANDIDATE_LIMIT = 15;

    private final AdsCampaignPlanRepository planRepository;
    private final AdsDailyStatRepository dailyStatRepository;
    private final AdsCampaignSnapshotRepository snapshotRepository;
    private final AdsSearchTermStatRepository searchTermRepository;
    private final AdsNormalInfoRepository normalInfoRepository;
    private final AdsNormalPostBackRepository postBackRepository;
    private final PostBackAttributionService attributionService;
    private final AdsPlanEvaluator planEvaluator;

    public AdsAdvisorService(AdsCampaignPlanRepository planRepository,
                             AdsDailyStatRepository dailyStatRepository,
                             AdsCampaignSnapshotRepository snapshotRepository,
                             AdsSearchTermStatRepository searchTermRepository,
                             AdsNormalInfoRepository normalInfoRepository,
                             AdsNormalPostBackRepository postBackRepository,
                             PostBackAttributionService attributionService,
                             AdsPlanEvaluator planEvaluator) {
        this.planRepository = planRepository;
        this.dailyStatRepository = dailyStatRepository;
        this.snapshotRepository = snapshotRepository;
        this.searchTermRepository = searchTermRepository;
        this.normalInfoRepository = normalInfoRepository;
        this.postBackRepository = postBackRepository;
        this.attributionService = attributionService;
        this.planEvaluator = planEvaluator;
    }

    // ---------- plans ----------

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPlans(String adsOwnerParam, Long currentUserId) {
        String owner = attributionService.resolveOwnerForRead(adsOwnerParam, currentUserId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (AdsCampaignPlan plan : planRepository.findByOwner(owner)) {
            result.add(planView(plan));
        }
        return result;
    }

    public Map<String, Object> evaluate(AdsCampaignPlan input) {
        return planEvaluator.evaluate(input == null ? new AdsCampaignPlan() : input);
    }

    // 有 id 更新该方案, 否则按 owner+广告系列名 新建或覆盖 / update by id, else upsert by owner + campaign name
    @Transactional
    public Map<String, Object> savePlan(AdsCampaignPlan input, String adsOwnerParam, Long currentUserId) {
        if (input == null || !StringUtils.hasText(input.getCampaignName())) {
            throw new IllegalArgumentException("campaignName is required");
        }
        String campaignName = input.getCampaignName().trim();
        AdsCampaignPlan plan;
        if (input.getId() != null) {
            plan = planRepository.findById(input.getId())
                    .orElseThrow(() -> new IllegalArgumentException("plan not found: " + input.getId()));
            checkAccess(plan, currentUserId);
        } else {
            String owner = attributionService.resolveOwnerForWrite(adsOwnerParam, currentUserId);
            plan = planRepository.findByOwnerAndCampaign(owner, campaignName).orElseGet(() -> {
                AdsCampaignPlan created = new AdsCampaignPlan();
                created.setAdsOwner(owner);
                return created;
            });
        }
        if (!campaignName.equals(plan.getCampaignName())) {
            Optional<AdsCampaignPlan> clash = planRepository.findByOwnerAndCampaign(plan.getAdsOwner(), campaignName);
            if (clash.isPresent() && !clash.get().getId().equals(plan.getId())) {
                throw new IllegalArgumentException("a plan for campaign " + campaignName + " already exists");
            }
        }
        copyEditable(input, plan, campaignName);
        AdsCampaignPlan saved = planRepository.save(plan);
        log.info("ADS_PLAN_SAVED id={} owner={} campaign={}", saved.getId(), saved.getAdsOwner(), saved.getCampaignName());
        return planView(saved);
    }

    @Transactional
    public void deletePlan(Long id, Long currentUserId) {
        AdsCampaignPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("plan not found: " + id));
        checkAccess(plan, currentUserId);
        planRepository.delete(plan);
        log.info("ADS_PLAN_DELETED id={} owner={} campaign={}", id, plan.getAdsOwner(), plan.getCampaignName());
    }

    private void checkAccess(AdsCampaignPlan plan, Long currentUserId) {
        String scope = attributionService.resolveOwnerForRead(null, currentUserId);
        if (scope != null && !scope.equals(plan.getAdsOwner())) {
            throw new IllegalArgumentException("plan does not belong to the current user");
        }
    }

    private void copyEditable(AdsCampaignPlan from, AdsCampaignPlan to, String campaignName) {
        to.setCampaignName(campaignName);
        to.setBrandName(trim(from.getBrandName()));
        to.setCountry(upper(from.getCountry()));
        to.setCommissionType(StringUtils.hasText(from.getCommissionType())
                ? from.getCommissionType().trim().toUpperCase(Locale.ROOT) : AdsCampaignPlan.COMMISSION_FIXED);
        to.setCommissionValue(from.getCommissionValue());
        to.setAvgOrderValue(from.getAvgOrderValue());
        to.setExpectedApprovalRate(from.getExpectedApprovalRate());
        to.setExpectedCpc(from.getExpectedCpc());
        to.setKeywords(from.getKeywords());
        to.setNegativeKeywords(from.getNegativeKeywords());
        to.setNotes(from.getNotes() == null || from.getNotes().length() <= 1000
                ? from.getNotes() : from.getNotes().substring(0, 1000));
    }

    private Map<String, Object> planView(AdsCampaignPlan plan) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("plan", plan);
        view.put("evaluation", planEvaluator.evaluate(plan));
        return view;
    }

    // ---------- daily review ----------

    @Transactional(readOnly = true)
    public Map<String, Object> review(String adsOwnerParam, Long currentUserId) {
        String owner = attributionService.resolveOwnerForRead(adsOwnerParam, currentUserId);
        LocalDate today = LocalDate.now();
        LocalDate from7d = today.minusDays(REVIEW_DAYS - 1L);

        List<AdsNormalInfo> tasks = owner == null
                ? normalInfoRepository.findAllByOrderByIdDesc()
                : normalInfoRepository.findByAdsOwnerOrderByIdDesc(owner);
        Map<String, AdsCampaignPlan> plans = new HashMap<>();
        planRepository.findByOwner(owner).forEach(plan -> plans.put(ownerKey(plan.getAdsOwner(), plan.getCampaignName()), plan));
        Map<String, AdsCampaignSnapshot> snapshots = new HashMap<>();
        snapshotRepository.findByOwner(owner).forEach(s -> snapshots.put(ownerKey(s.getAdsOwner(), s.getCampaignName()), s));
        Map<String, List<AdsDailyStat>> daily = new HashMap<>();
        dailyStatRepository.findSince(owner, from7d).forEach(stat ->
                daily.computeIfAbsent(ownerKey(stat.getAdsOwner(), stat.getCampaignName()), k -> new ArrayList<>()).add(stat));
        Map<String, List<AdsSearchTermStat>> terms = latestTerms(searchTermRepository.findSince(owner, today.minusDays(3)));
        Map<Long, List<AdsNormalPostBack>> orders = new HashMap<>();
        postBackRepository.findAttributedByOwner(owner).forEach(order ->
                orders.computeIfAbsent(order.getNormalAdsId(), k -> new ArrayList<>()).add(order));
        Map<String, Optional<BigDecimal>> rateCache = new HashMap<>();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (AdsNormalInfo task : tasks) {
            String key = ownerKey(task.getAdsOwner(), task.getCampainName());
            rows.add(reviewTask(task, plans.get(key), snapshots.get(key), daily.getOrDefault(key, List.of()),
                    terms.getOrDefault(key, List.of()), orders.getOrDefault(task.getId(), List.of()),
                    today, from7d, rateCache));
        }
        rows.sort(Comparator.comparingInt((Map<String, Object> row) -> AdsReviewRules.priority((String) row.get("decision")))
                .thenComparing(row -> String.valueOf(row.get("campaignName"))));

        Map<String, Long> summary = new LinkedHashMap<>();
        AdsReviewRules.PRIORITY.forEach(decision -> summary.put(decision, 0L));
        rows.forEach(row -> summary.merge((String) row.get("decision"), 1L, Long::sum));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generatedAt", LocalDateTime.now().withNano(0).toString());
        result.put("today", today.toString());
        result.put("adsOwner", owner);
        result.put("summary", summary);
        result.put("tasks", rows);
        result.put("markdown", reviewMarkdown(today, owner, summary, rows));
        log.info("ADS_REVIEW_BUILT owner={} tasks={} summary={}", owner, rows.size(), summary);
        return result;
    }

    private Map<String, Object> reviewTask(AdsNormalInfo task, AdsCampaignPlan plan, AdsCampaignSnapshot snapshot,
                                           List<AdsDailyStat> daily, List<AdsSearchTermStat> terms,
                                           List<AdsNormalPostBack> orders, LocalDate today, LocalDate from7d,
                                           Map<String, Optional<BigDecimal>> rateCache) {
        long clicks7d = 0;
        long impressions7d = 0;
        double cost7d = 0;
        boolean costConvertible = true;
        LocalDate lastDaily = null;
        Map<String, Long> networkClicks = new TreeMap<>();
        for (AdsDailyStat stat : daily) {
            clicks7d += value(stat.getClicks());
            impressions7d += value(stat.getImpressions());
            Double usd = usd(stat.getCurrency(), stat.getCost(), rateCache);
            if (usd == null) {
                costConvertible = false;
            } else {
                cost7d += usd;
            }
            networkClicks.merge(stat.getNetwork(), value(stat.getClicks()), Long::sum);
            if (lastDaily == null || stat.getStatDate().isAfter(lastDaily)) {
                lastDaily = stat.getStatDate();
            }
        }
        networkClicks.values().removeIf(clicks -> clicks == 0);

        long approved = 0;
        long pending = 0;
        long rejected = 0;
        long orders7d = 0;
        double commissionApproved = 0;
        double commissionPending = 0;
        double commissionTotal = 0;
        double approved7d = 0;
        double pending7d = 0;
        for (AdsNormalPostBack order : orders) {
            double commission = order.getUserCommissionAmount() == null ? 0 : order.getUserCommissionAmount().doubleValue();
            String bucket = statusBucket(order.getStatus());
            LocalDate orderDate = orderDate(order.getOrderTime());
            boolean recent = orderDate != null && !orderDate.isBefore(from7d);
            commissionTotal += commission;
            if (recent) {
                orders7d++;
            }
            switch (bucket) {
                case "APPROVED" -> {
                    approved++;
                    commissionApproved += commission;
                    if (recent) {
                        approved7d += commission;
                    }
                }
                case "REJECTED" -> rejected++;
                default -> {
                    pending++;
                    commissionPending += commission;
                    if (recent) {
                        pending7d += commission;
                    }
                }
            }
        }

        Double planNet = null;
        Double planApproval = null;
        if (plan != null) {
            Double commission = AdsPlanEvaluator.commissionPerOrder(plan);
            planApproval = plan.getExpectedApprovalRate() == null ? null : AdsPlanEvaluator.approvalRate(plan);
            if (commission != null && commission > 0) {
                planNet = commission * AdsPlanEvaluator.approvalRate(plan);
            }
        }
        Double lifetimeCost = usd(task.getCostCurrency(), task.getLastReportCost(), rateCache);
        String currency = snapshot == null ? null : snapshot.getCurrency();
        AdsReviewRules.TaskMetrics metrics = new AdsReviewRules.TaskMetrics(
                task.getId(), task.getCampainName(), task.getStatus(),
                value(task.getLastReportClicks()), lifetimeCost,
                !daily.isEmpty(), lastDaily, clicks7d, impressions7d,
                daily.isEmpty() || !costConvertible ? null : cost7d, networkClicks,
                orders.size(), approved, pending, rejected,
                commissionApproved, commissionPending, commissionTotal,
                orders7d, approved7d, pending7d,
                planNet, planApproval,
                snapshot == null ? null : snapshot.getSearchPartners(),
                snapshot == null ? null : snapshot.getDisplayNetwork(),
                snapshot == null ? null : snapshot.getGeoTargetType(),
                snapshot == null ? null : snapshot.getBiddingStrategy(),
                snapshot == null ? null : usd(currency, snapshot.getCpcCeiling(), rateCache),
                snapshot == null ? null : toDouble(snapshot.getBudgetLostShare()),
                snapshot == null ? null : toDouble(snapshot.getRankLostShare()),
                today);
        AdsReviewRules.Decision decision = AdsReviewRules.decide(metrics);

        List<String> brandTokens = AdsKeywordRules.brandTokens(
                plan == null ? null : plan.getBrandName(), task.getCampainName(), task.getLandingPageUrl());

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("normalAdsId", task.getId());
        row.put("adsOwner", task.getAdsOwner());
        row.put("campaignName", task.getCampainName());
        row.put("country", task.getCampainCountry());
        row.put("platformName", task.getPlatformName());
        row.put("taskStatus", task.getStatus());
        row.put("decision", decision.decision());
        row.put("reasons", decision.reasons());
        row.put("actions", decision.actions());
        row.put("metrics", decision.metrics());
        row.put("hasPlan", plan != null);
        row.put("lastDailyDate", lastDaily == null ? null : lastDaily.toString());
        row.put("settings", settingsView(snapshot));
        row.put("negativeCandidates", negativeCandidates(terms, brandTokens));
        return row;
    }

    private Map<String, Object> settingsView(AdsCampaignSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("searchPartners", snapshot.getSearchPartners());
        view.put("displayNetwork", snapshot.getDisplayNetwork());
        view.put("geoTargetType", snapshot.getGeoTargetType());
        view.put("biddingStrategy", snapshot.getBiddingStrategy());
        view.put("dailyBudget", snapshot.getDailyBudget());
        view.put("cpcCeiling", snapshot.getCpcCeiling());
        view.put("currency", snapshot.getCurrency());
        view.put("searchImpressionShare", snapshot.getSearchImpressionShare());
        view.put("budgetLostShare", snapshot.getBudgetLostShare());
        view.put("rankLostShare", snapshot.getRankLostShare());
        view.put("reportDate", snapshot.getReportDate() == null ? null : snapshot.getReportDate().toString());
        return view;
    }

    // 按花费排序, 标出不会下单的意图词和非品牌词 / costliest non-buying or non-brand terms as negative candidates
    private List<Map<String, Object>> negativeCandidates(List<AdsSearchTermStat> terms, List<String> brandTokens) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<AdsSearchTermStat> sorted = new ArrayList<>(terms);
        sorted.sort(Comparator.comparing((AdsSearchTermStat t) -> t.getCost() == null ? BigDecimal.ZERO : t.getCost()).reversed());
        for (AdsSearchTermStat term : sorted) {
            if (value(term.getClicks()) == 0) {
                continue;
            }
            String reason = null;
            String intent = AdsKeywordRules.findNegativeIntent(term.getSearchTerm());
            if (intent != null) {
                reason = "non-buying intent: " + intent;
            } else if (!brandTokens.isEmpty() && !AdsKeywordRules.containsBrand(term.getSearchTerm(), brandTokens)) {
                reason = "not a brand search";
            }
            if (reason == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("term", term.getSearchTerm());
            item.put("clicks", value(term.getClicks()));
            item.put("impressions", value(term.getImpressions()));
            item.put("cost", term.getCost());
            item.put("reason", reason);
            result.add(item);
            if (result.size() >= TERM_CANDIDATE_LIMIT) {
                break;
            }
        }
        return result;
    }

    // 每个广告系列只取最近一天的搜索词快照 / keep only the latest snapshot per campaign
    private Map<String, List<AdsSearchTermStat>> latestTerms(List<AdsSearchTermStat> rows) {
        Map<String, LocalDate> latest = new HashMap<>();
        for (AdsSearchTermStat row : rows) {
            latest.merge(ownerKey(row.getAdsOwner(), row.getCampaignName()), row.getSnapshotDate(),
                    (a, b) -> a.isAfter(b) ? a : b);
        }
        Map<String, List<AdsSearchTermStat>> result = new HashMap<>();
        for (AdsSearchTermStat row : rows) {
            String key = ownerKey(row.getAdsOwner(), row.getCampaignName());
            if (row.getSnapshotDate().equals(latest.get(key))) {
                result.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
            }
        }
        return result;
    }

    // 拒单词优先判断, 避免 "invalid" 命中 "valid" / check rejection words first so "invalid" is not "valid"
    static String statusBucket(String status) {
        String value = status == null ? "" : status.toLowerCase(Locale.ROOT);
        if (value.matches(".*(reject|refund|cancel|void|declin|revers|invalid|deny|denied).*")) {
            return "REJECTED";
        }
        if (value.matches(".*(approv|confirm|paid|settle|effective|valid|complete|lock).*")) {
            return "APPROVED";
        }
        return "PENDING";
    }

    // orderTime 可能是秒/毫秒时间戳或 yyyy-MM-dd... / epoch seconds, millis, or a date string
    static LocalDate orderDate(String orderTime) {
        if (!StringUtils.hasText(orderTime)) {
            return null;
        }
        String value = orderTime.trim();
        if (value.matches("\\d{9,13}")) {
            long epoch = Long.parseLong(value);
            Instant instant = value.length() >= 13 ? Instant.ofEpochMilli(epoch) : Instant.ofEpochSecond(epoch);
            return instant.atZone(ZoneId.systemDefault()).toLocalDate();
        }
        if (value.length() >= 10) {
            try {
                return LocalDate.parse(value.substring(0, 10).replace('/', '-'));
            } catch (DateTimeParseException e) {
                return null;
            }
        }
        return null;
    }

    private Double usd(String currency, BigDecimal amount, Map<String, Optional<BigDecimal>> rateCache) {
        if (amount == null) {
            return null;
        }
        String code = StringUtils.hasText(currency) ? currency.trim().toUpperCase(Locale.ROOT) : "USD";
        Optional<BigDecimal> rate = rateCache.computeIfAbsent(code,
                c -> Optional.ofNullable(attributionService.toUsd(c, BigDecimal.ONE)));
        return rate.map(r -> amount.multiply(r).doubleValue()).orElse(null);
    }

    // ---------- markdown ----------

    @SuppressWarnings("unchecked")
    private String reviewMarkdown(LocalDate today, String owner, Map<String, Long> summary, List<Map<String, Object>> rows) {
        StringBuilder md = new StringBuilder();
        md.append("# Daily ads review ").append(today).append(owner == null ? " (all owners)" : " (owner " + owner + ")").append('\n');
        md.append("Summary: ");
        summary.forEach((decision, count) -> {
            if (count > 0) {
                md.append(decision).append(' ').append(count).append("  ");
            }
        });
        md.append("\n\nRules: commission counts approved + pending x approval rate; stop line = 3x net commission "
                + "per order with 0 orders; ROAS < 0.5 after the stop line = STOP; 0.5-1 = OPTIMIZE; >= 1 = CONTINUE; "
                + ">= 1.3 with 5+ orders = SCALE. Money in USD.\n\n");
        md.append("| Campaign | Country | Decision | Spend | Spend 7d | Clicks | CPC | Break-even CPC | Orders a/p/r | Eff. commission | ROAS | ROAS 7d |\n");
        md.append("|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (Map<String, Object> row : rows) {
            Map<String, Object> m = (Map<String, Object>) row.get("metrics");
            md.append("| ").append(row.get("campaignName")).append(" | ").append(row.get("country"))
                    .append(" | ").append(row.get("decision"))
                    .append(" | ").append(cell(m.get("lifetimeCostUsd"))).append(" | ").append(cell(m.get("cost7dUsd")))
                    .append(" | ").append(cell(m.get("lifetimeClicks"))).append(" | ").append(cell(m.get("cpc")))
                    .append(" | ").append(cell(m.get("breakEvenCpc")))
                    .append(" | ").append(m.get("approved")).append('/').append(m.get("pending")).append('/').append(m.get("rejected"))
                    .append(" | ").append(cell(m.get("effectiveCommission"))).append(" | ").append(cell(m.get("roas")))
                    .append(" | ").append(cell(m.get("roas7d"))).append(" |\n");
        }
        for (Map<String, Object> row : rows) {
            if (AdsReviewRules.PAUSED.equals(row.get("decision"))) {
                continue;
            }
            md.append("\n## ").append(row.get("campaignName")).append(" (task ").append(row.get("normalAdsId"))
                    .append(") - ").append(row.get("decision")).append('\n');
            ((List<String>) row.get("reasons")).forEach(reason -> md.append("- Why: ").append(reason).append('\n'));
            ((List<String>) row.get("actions")).forEach(action -> md.append("- Do: ").append(action).append('\n'));
            Map<String, Object> m = (Map<String, Object>) row.get("metrics");
            md.append("- Last 7d: impressions ").append(m.get("impressions7d")).append(", clicks ").append(m.get("clicks7d"))
                    .append(", orders ").append(m.get("orders7d")).append(", networks ").append(m.get("networkClicks7d")).append('\n');
            Map<String, Object> settings = (Map<String, Object>) row.get("settings");
            if (settings != null) {
                md.append("- Settings: ").append(settings).append('\n');
            }
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) row.get("negativeCandidates");
            if (!candidates.isEmpty()) {
                md.append("- Negative keyword candidates:\n");
                candidates.forEach(item -> md.append("  - `").append(item.get("term")).append("` clicks ")
                        .append(item.get("clicks")).append(", cost ").append(item.get("cost"))
                        .append(" (").append(item.get("reason")).append(")\n"));
            }
        }
        return md.toString();
    }

    // ---------- helpers ----------

    private static String ownerKey(String owner, String campaign) {
        return (owner == null ? "" : owner) + "\u0000" + (campaign == null ? "" : campaign.trim().toLowerCase(Locale.ROOT));
    }

    private static long value(Long value) {
        return value == null ? 0L : value;
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private static String cell(Object value) {
        if (value == null) {
            return "-";
        }
        if (value instanceof Double number) {
            return String.format(Locale.ROOT, "%.2f", number);
        }
        return String.valueOf(value);
    }

    private static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String upper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }
}
