package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsCampaignCost;
import com.admire.cars.runner.entity.AdsClickTrack;
import com.admire.cars.runner.entity.AdsDailyStat;
import com.admire.cars.runner.entity.AdsNormalInfo;
import com.admire.cars.runner.entity.AdsNormalPostBack;
import com.admire.cars.runner.entity.ExchangeRate;
import com.admire.cars.runner.entity.ShiftLink;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.AdsCampaignCostRepository;
import com.admire.cars.runner.repository.AdsClickTrackRepository;
import com.admire.cars.runner.repository.AdsDailyStatRepository;
import com.admire.cars.runner.repository.AdsNormalInfoRepository;
import com.admire.cars.runner.repository.AdsNormalPostBackRepository;
import com.admire.cars.runner.repository.ExchangeRateRepository;
import com.admire.cars.runner.repository.ShiftLinkRepository;
import com.admire.cars.runner.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 转化归因与统计: 把 postback 的 sub_id 匹配到点击ID, 再对应到后台上架的 normaltask
 * Postback attribution and stats: match sub_id to a click id, then to the listed normal task
 */
@Slf4j
@Service
public class PostBackAttributionService {

    public static final String MATCHED = "MATCHED";
    public static final String PARTIAL = "PARTIAL";
    public static final String UNMATCHED = "UNMATCHED";
    // 联盟佣金按 USD 计 / affiliate commission is in USD
    private static final String USD = "USD";

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    // 可选的 {adid} 占位符会写成 n<taskId> / optional {adid} placeholder is written as n<taskId>
    private static final Pattern TASK_PATTERN = Pattern.compile("(?:^|[^0-9a-zA-Z])n(\\d+)(?:$|[^0-9a-zA-Z])");

    private final AdsNormalPostBackRepository postBackRepository;
    private final AdsClickTrackRepository clickTrackRepository;
    private final AdsNormalInfoRepository adsNormalInfoRepository;
    private final ShiftLinkRepository shiftLinkRepository;
    private final UserRepository userRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final AdsDailyStatRepository adsDailyStatRepository;
    private final AdsCampaignCostRepository campaignCostRepository;

    public PostBackAttributionService(AdsNormalPostBackRepository postBackRepository,
                                      AdsClickTrackRepository clickTrackRepository,
                                      AdsNormalInfoRepository adsNormalInfoRepository,
                                      ShiftLinkRepository shiftLinkRepository,
                                      UserRepository userRepository,
                                      ExchangeRateRepository exchangeRateRepository,
                                      AdsDailyStatRepository adsDailyStatRepository,
                                      AdsCampaignCostRepository campaignCostRepository) {
        this.postBackRepository = postBackRepository;
        this.clickTrackRepository = clickTrackRepository;
        this.adsNormalInfoRepository = adsNormalInfoRepository;
        this.shiftLinkRepository = shiftLinkRepository;
        this.userRepository = userRepository;
        this.exchangeRateRepository = exchangeRateRepository;
        this.adsDailyStatRepository = adsDailyStatRepository;
        this.campaignCostRepository = campaignCostRepository;
    }

    // 回调落库后立即归因 / called right after a postback is saved
    @Transactional
    public void attribute(String adsOwner, String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            log.warn("ATTRIBUTION_SKIP_NO_ORDER owner={}", adsOwner);
            return;
        }
        Optional<AdsNormalPostBack> found = postBackRepository.findByAdsOwnerAndOrderNo(adsOwner, orderNo.trim());
        if (found.isEmpty()) {
            log.warn("ATTRIBUTION_SKIP_NOT_FOUND owner={} orderNo={}", adsOwner, orderNo);
            return;
        }
        AdsNormalPostBack postBack = found.get();
        // 已精确归因(含人工绑定)的订单, 状态变更回调不再重算 / status callbacks must not redo a confirmed match
        if (MATCHED.equals(postBack.getMatchStatus())) {
            log.info("ATTRIBUTION_KEEP owner={} orderNo={} clickId={} rule={}",
                    adsOwner, orderNo, postBack.getClickId(), postBack.getMatchRule());
            return;
        }
        applyMatch(postBack);
        postBackRepository.save(postBack);
        log.info("ATTRIBUTION_DONE owner={} orderNo={} clickId={} normalAdsId={} status={} rule={}",
                adsOwner, orderNo, postBack.getClickId(), postBack.getNormalAdsId(),
                postBack.getMatchStatus(), postBack.getMatchRule());
    }

    public record RerunResult(int scanned, int matched, int partial, int changed) {
    }

    // 后期补录点击台账后重跑未归因订单 / re-run unmatched orders after click ids are backfilled
    @Transactional
    public RerunResult reattribute(String adsOwner, Pageable pageable) {
        Page<AdsNormalPostBack> page = postBackRepository.findReattributable(adsOwner, pageable);
        int matched = 0;
        int partial = 0;
        int changed = 0;
        for (AdsNormalPostBack postBack : page.getContent()) {
            String before = postBack.getMatchStatus();
            applyMatch(postBack);
            postBackRepository.save(postBack);
            if (MATCHED.equals(postBack.getMatchStatus())) {
                matched++;
            } else if (PARTIAL.equals(postBack.getMatchStatus())) {
                partial++;
            }
            if (!java.util.Objects.equals(before, postBack.getMatchStatus())) {
                changed++;
                log.info("ATTRIBUTION_RERUN_CHANGED orderNo={} from={} to={} rule={}",
                        postBack.getOrderNo(), before, postBack.getMatchStatus(), postBack.getMatchRule());
            }
        }
        log.info("ATTRIBUTION_RERUN owner={} scanned={} matched={} partial={} changed={}",
                adsOwner, page.getNumberOfElements(), matched, partial, changed);
        return new RerunResult(page.getNumberOfElements(), matched, partial, changed);
    }

    // 平台未回传 clickId 时人工绑定 / manual binding when the network does not echo our click id
    @Transactional
    public void bind(String adsOwner, String orderNo, String clickId) {
        AdsNormalPostBack postBack = postBackRepository.findByAdsOwnerAndOrderNo(adsOwner, orderNo)
                .orElseThrow(() -> new IllegalArgumentException("ADS_NORMAL_POST_BACK not found: " + orderNo));
        AdsClickTrack track = clickTrackRepository.findByClickId(clickId)
                .orElseThrow(() -> new IllegalArgumentException("ADS_CLICK_TRACK not found: " + clickId));
        postBack.setClickId(track.getClickId());
        postBack.setNormalAdsId(track.getNormalAdsId());
        postBack.setShiftLinkId(track.getShiftLinkId());
        postBack.setMatchStatus(MATCHED);
        postBack.setMatchRule("MANUAL_BIND");
        postBack.setAttributedAt(LocalDateTime.now());
        postBackRepository.save(postBack);
        log.info("ATTRIBUTION_MANUAL owner={} orderNo={} clickId={} normalAdsId={}",
                adsOwner, orderNo, track.getClickId(), track.getNormalAdsId());
    }

    // 按后台上架的 normaltask 汇总点击与下单 / per-normal-task click and order summary
    @Transactional(readOnly = true)
    public Map<String, Object> stats(String adsOwnerParam, Long currentUserId) {
        String adsOwner = resolveAdsOwner(adsOwnerParam, currentUserId);
        List<AdsNormalInfo> tasks = adsOwner == null
                ? adsNormalInfoRepository.findAllByOrderByIdDesc()
                : adsNormalInfoRepository.findByAdsOwnerOrderByIdDesc(adsOwner);

        Map<Long, Object[]> clickAgg = new HashMap<>();
        for (Object[] row : clickTrackRepository.aggregateByOwner(adsOwner)) {
            if (row[0] != null) {
                clickAgg.put(((Number) row[0]).longValue(), row);
            }
        }
        Map<Long, Object[]> orderAgg = new HashMap<>();
        for (Object[] row : postBackRepository.aggregateByOwner(adsOwner)) {
            if (row[0] != null) {
                orderAgg.put(((Number) row[0]).longValue(), row);
            }
        }

        Map<Long, Map<String, Long>> statusByTask = new HashMap<>();
        for (Object[] row : postBackRepository.countByTaskAndStatus(adsOwner)) {
            if (row[0] != null) {
                statusByTask.computeIfAbsent(((Number) row[0]).longValue(), key -> new HashMap<>())
                        .merge(statusLabel(row[1]), toLong(row[2]), Long::sum);
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        long totalOrders = 0L;
        long totalIssued = 0L;
        long totalTracked = 0L;
        long totalReported = 0L;
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalCommission = BigDecimal.ZERO;
        BigDecimal totalCostUsd = BigDecimal.ZERO;
        int unconvertedCostTasks = 0;
        // 花费取自按 campaign 的台账, 删任务/删广告不清零; 同名 campaign 的历史花费合并到任务上
        // Cost comes from the per-campaign ledger, so deleting the task or the campaign keeps history
        Map<String, List<AdsCampaignCost>> ledgerByCampaign = AdsCampaignCostService.groupByCampaign(
                adsOwner == null ? campaignCostRepository.findAll() : campaignCostRepository.findByAdsOwner(adsOwner));
        Set<String> taskCampaignKeys = new HashSet<>();
        Map<String, Optional<BigDecimal>> rateCache = new HashMap<>();

        for (AdsNormalInfo task : tasks) {
            Object[] clicks = clickAgg.get(task.getId());
            Object[] orders = orderAgg.get(task.getId());
            long issued = clicks == null ? 0L : toLong(clicks[1]);
            long tracked = clicks == null ? 0L : toLong(clicks[2]);
            long orderCount = orders == null ? 0L : toLong(orders[1]);
            BigDecimal amount = orders == null || orders[2] == null ? BigDecimal.ZERO : new BigDecimal(orders[2].toString());
            BigDecimal commission = orders == null || orders[3] == null ? BigDecimal.ZERO : new BigDecimal(orders[3].toString());
            long matchedClickIds = orders == null ? 0L : toLong(orders[4]);
            long refundedCount = orders == null || orders.length < 6 || orders[5] == null ? 0L : toLong(orders[5]);
            Long reportedClicks = task.getLastReportClicks();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("normalAdsId", task.getId());
            row.put("campainName", task.getCampainName());
            row.put("advertiserId", firstQueryParam(task.getAffiliteUrl(), "ad", "a", "advertiserid"));
            row.put("campainCountry", task.getCampainCountry());
            row.put("platformName", task.getPlatformName());
            row.put("status", task.getStatus());
            row.put("issuedClickIds", issued);
            row.put("trackedClicks", tracked);
            row.put("reportedClicks", reportedClicks);
            row.put("orderCount", orderCount);
            row.put("orderStatusCounts", sortByCountDesc(statusByTask.get(task.getId())));
            row.put("refundedOrderCount", refundedCount);
            row.put("matchedClickIds", matchedClickIds);
            row.put("orderAmount", amount);
            row.put("commission", commission);
            row.put("conversionRate", reportedClicks == null || reportedClicks <= 0 || orderCount == 0
                    ? null : BigDecimal.valueOf(orderCount)
                    .divide(BigDecimal.valueOf(reportedClicks), 6, java.math.RoundingMode.HALF_UP));

            // 广告花费按当日汇率折算成 USD, 与佣金(USD)同口径计算投入产出
            // Convert ad cost to USD with today's rate so it is comparable with USD commission
            String costKey = AdsCampaignCostService.matchKey(task.getAdsOwner(), task.getCampainName());
            taskCampaignKeys.add(costKey);
            List<AdsCampaignCost> ledger = ledgerByCampaign.get(costKey);
            boolean fromLedger = ledger != null && !ledger.isEmpty();
            TaskCost cost = fromLedger ? ledgerCost(ledger, rateCache) : taskFieldCost(task, rateCache);
            BigDecimal adCostUsd = cost.usd();
            row.put("adCost", cost.amount());
            row.put("costCurrency", cost.currency());
            row.put("adCostUsd", adCostUsd);
            row.put("adCostCampaigns", fromLedger ? ledger.size() : 0);
            row.put("profitUsd", adCostUsd == null ? null : commission.subtract(adCostUsd));
            row.put("roas", ratio(commission, adCostUsd));
            rows.add(row);

            // 台账里的花费在循环后统一累计, 避免同名任务重复计算 / ledger cost is summed once after the loop
            if (!fromLedger) {
                if (adCostUsd != null) {
                    totalCostUsd = totalCostUsd.add(adCostUsd);
                } else if (cost.amount() != null && cost.amount().signum() > 0) {
                    unconvertedCostTasks++;
                }
            }

            totalOrders += orderCount;
            totalIssued += issued;
            totalTracked += tracked;
            totalReported += reportedClicks == null ? 0L : reportedClicks;
            totalAmount = totalAmount.add(amount);
            totalCommission = totalCommission.add(commission);
        }

        // 台账花费: 当前任务对应的 + 曾绑定过 Normal 任务但任务已删除的; 从未对应过 Normal 任务的(如 Matrix)不计入
        // Ledger totals: campaigns of current tasks plus campaigns whose normal task was deleted; never-bound ones are excluded
        BigDecimal deletedTaskCostUsd = BigDecimal.ZERO;
        int deletedTaskCampaigns = 0;
        for (Map.Entry<String, List<AdsCampaignCost>> entry : ledgerByCampaign.entrySet()) {
            boolean hasTask = taskCampaignKeys.contains(entry.getKey());
            boolean counted = false;
            for (AdsCampaignCost ledgerRow : entry.getValue()) {
                if (!hasTask && ledgerRow.getNormalAdsId() == null) {
                    continue;
                }
                BigDecimal usd = toUsdCached(ledgerRow.getCostCurrency(), ledgerRow.getLifetimeCost(), rateCache);
                if (usd == null) {
                    if (ledgerRow.getLifetimeCost() != null && ledgerRow.getLifetimeCost().signum() > 0) {
                        unconvertedCostTasks++;
                        log.warn("ledger cost not converted, no exchange rate owner={} campaign={} currency={} cost={}",
                                ledgerRow.getAdsOwner(), ledgerRow.getCampaignName(), ledgerRow.getCostCurrency(),
                                ledgerRow.getLifetimeCost());
                    }
                    continue;
                }
                totalCostUsd = totalCostUsd.add(usd);
                if (!hasTask) {
                    deletedTaskCostUsd = deletedTaskCostUsd.add(usd);
                    counted = true;
                }
            }
            if (counted) {
                deletedTaskCampaigns++;
            }
        }
        log.info("AD_COST_TOTALS owner={} tasks={} ledgerCampaigns={} totalCostUsd={} deletedTaskCostUsd={} deletedTaskCampaigns={}",
                adsOwner, tasks.size(), ledgerByCampaign.size(), totalCostUsd, deletedTaskCostUsd, deletedTaskCampaigns);

        // 全局口径含未归因订单, 不能只累加各任务 / include unmatched orders in global totals
        List<Object[]> totals = postBackRepository.aggregateTotal(adsOwner);
        long totalRefunded = 0L;
        if (!totals.isEmpty() && totals.get(0) != null) {
            Object[] total = totals.get(0);
            totalOrders = toLong(total[0]);
            totalAmount = total[1] == null ? BigDecimal.ZERO : new BigDecimal(total[1].toString());
            totalCommission = total[2] == null ? BigDecimal.ZERO : new BigDecimal(total[2].toString());
            totalRefunded = total.length < 4 || total[3] == null ? 0L : toLong(total[3]);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("adsOwner", adsOwner);
        result.put("normalTaskCount", tasks.size());
        result.put("issuedClickIds", totalIssued);
        result.put("trackedClicks", totalTracked);
        result.put("reportedClicks", totalReported);
        result.put("orderCount", totalOrders);
        Map<String, Long> totalStatus = new HashMap<>();
        for (Object[] row : postBackRepository.countByStatus(adsOwner)) {
            totalStatus.merge(statusLabel(row[0]), toLong(row[1]), Long::sum);
        }
        result.put("orderStatusCounts", sortByCountDesc(totalStatus));
        result.put("refundedOrderCount", totalRefunded);
        result.put("unmatchedOrderCount", postBackRepository.countUnmatched(adsOwner));
        result.put("orderAmount", totalAmount);
        result.put("commission", totalCommission);
        // 总投入产出: 佣金口径含未归因订单, 花费含已删除任务的历史投入
        // Totals: commission includes unmatched orders, cost includes the history of deleted tasks
        result.put("adCostUsd", totalCostUsd);
        result.put("deletedTaskAdCostUsd", deletedTaskCostUsd);
        result.put("deletedTaskCampaigns", deletedTaskCampaigns);
        result.put("profitUsd", totalCommission.subtract(totalCostUsd));
        result.put("roas", ratio(totalCommission, totalCostUsd));
        result.put("unconvertedCostTasks", unconvertedCostTasks);
        result.put("usdCnyRate", latestUsdCnyRate());
        result.put("tasks", rows);

        List<Map<String, Object>> unmatchedByAdvertiser = new ArrayList<>();
        for (Object[] row : postBackRepository.findUnmatchedGroupedByAdvertiser(adsOwner)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("advertiserShopName", row[0]);
            item.put("advertiserShopId", row[1]);
            item.put("orderCount", toLong(row[2]));
            item.put("orderAmount", row[3] == null ? BigDecimal.ZERO : new BigDecimal(row[3].toString()));
            item.put("commission", row[4] == null ? BigDecimal.ZERO : new BigDecimal(row[4].toString()));
            unmatchedByAdvertiser.add(item);
        }
        result.put("unmatchedByAdvertiser", unmatchedByAdvertiser);

        List<Map<String, Object>> byPlatform = new ArrayList<>();
        java.util.Set<String> platformNames = new java.util.TreeSet<>();
        for (Object[] row : postBackRepository.aggregateByPlatform(adsOwner)) {
            Map<String, Object> item = new LinkedHashMap<>();
            String platform = row[0] == null ? "Unknown" : row[0].toString();
            item.put("platform", platform);
            item.put("orderCount", toLong(row[1]));
            item.put("orderAmount", row[2] == null ? BigDecimal.ZERO : new BigDecimal(row[2].toString()));
            item.put("commission", row[3] == null ? BigDecimal.ZERO : new BigDecimal(row[3].toString()));
            byPlatform.add(item);
            platformNames.add(platform);
        }
        result.put("byPlatform", byPlatform);
        result.put("platforms", new ArrayList<>(platformNames));

        // 按天按 offer 明细: 投放了哪些 offer, 每个 offer 的点击/消耗/cashback, 最近 7 天
        // Per-day per-offer rows: which offers ran with clicks/cost/cashback each day, last 7 days
        result.put("daily", buildDailyRows(adsOwner, tasks, 7));

        // 归因链路自检, 直接定位卡在哪一环 / attribution health check
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("clickTrackCount", clickTrackRepository.countByOwner(adsOwner));
        health.put("orderCount", totalOrders);
        health.put("withSubIdCount", postBackRepository.countWithSubId(adsOwner));
        health.put("matchedCount", postBackRepository.countMatched(adsOwner));
        health.put("recentSubIds", postBackRepository.findRecentSubIds(adsOwner, PageRequest.of(0, 5)));
        result.put("attributionHealth", health);
        return result;
    }

    // 按天按 offer 明细: 广告数据来自脚本日快照, 订单按归一化日期+任务分组, 合并成每天每个 offer 一行
    // Per-day per-offer rows: ad metrics from script snapshots, orders grouped by normalized date and task
    // 未归因订单单独一行, 不混进任何 offer / unmatched orders stay in their own row
    private Map<String, Object> buildDailyRows(String adsOwner, List<AdsNormalInfo> tasks, int days) {
        LocalDate dailyFrom = LocalDate.now().minusDays(days - 1L);
        // 任务索引: campaignName -> task, id -> task / campaign and id indexed tasks
        Map<String, AdsNormalInfo> taskByCampaign = new HashMap<>();
        Map<Long, AdsNormalInfo> taskById = new HashMap<>();
        for (AdsNormalInfo task : tasks) {
            taskByCampaign.put(task.getCampainName(), task);
            taskById.put(task.getId(), task);
        }
        // 广告数据: day -> campaignName -> [0]=impressions, [1]=clicks / ad metrics per day and campaign
        Map<LocalDate, Map<String, long[]>> adByDay = new TreeMap<>();
        // 折算后的 USD 花费: day -> campaignName -> cost / USD cost per day and campaign
        Map<LocalDate, Map<String, BigDecimal>> costByDay = new TreeMap<>();
        long unconvertedCostRows = 0L;
        for (AdsDailyStat stat : adsDailyStatRepository.findSince(adsOwner, dailyFrom)) {
            long[] ad = adByDay.computeIfAbsent(stat.getStatDate(), k -> new HashMap<>())
                    .computeIfAbsent(stat.getCampaignName(), k -> new long[2]);
            ad[0] += stat.getImpressions() == null ? 0L : stat.getImpressions();
            ad[1] += stat.getClicks() == null ? 0L : stat.getClicks();
            if (stat.getCost() == null || stat.getCost().signum() == 0) {
                continue;
            }
            String currency = StringUtils.hasText(stat.getCurrency())
                    ? stat.getCurrency().trim().toUpperCase(java.util.Locale.ROOT) : USD;
            BigDecimal rate = USD.equals(currency) ? BigDecimal.ONE : usdRateOf(currency);
            if (rate == null) {
                unconvertedCostRows++;
                continue;
            }
            costByDay.computeIfAbsent(stat.getStatDate(), k -> new HashMap<>())
                    .merge(stat.getCampaignName(), stat.getCost().multiply(rate), BigDecimal::add);
        }
        // 订单: day -> taskId(-1 表示未归因) -> [0]=count, [1]=amount, [2]=commission
        // orders per day and task id (-1 = unmatched)
        Map<LocalDate, Map<Long, Object[]>> ordersByDay = new TreeMap<>();
        long skippedOrders = 0L;
        String skippedOrderTimeSample = null;
        for (AdsNormalPostBack p : postBackRepository.findByAdsOwner(adsOwner)) {
            LocalDate day = AdsAdvisorService.orderDate(p.getOrderTime());
            if (day == null || day.isBefore(dailyFrom)) {
                if (day == null) {
                    skippedOrders++;
                    if (skippedOrderTimeSample == null) {
                        skippedOrderTimeSample = p.getOrderTime();
                    }
                }
                continue;
            }
            Long taskId = p.getNormalAdsId();
            Object[] agg = ordersByDay.computeIfAbsent(day, k -> new HashMap<>())
                    .computeIfAbsent(taskId == null ? -1L : taskId, k -> new Object[]{0L, BigDecimal.ZERO, BigDecimal.ZERO});
            agg[0] = toLong(agg[0]) + 1;
            agg[1] = ((BigDecimal) agg[1]).add(p.getOrderAmount() == null ? BigDecimal.ZERO : p.getOrderAmount());
            agg[2] = ((BigDecimal) agg[2]).add(p.getUserCommissionAmount() == null
                    ? BigDecimal.ZERO : p.getUserCommissionAmount());
        }
        // 合并成每天每个 offer 一行 / merge into one row per offer per day
        TreeSet<LocalDate> allDays = new TreeSet<>();
        allDays.addAll(adByDay.keySet());
        allDays.addAll(ordersByDay.keySet());
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> dayList = new ArrayList<>();
        for (LocalDate day : allDays) {
            dayList.add(day.toString());
            // 行键: t<taskId> 归到任务, c<campaignName> 只有广告数据, u 未归因订单
            // row keys: t<id> matched to a task, c<campaign> ad-only, u unmatched orders
            Map<String, Map<String, Object>> rowsOfDay = new LinkedHashMap<>();
            for (Map.Entry<String, long[]> entry : adByDay.getOrDefault(day, Map.of()).entrySet()) {
                AdsNormalInfo task = taskByCampaign.get(entry.getKey());
                String key = task != null ? "t" + task.getId() : "c" + entry.getKey();
                Map<String, Object> row = rowsOfDay.computeIfAbsent(key, k -> new LinkedHashMap<>());
                row.put("date", day.toString());
                row.put("campaignName", entry.getKey());
                if (task != null) {
                    row.put("normalAdsId", task.getId());
                }
                row.put("impressions", entry.getValue()[0]);
                row.put("adClicks", entry.getValue()[1]);
                row.put("adCostUsd", costByDay.getOrDefault(day, Map.of())
                        .getOrDefault(entry.getKey(), BigDecimal.ZERO)
                        .setScale(4, java.math.RoundingMode.HALF_UP));
                row.put("orderCount", 0L);
                row.put("orderAmount", BigDecimal.ZERO);
                row.put("commission", BigDecimal.ZERO);
            }
            for (Map.Entry<Long, Object[]> entry : ordersByDay.getOrDefault(day, Map.of()).entrySet()) {
                Long taskId = entry.getKey();
                AdsNormalInfo task = taskId >= 0 ? taskById.get(taskId) : null;
                String key = task != null ? "t" + task.getId() : (taskId >= 0 ? "t?" + taskId : "u");
                Map<String, Object> row = rowsOfDay.computeIfAbsent(key, k -> {
                    Map<String, Object> fresh = new LinkedHashMap<>();
                    fresh.put("date", day.toString());
                    fresh.put("campaignName", task != null ? task.getCampainName()
                            : (taskId >= 0 ? "(removed task #" + taskId + ")" : "(unmatched orders)"));
                    if (task != null) {
                        fresh.put("normalAdsId", task.getId());
                    }
                    fresh.put("impressions", 0L);
                    fresh.put("adClicks", 0L);
                    fresh.put("adCostUsd", BigDecimal.ZERO);
                    fresh.put("orderCount", 0L);
                    fresh.put("orderAmount", BigDecimal.ZERO);
                    fresh.put("commission", BigDecimal.ZERO);
                    return fresh;
                });
                Object[] agg = entry.getValue();
                row.put("orderCount", toLong(row.get("orderCount")) + toLong(agg[0]));
                row.put("orderAmount", ((BigDecimal) row.get("orderAmount")).add((BigDecimal) agg[1]));
                row.put("commission", ((BigDecimal) row.get("commission")).add((BigDecimal) agg[2]));
            }
            for (Map<String, Object> row : rowsOfDay.values()) {
                BigDecimal adCostUsd = (BigDecimal) row.get("adCostUsd");
                BigDecimal commission = (BigDecimal) row.get("commission");
                row.put("profitUsd", commission.subtract(adCostUsd));
                row.put("roas", ratio(commission, adCostUsd));
                rows.add(row);
            }
        }
        if (unconvertedCostRows > 0) {
            log.warn("daily cost rows skipped for missing exchange rate, owner={}, rows={}", adsOwner, unconvertedCostRows);
        }
        if (skippedOrders > 0) {
            log.warn("daily orders skipped for unparseable order_time, owner={}, count={}, sample={}",
                    adsOwner, skippedOrders, skippedOrderTimeSample);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", dayList);
        result.put("rows", rows);
        return result;
    }

    // Click 区数据: 每个点击ID的账号/平台/品牌 + 命中次数 + 带来的订单数/金额/佣金
    // Click tab: owner/platform/brand plus hit count and attributed orders for each click id
    @Transactional(readOnly = true)
    public Map<String, Object> clickSummary(String adsOwnerParam, Long currentUserId, int limit) {
        String adsOwner = resolveAdsOwner(adsOwnerParam, currentUserId);
        PageRequest page = PageRequest.of(0, Math.min(Math.max(limit, 1), 2000));
        // 已启用的链 + 最新生成的链, 去重后按 id 倒序; 最新的链里多数是被跳过、从未启用的链, 点击为 0
        // Activated links plus newest links; most of the newest were skipped and never activated, so they show 0
        Map<Long, AdsClickTrack> merged = new LinkedHashMap<>();
        List<AdsClickTrack> reached = clickTrackRepository.findReached(adsOwner, page);
        for (AdsClickTrack track : reached) {
            merged.put(track.getId(), track);
        }
        for (AdsClickTrack track : clickTrackRepository.findRecent(adsOwner, page)) {
            merged.putIfAbsent(track.getId(), track);
        }
        List<AdsClickTrack> tracks = merged.values().stream()
                .sorted(java.util.Comparator.comparing(AdsClickTrack::getId).reversed())
                .toList();
        log.info("click summary owner={} reached={} total={}", adsOwner, reached.size(), tracks.size());

        // 真实点击数来自 ShiftLink.DISPLAY_TIMES(Google Ads 上报驱动), 不是 /api/click 的命中数
        // real clicks come from ShiftLink.DISPLAY_TIMES driven by Google Ads reports
        List<Long> linkIds = tracks.stream()
                .map(AdsClickTrack::getShiftLinkId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Long> clicksByLinkId = new HashMap<>();
        if (!linkIds.isEmpty()) {
            for (ShiftLink link : shiftLinkRepository.findAllById(linkIds)) {
                clicksByLinkId.put(link.getId(), link.getDisplayTimes() == null ? 0L : link.getDisplayTimes());
            }
        }

        // campaign 累计点击, 与上面的链接级点击并列展示 / campaign lifetime clicks, shown next to link clicks
        List<AdsNormalInfo> tasks = adsOwner == null
                ? adsNormalInfoRepository.findAllByOrderByIdDesc()
                : adsNormalInfoRepository.findByAdsOwnerOrderByIdDesc(adsOwner);
        Map<Long, Long> clicksByTask = new HashMap<>();
        for (AdsNormalInfo task : tasks) {
            clicksByTask.put(task.getId(), task.getLastReportClicks() == null ? 0L : task.getLastReportClicks());
        }

        Map<String, Object[]> orderAgg = new HashMap<>();
        for (Object[] row : postBackRepository.aggregateByClickId(adsOwner)) {
            if (row[0] != null) {
                orderAgg.put(row[0].toString(), row);
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        java.util.Set<String> owners = new java.util.TreeSet<>();
        java.util.Set<String> platforms = new java.util.TreeSet<>();
        java.util.Set<String> brands = new java.util.TreeSet<>();
        for (AdsClickTrack track : tracks) {
            Object[] agg = orderAgg.get(track.getClickId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("clickId", track.getClickId());
            row.put("adsOwner", track.getAdsOwner());
            row.put("platformName", track.getPlatformName());
            row.put("campainName", track.getCampainName());
            row.put("normalAdsId", track.getNormalAdsId());
            row.put("createDate", track.getCreateDate());
            row.put("linkClicks", track.getShiftLinkId() == null
                    ? 0L : clicksByLinkId.getOrDefault(track.getShiftLinkId(), 0L));
            row.put("campaignClicks", track.getNormalAdsId() == null
                    ? 0L : clicksByTask.getOrDefault(track.getNormalAdsId(), 0L));
            row.put("trackedHits", track.getClickCount() == null ? 0L : track.getClickCount());
            row.put("shiftLinkId", track.getShiftLinkId());
            row.put("orderCount", agg == null ? 0L : toLong(agg[1]));
            row.put("orderAmount", agg == null || agg[2] == null ? BigDecimal.ZERO : new BigDecimal(agg[2].toString()));
            row.put("commission", agg == null || agg[3] == null ? BigDecimal.ZERO : new BigDecimal(agg[3].toString()));
            row.put("fullUrl", track.getFullUrl());
            rows.add(row);
            if (StringUtils.hasText(track.getAdsOwner())) owners.add(track.getAdsOwner());
            if (StringUtils.hasText(track.getPlatformName())) platforms.add(track.getPlatformName());
            if (StringUtils.hasText(track.getCampainName())) brands.add(track.getCampainName());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("clicks", rows);
        result.put("reachedLinks", reached.size());
        result.put("campaignClicksTotal", clicksByTask.values().stream().mapToLong(Long::longValue).sum());
        result.put("owners", new ArrayList<>(owners));
        result.put("platforms", new ArrayList<>(platforms));
        result.put("brands", new ArrayList<>(brands));
        return result;
    }

    // 点击ID台账 / click id ledger
    @Transactional(readOnly = true)
    public List<AdsClickTrack> clicks(String adsOwnerParam, Long normalAdsId, Long currentUserId) {
        String adsOwner = resolveAdsOwner(adsOwnerParam, currentUserId);
        if (adsOwner == null) {
            return clickTrackRepository.findRecent(null, PageRequest.of(0, 1000));
        }
        if (normalAdsId != null) {
            return clickTrackRepository.findByAdsOwnerAndNormalAdsIdOrderByIdDesc(adsOwner, normalAdsId);
        }
        return clickTrackRepository.findByAdsOwnerOrderByIdDesc(adsOwner);
    }

    @Transactional(readOnly = true)
    public List<AdsClickTrack> recentClicks(String adsOwner, int limit) {
        return clickTrackRepository.findRecent(adsOwner, PageRequest.of(0, Math.max(limit, 1)));
    }

    @Transactional(readOnly = true)
    public List<AdsNormalPostBack> recentOrders(String adsOwner, int limit) {
        return postBackRepository.findRecent(adsOwner, PageRequest.of(0, Math.max(limit, 1)));
    }

    // 某个点击ID有没有下单 / orders attributed to one click id
    @Transactional(readOnly = true)
    public Map<String, Object> clickDetail(String clickId, String adsOwnerParam, Long currentUserId) {
        String adsOwner = resolveAdsOwner(adsOwnerParam, currentUserId);
        AdsClickTrack track = clickTrackRepository.findByClickId(clickId.trim()).orElse(null);
        if (track != null && adsOwner != null && !adsOwner.equals(track.getAdsOwner())) {
            throw new IllegalArgumentException("Unauthorized: click id belongs to another owner");
        }
        List<AdsNormalPostBack> orders = postBackRepository
                .findByClickIdWithOwner(adsOwner, clickId.trim());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("clickId", clickId.trim());
        result.put("click", track);
        result.put("orderCount", orders.size());
        result.put("orders", orders);
        return result;
    }

    @Transactional(readOnly = true)
    public Page<AdsNormalPostBack> unmatched(String adsOwnerParam, Long currentUserId, Pageable pageable) {
        return postBackRepository.findUnmatched(resolveAdsOwner(adsOwnerParam, currentUserId), pageable);
    }

    private void applyMatch(AdsNormalPostBack postBack) {
        List<String> candidates = Arrays.asList(
                postBack.getSubId(), postBack.getSubId2(), postBack.getSignId(), postBack.getAdvertiserShopId());
        for (String candidate : candidates) {
            if (!StringUtils.hasText(candidate)) {
                continue;
            }
            Optional<AdsClickTrack> exact = clickTrackRepository.findByClickId(candidate.trim());
            if (exact.isPresent()) {
                fillFromTrack(postBack, exact.get(), "EXACT_SUB_ID");
                return;
            }
        }

        String joined = String.join(" ", candidates.stream()
                .filter(StringUtils::hasText).map(String::trim).toList());
        Matcher uuidMatcher = UUID_PATTERN.matcher(joined);
        while (uuidMatcher.find()) {
            Optional<AdsClickTrack> found = clickTrackRepository.findByClickId(uuidMatcher.group());
            if (found.isPresent()) {
                fillFromTrack(postBack, found.get(), "UUID_IN_VALUE");
                return;
            }
        }

        Matcher taskMatcher = TASK_PATTERN.matcher(joined);
        while (taskMatcher.find()) {
            Long taskId = Long.valueOf(taskMatcher.group(1));
            Optional<AdsNormalInfo> task = adsNormalInfoRepository.findById(taskId);
            if (task.isPresent() && task.get().getAdsOwner().equals(postBack.getAdsOwner())) {
                postBack.setNormalAdsId(taskId);
                postBack.setClickId(null);
                postBack.setShiftLinkId(null);
                postBack.setMatchStatus(PARTIAL);
                postBack.setMatchRule("NORMAL_ADS_ID");
                postBack.setAttributedAt(LocalDateTime.now());
                return;
            }
        }

        // 平台未回传 subid 时, 用商家ID/campaign 名兜底到 normaltask(只能到任务级)
        // fall back to advertiser id / campaign name when the network does not echo our subid (task level only)
        Long advertiserTaskId = matchByAdvertiser(postBack);
        if (advertiserTaskId != null) {
            postBack.setNormalAdsId(advertiserTaskId);
            postBack.setClickId(null);
            postBack.setShiftLinkId(null);
            postBack.setMatchStatus(PARTIAL);
            postBack.setMatchRule("ADVERTISER_SHOP_ID");
            postBack.setAttributedAt(LocalDateTime.now());
            return;
        }

        postBack.setMatchStatus(UNMATCHED);
        postBack.setMatchRule("NONE");
        postBack.setAttributedAt(LocalDateTime.now());
    }

    private void fillFromTrack(AdsNormalPostBack postBack, AdsClickTrack track, String rule) {
        postBack.setClickId(track.getClickId());
        postBack.setNormalAdsId(track.getNormalAdsId());
        postBack.setShiftLinkId(track.getShiftLinkId());
        postBack.setMatchStatus(MATCHED);
        postBack.setMatchRule(rule);
        postBack.setAttributedAt(LocalDateTime.now());
    }

    // 商家ID来自 normaltask 的 Affiliate Url 中 a= 参数; 只有唯一命中才采纳, 避免错配
    // advertiser id comes from the a= param of the task's affiliate url; only a unique hit is accepted
    private Long matchByAdvertiser(AdsNormalPostBack postBack) {
        List<AdsNormalInfo> tasks = adsNormalInfoRepository
                .findByAdsOwnerOrderByIdDesc(postBack.getAdsOwner());
        if (tasks == null || tasks.isEmpty()) {
            return null;
        }
        String shopId = postBack.getAdvertiserShopId();
        if (StringUtils.hasText(shopId)) {
            // 联盟链接里商家参数名不统一: ad= / a= / advertiserId=
            // affiliate links use different advertiser param names
            List<AdsNormalInfo> byId = tasks.stream()
                    .filter(task -> shopId.trim().equals(
                            firstQueryParam(task.getAffiliteUrl(), "ad", "a", "advertiserid")))
                    .toList();
            if (byId.size() == 1) {
                return byId.get(0).getId();
            }
        }
        String shopName = normalizeName(postBack.getAdvertiserShopName());
        if (StringUtils.hasText(shopName)) {
            List<AdsNormalInfo> byName = tasks.stream()
                    .filter(task -> isNameMatch(normalizeName(task.getCampainName()), shopName))
                    .toList();
            if (byName.size() == 1) {
                return byName.get(0).getId();
            }
        }
        return null;
    }

    // 商家名常带国家后缀或空格, 归一化后双向包含 / normalize advertiser names with country suffixes
    private String normalizeName(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private boolean isNameMatch(String campaign, String advertiser) {
        if (campaign.isEmpty() || advertiser.isEmpty()) {
            return false;
        }
        if (campaign.equals(advertiser)) {
            return true;
        }
        if (campaign.length() < 4 || advertiser.length() < 4) {
            return false;
        }
        return advertiser.contains(campaign) || campaign.contains(advertiser);
    }

    private String firstQueryParam(String url, String... names) {
        for (String name : names) {
            String value = extractQueryParam(url, name);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String extractQueryParam(String url, String name) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        Matcher matcher = Pattern.compile("[?&]" + Pattern.quote(name) + "=([^&#]*)").matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    // 与 AdsNormalPostBackService.search 保持一致的归属校验 / same ownership rule as postback search
    private String resolveAdsOwner(String adsOwnerParam, Long currentUserId) {
        if (currentUserId != null) {
            User current = userRepository.findById(currentUserId)
                    .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found: " + currentUserId));
            if (!isAdmin(current)) {
                return current.getUserPhoneNumber();
            }
            // admin 未指定 owner 时查全部, 与订单列表 All owners 口径一致
            // admin without an explicit owner sees all owners, same as the order list
            return StringUtils.hasText(adsOwnerParam) ? adsOwnerParam.trim() : null;
        }
        if (!StringUtils.hasText(adsOwnerParam)) {
            throw new IllegalArgumentException("adsOwner is required");
        }
        return adsOwnerParam.trim();
    }

    private boolean isAdmin(User user) {
        return user.getUserRole() != null
                && Arrays.stream(user.getUserRole().split(","))
                .map(String::trim)
                .anyMatch(role -> "admin".equalsIgnoreCase(role));
    }

    // 供跨账号批量操作解析归属, 允许返回 null 表示全部 / all-owner scope for batch operations
    @Transactional(readOnly = true)
    public String resolveOwnerForRead(String adsOwnerParam, Long currentUserId) {
        return resolveAdsOwner(adsOwnerParam, currentUserId);
    }

    // 供写操作接口解析归属 / owner resolution for write endpoints
    @Transactional(readOnly = true)
    public String resolveOwnerForWrite(String adsOwnerParam, Long currentUserId) {
        String owner = resolveAdsOwner(adsOwnerParam, currentUserId);
        if (!StringUtils.hasText(owner)) {
            throw new IllegalArgumentException("adsOwner is required for this operation");
        }
        return owner;
    }

    private long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private String statusLabel(Object status) {
        String value = status == null ? "" : status.toString().trim();
        return value.isEmpty() ? "unknown" : value;
    }

    // 状态按订单数降序 / statuses ordered by order count desc
    private Map<String, Long> sortByCountDesc(Map<String, Long> counts) {
        Map<String, Long> sorted = new LinkedHashMap<>();
        if (counts == null) {
            return sorted;
        }
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return sorted;
    }

    // 任务展示用花费: amount/currency 为原币种(多币种时为空/MIXED), usd 为折算值, 缺汇率为空
    // Task cost view: original amount and currency (null/MIXED when mixed) plus USD, null when a rate is missing
    private record TaskCost(BigDecimal amount, String currency, BigDecimal usd) {
    }

    // 同名 campaign 的全部台账行合计(含已删除/已重建的旧 campaign) / sum all ledger rows of the campaign name
    private TaskCost ledgerCost(List<AdsCampaignCost> ledger, Map<String, Optional<BigDecimal>> rateCache) {
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal usd = BigDecimal.ZERO;
        String currency = null;
        boolean mixed = false;
        boolean usdMissing = false;
        for (AdsCampaignCost row : ledger) {
            BigDecimal cost = row.getLifetimeCost() == null ? BigDecimal.ZERO : row.getLifetimeCost();
            String code = StringUtils.hasText(row.getCostCurrency())
                    ? row.getCostCurrency().trim().toUpperCase(java.util.Locale.ROOT) : null;
            if (currency == null) {
                currency = code;
            } else if (code != null && !code.equals(currency)) {
                mixed = true;
            }
            amount = amount.add(cost);
            BigDecimal converted = toUsdCached(code, cost, rateCache);
            if (converted == null) {
                usdMissing = usdMissing || cost.signum() > 0;
            } else {
                usd = usd.add(converted);
            }
        }
        return new TaskCost(mixed ? null : amount, mixed ? "MIXED" : currency, usdMissing ? null : usd);
    }

    // 台账里没有该 campaign 时退回任务字段(如尚未上报) / fall back to the task field when the ledger has no row yet
    private TaskCost taskFieldCost(AdsNormalInfo task, Map<String, Optional<BigDecimal>> rateCache) {
        BigDecimal cost = task.getLastReportCost();
        if (cost == null) {
            return new TaskCost(null, task.getCostCurrency(), null);
        }
        BigDecimal usd = toUsdCached(task.getCostCurrency(), cost, rateCache);
        if (usd == null) {
            log.warn("ad cost not converted, no exchange rate adsId={} campaign={} currency={} cost={}",
                    task.getId(), task.getCampainName(), task.getCostCurrency(), cost);
        }
        return new TaskCost(cost, task.getCostCurrency(), usd);
    }

    // 带缓存的 USD 折算, 避免每行查一次汇率 / USD conversion with a per-request rate cache
    private BigDecimal toUsdCached(String currencyCode, BigDecimal amount, Map<String, Optional<BigDecimal>> rateCache) {
        if (amount == null) {
            return null;
        }
        String currency = StringUtils.hasText(currencyCode)
                ? currencyCode.trim().toUpperCase(java.util.Locale.ROOT) : USD;
        if (USD.equals(currency)) {
            return amount;
        }
        return rateCache.computeIfAbsent(currency, code -> Optional.ofNullable(usdRateOf(code)))
                .map(rate -> amount.multiply(rate).setScale(4, java.math.RoundingMode.HALF_UP))
                .orElse(null);
    }

    // 任意币种金额折算 USD, 缺汇率返回 null; 空币种按 USD / convert any currency to USD, null when no rate
    @Transactional(readOnly = true)
    public BigDecimal toUsd(String currencyCode, BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        String currency = StringUtils.hasText(currencyCode)
                ? currencyCode.trim().toUpperCase(java.util.Locale.ROOT) : USD;
        if (USD.equals(currency)) {
            return amount;
        }
        BigDecimal rate = usdRateOf(currency);
        if (rate == null) {
            return null;
        }
        return amount.multiply(rate).setScale(4, java.math.RoundingMode.HALF_UP);
    }

    // 1 单位 currency 折合多少 USD / how many USD one unit of currency is worth
    private BigDecimal usdRateOf(String currency) {
        Optional<BigDecimal> direct = exchangeRateRepository
                .findTopByFromCurrencyAndToCurrencyOrderByEffectiveDateDescCreateDateDescIdDesc(currency, USD)
                .map(ExchangeRate::getRate)
                .filter(rate -> rate.signum() > 0);
        if (direct.isPresent()) {
            return direct.get();
        }
        return exchangeRateRepository
                .findTopByFromCurrencyAndToCurrencyOrderByEffectiveDateDescCreateDateDescIdDesc(USD, currency)
                .map(ExchangeRate::getRate)
                .filter(rate -> rate.signum() > 0)
                .map(rate -> BigDecimal.ONE.divide(rate, 12, java.math.RoundingMode.HALF_UP))
                .orElse(null);
    }

    // 页面展示用的最新 USD→CNY 汇率 / latest USD to CNY rate for display
    private BigDecimal latestUsdCnyRate() {
        return exchangeRateRepository
                .findTopByFromCurrencyAndToCurrencyOrderByEffectiveDateDescCreateDateDescIdDesc(USD, "CNY")
                .map(ExchangeRate::getRate)
                .orElse(null);
    }

    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() <= 0) {
            return null;
        }
        return numerator.divide(denominator, 6, java.math.RoundingMode.HALF_UP);
    }
}
