package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsClickTrack;
import com.admire.cars.runner.entity.AdsNormalInfo;
import com.admire.cars.runner.entity.AdsNormalPostBack;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.AdsClickTrackRepository;
import com.admire.cars.runner.repository.AdsNormalInfoRepository;
import com.admire.cars.runner.repository.AdsNormalPostBackRepository;
import com.admire.cars.runner.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    // 可选的 {adid} 占位符会写成 n<taskId> / optional {adid} placeholder is written as n<taskId>
    private static final Pattern TASK_PATTERN = Pattern.compile("(?:^|[^0-9a-zA-Z])n(\\d+)(?:$|[^0-9a-zA-Z])");

    private final AdsNormalPostBackRepository postBackRepository;
    private final AdsClickTrackRepository clickTrackRepository;
    private final AdsNormalInfoRepository adsNormalInfoRepository;
    private final UserRepository userRepository;

    public PostBackAttributionService(AdsNormalPostBackRepository postBackRepository,
                                      AdsClickTrackRepository clickTrackRepository,
                                      AdsNormalInfoRepository adsNormalInfoRepository,
                                      UserRepository userRepository) {
        this.postBackRepository = postBackRepository;
        this.clickTrackRepository = clickTrackRepository;
        this.adsNormalInfoRepository = adsNormalInfoRepository;
        this.userRepository = userRepository;
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
        applyMatch(postBack);
        postBackRepository.save(postBack);
        log.info("ATTRIBUTION_DONE owner={} orderNo={} clickId={} normalAdsId={} status={} rule={}",
                adsOwner, orderNo, postBack.getClickId(), postBack.getNormalAdsId(),
                postBack.getMatchStatus(), postBack.getMatchRule());
    }

    // 后期补录点击台账后重跑未归因订单 / re-run unmatched orders after click ids are backfilled
    @Transactional
    public int reattribute(String adsOwner, Pageable pageable) {
        Page<AdsNormalPostBack> page = postBackRepository.findReattributable(adsOwner, pageable);
        int matched = 0;
        for (AdsNormalPostBack postBack : page.getContent()) {
            applyMatch(postBack);
            postBackRepository.save(postBack);
            if (MATCHED.equals(postBack.getMatchStatus())) {
                matched++;
            }
        }
        log.info("ATTRIBUTION_RERUN owner={} scanned={} matched={}", adsOwner, page.getNumberOfElements(), matched);
        return matched;
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
        List<AdsNormalInfo> tasks = adsNormalInfoRepository.findByAdsOwnerOrderByIdDesc(adsOwner);

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

        List<Map<String, Object>> rows = new ArrayList<>();
        long totalOrders = 0L;
        long totalIssued = 0L;
        long totalTracked = 0L;
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalCommission = BigDecimal.ZERO;

        for (AdsNormalInfo task : tasks) {
            Object[] clicks = clickAgg.get(task.getId());
            Object[] orders = orderAgg.get(task.getId());
            long issued = clicks == null ? 0L : toLong(clicks[1]);
            long tracked = clicks == null ? 0L : toLong(clicks[2]);
            long orderCount = orders == null ? 0L : toLong(orders[1]);
            BigDecimal amount = orders == null || orders[2] == null ? BigDecimal.ZERO : new BigDecimal(orders[2].toString());
            BigDecimal commission = orders == null || orders[3] == null ? BigDecimal.ZERO : new BigDecimal(orders[3].toString());
            long matchedClickIds = orders == null ? 0L : toLong(orders[4]);
            Long reportedClicks = task.getLastReportClicks();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("normalAdsId", task.getId());
            row.put("campainName", task.getCampainName());
            row.put("campainCountry", task.getCampainCountry());
            row.put("platformName", task.getPlatformName());
            row.put("status", task.getStatus());
            row.put("issuedClickIds", issued);
            row.put("trackedClicks", tracked);
            row.put("reportedClicks", reportedClicks);
            row.put("orderCount", orderCount);
            row.put("matchedClickIds", matchedClickIds);
            row.put("orderAmount", amount);
            row.put("commission", commission);
            row.put("conversionRate", reportedClicks == null || reportedClicks <= 0 || orderCount == 0
                    ? null : BigDecimal.valueOf(orderCount)
                    .divide(BigDecimal.valueOf(reportedClicks), 6, java.math.RoundingMode.HALF_UP));
            rows.add(row);

            totalOrders += orderCount;
            totalIssued += issued;
            totalTracked += tracked;
            totalAmount = totalAmount.add(amount);
            totalCommission = totalCommission.add(commission);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("adsOwner", adsOwner);
        result.put("normalTaskCount", tasks.size());
        result.put("issuedClickIds", totalIssued);
        result.put("trackedClicks", totalTracked);
        result.put("orderCount", totalOrders);
        result.put("unmatchedOrderCount", postBackRepository.countUnmatched(adsOwner));
        result.put("orderAmount", totalAmount);
        result.put("commission", totalCommission);
        result.put("tasks", rows);
        return result;
    }

    // 点击ID台账 / click id ledger
    @Transactional(readOnly = true)
    public List<AdsClickTrack> clicks(String adsOwnerParam, Long normalAdsId, Long currentUserId) {
        String adsOwner = resolveAdsOwner(adsOwnerParam, currentUserId);
        if (normalAdsId != null) {
            return clickTrackRepository.findByAdsOwnerAndNormalAdsIdOrderByIdDesc(adsOwner, normalAdsId);
        }
        return clickTrackRepository.findByAdsOwnerOrderByIdDesc(adsOwner);
    }

    // 某个点击ID有没有下单 / orders attributed to one click id
    @Transactional(readOnly = true)
    public Map<String, Object> clickDetail(String clickId, String adsOwnerParam, Long currentUserId) {
        String adsOwner = resolveAdsOwner(adsOwnerParam, currentUserId);
        AdsClickTrack track = clickTrackRepository.findByClickId(clickId.trim()).orElse(null);
        if (track != null && !adsOwner.equals(track.getAdsOwner())) {
            throw new IllegalArgumentException("Unauthorized: click id belongs to another owner");
        }
        List<AdsNormalPostBack> orders = postBackRepository
                .findByAdsOwnerAndClickIdOrderByIdDesc(adsOwner, clickId.trim());
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
            List<AdsNormalInfo> byId = tasks.stream()
                    .filter(task -> shopId.trim().equals(extractQueryParam(task.getAffiliteUrl(), "a")))
                    .toList();
            if (byId.size() == 1) {
                return byId.get(0).getId();
            }
        }
        String shopName = postBack.getAdvertiserShopName();
        if (StringUtils.hasText(shopName)) {
            String normalized = shopName.trim().toLowerCase();
            List<AdsNormalInfo> byName = tasks.stream()
                    .filter(task -> task.getCampainName() != null
                            && task.getCampainName().trim().toLowerCase().equals(normalized))
                    .toList();
            if (byName.size() == 1) {
                return byName.get(0).getId();
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

    // 供写操作接口解析归属 / owner resolution for write endpoints
    @Transactional(readOnly = true)
    public String resolveOwnerForWrite(String adsOwnerParam, Long currentUserId) {
        return resolveAdsOwner(adsOwnerParam, currentUserId);
    }

    private long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }
}
