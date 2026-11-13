package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsCampaignSnapshot;
import com.admire.cars.runner.entity.AdsDailyStat;
import com.admire.cars.runner.entity.AdsSearchTermStat;
import com.admire.cars.runner.repository.AdsCampaignSnapshotRepository;
import com.admire.cars.runner.repository.AdsDailyStatRepository;
import com.admire.cars.runner.repository.AdsSearchTermStatRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 接收 Ads 脚本上报的按天数据、广告系列设置和搜索词 / store daily stats, campaign settings and search terms from the script
 */
@Slf4j
@Service
public class AdsDailyReportService {

    // 脚本只回传最近几天, 更早的日期视为异常数据 / the script only sends recent days
    static final int MAX_DAYS_BACK = 60;
    static final int MAX_DAILY_ROWS = 5000;
    static final int MAX_TERM_ROWS = 3000;
    static final int TERM_MAX_LENGTH = 255;

    private final UserService userService;
    private final AdsDailyStatRepository dailyStatRepository;
    private final AdsCampaignSnapshotRepository snapshotRepository;
    private final AdsSearchTermStatRepository searchTermRepository;

    public AdsDailyReportService(UserService userService,
                                 AdsDailyStatRepository dailyStatRepository,
                                 AdsCampaignSnapshotRepository snapshotRepository,
                                 AdsSearchTermStatRepository searchTermRepository) {
        this.userService = userService;
        this.dailyStatRepository = dailyStatRepository;
        this.snapshotRepository = snapshotRepository;
        this.searchTermRepository = searchTermRepository;
    }

    public record CampaignSetting(String name, Boolean googleSearch, Boolean searchPartners, Boolean displayNetwork,
                                  String geoTargetType, String biddingStrategy, BigDecimal dailyBudget,
                                  BigDecimal cpcCeiling, BigDecimal searchImpressionShare,
                                  BigDecimal budgetLostShare, BigDecimal rankLostShare) {
    }

    public record DailyRow(String name, String date, String network, Long impressions, Long clicks, BigDecimal cost) {
    }

    public record SearchTermRow(String name, String term, Long impressions, Long clicks, BigDecimal cost) {
    }

    public record DailyReportRequest(String currency, String reportDate, List<CampaignSetting> campaigns,
                                     List<DailyRow> daily, List<SearchTermRow> searchTerms) {
    }

    public record IngestResult(int campaigns, int dailyRows, int searchTerms) {
    }

    @Transactional
    public IngestResult ingest(String apiKey, DailyReportRequest request) {
        String adsOwner = userService.getEnabledUserByApiKey(apiKey).getUserPhoneNumber();
        if (request == null) {
            return new IngestResult(0, 0, 0);
        }
        LocalDate reportDate = parseDate(request.reportDate());
        if (reportDate == null) {
            reportDate = LocalDate.now();
        }
        String currency = StringUtils.hasText(request.currency())
                ? request.currency().trim().toUpperCase(Locale.ROOT) : null;
        int campaigns = saveSnapshots(adsOwner, currency, reportDate, request.campaigns());
        int daily = saveDaily(adsOwner, currency, reportDate, request.daily());
        int terms = saveSearchTerms(adsOwner, reportDate, request.searchTerms());
        log.info("DAILY_REPORT_SAVED owner={} reportDate={} currency={} campaigns={} dailyRows={} searchTerms={}",
                adsOwner, reportDate, currency, campaigns, daily, terms);
        return new IngestResult(campaigns, daily, terms);
    }

    private int saveSnapshots(String adsOwner, String currency, LocalDate reportDate, List<CampaignSetting> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        Map<String, AdsCampaignSnapshot> existing = new HashMap<>();
        for (AdsCampaignSnapshot snapshot : snapshotRepository.findByOwner(adsOwner)) {
            existing.put(snapshot.getCampaignName(), snapshot);
        }
        List<AdsCampaignSnapshot> toSave = new ArrayList<>();
        for (CampaignSetting row : rows) {
            String name = campaignName(row == null ? null : row.name());
            if (name == null) {
                continue;
            }
            AdsCampaignSnapshot snapshot = existing.computeIfAbsent(name, key -> {
                AdsCampaignSnapshot created = new AdsCampaignSnapshot();
                created.setAdsOwner(adsOwner);
                created.setCampaignName(key);
                return created;
            });
            snapshot.setCurrency(currency);
            snapshot.setGoogleSearch(row.googleSearch());
            snapshot.setSearchPartners(row.searchPartners());
            snapshot.setDisplayNetwork(row.displayNetwork());
            snapshot.setGeoTargetType(truncate(row.geoTargetType(), 32));
            snapshot.setBiddingStrategy(truncate(row.biddingStrategy(), 48));
            snapshot.setDailyBudget(row.dailyBudget());
            snapshot.setCpcCeiling(row.cpcCeiling());
            snapshot.setSearchImpressionShare(share(row.searchImpressionShare()));
            snapshot.setBudgetLostShare(share(row.budgetLostShare()));
            snapshot.setRankLostShare(share(row.rankLostShare()));
            snapshot.setReportDate(reportDate);
            toSave.add(snapshot);
        }
        snapshotRepository.saveAll(toSave);
        return toSave.size();
    }

    // 同一 owner+广告系列+日期+网络 只保留一行, 新值覆盖旧值 / one row per key, newer report overwrites
    private int saveDaily(String adsOwner, String currency, LocalDate reportDate, List<DailyRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        if (rows.size() > MAX_DAILY_ROWS) {
            throw new IllegalArgumentException("too many daily rows: " + rows.size());
        }
        LocalDate earliest = reportDate.minusDays(MAX_DAYS_BACK);
        Map<String, AdsDailyStat> existing = new HashMap<>();
        for (AdsDailyStat stat : dailyStatRepository.findSince(adsOwner, earliest)) {
            existing.put(key(stat.getCampaignName(), stat.getStatDate(), stat.getNetwork()), stat);
        }
        Map<String, AdsDailyStat> toSave = new LinkedHashMap<>();
        for (DailyRow row : rows) {
            String name = campaignName(row == null ? null : row.name());
            LocalDate date = row == null ? null : parseDate(row.date());
            if (name == null || date == null || date.isBefore(earliest) || date.isAfter(reportDate.plusDays(1))) {
                log.warn("DAILY_REPORT_ROW_SKIPPED owner={} row={}", adsOwner, row);
                continue;
            }
            String network = StringUtils.hasText(row.network())
                    ? truncate(row.network().trim().toUpperCase(Locale.ROOT), 32) : "ALL";
            String rowKey = key(name, date, network);
            AdsDailyStat stat = existing.get(rowKey);
            if (stat == null) {
                stat = toSave.get(rowKey);
            }
            if (stat == null) {
                stat = new AdsDailyStat();
                stat.setAdsOwner(adsOwner);
                stat.setCampaignName(name);
                stat.setStatDate(date);
                stat.setNetwork(network);
            }
            stat.setImpressions(nonNegative(row.impressions()));
            stat.setClicks(nonNegative(row.clicks()));
            stat.setCost(row.cost() == null || row.cost().signum() < 0 ? BigDecimal.ZERO : row.cost());
            stat.setCurrency(currency);
            toSave.put(rowKey, stat);
        }
        dailyStatRepository.saveAll(toSave.values());
        return toSave.size();
    }

    // 搜索词是近 7 天汇总, 每次上报替换当天快照 / last-7-day totals, each report replaces today's snapshot
    private int saveSearchTerms(String adsOwner, LocalDate reportDate, List<SearchTermRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        if (rows.size() > MAX_TERM_ROWS) {
            throw new IllegalArgumentException("too many search term rows: " + rows.size());
        }
        Map<String, AdsSearchTermStat> merged = new LinkedHashMap<>();
        Set<String> campaigns = new LinkedHashSet<>();
        for (SearchTermRow row : rows) {
            String name = campaignName(row == null ? null : row.name());
            String term = row == null || !StringUtils.hasText(row.term()) ? null
                    : truncate(row.term().trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "), TERM_MAX_LENGTH);
            if (name == null || term == null) {
                continue;
            }
            campaigns.add(name);
            AdsSearchTermStat stat = merged.computeIfAbsent(name + "\u0000" + term, key -> {
                AdsSearchTermStat created = new AdsSearchTermStat();
                created.setAdsOwner(adsOwner);
                created.setCampaignName(name);
                created.setSnapshotDate(reportDate);
                created.setSearchTerm(term);
                created.setImpressions(0L);
                created.setClicks(0L);
                created.setCost(BigDecimal.ZERO);
                return created;
            });
            stat.setImpressions(stat.getImpressions() + nonNegative(row.impressions()));
            stat.setClicks(stat.getClicks() + nonNegative(row.clicks()));
            if (row.cost() != null && row.cost().signum() > 0) {
                stat.setCost(stat.getCost().add(row.cost()));
            }
        }
        if (campaigns.isEmpty()) {
            return 0;
        }
        searchTermRepository.deleteSnapshot(adsOwner, reportDate, campaigns);
        searchTermRepository.saveAll(merged.values());
        return merged.size();
    }

    static LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim().substring(0, Math.min(10, value.trim().length())));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String campaignName(String value) {
        return StringUtils.hasText(value) ? truncate(value.trim(), 128) : null;
    }

    private static String key(String campaign, LocalDate date, String network) {
        return campaign + "\u0000" + date + "\u0000" + network;
    }

    private static long nonNegative(Long value) {
        return value == null ? 0L : Math.max(value, 0L);
    }

    // 展示份额 0~1, Google 低于 10% 时返回 0.0999 / impression share is 0..1
    private static BigDecimal share(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            return null;
        }
        return value.compareTo(BigDecimal.ONE) > 0 ? BigDecimal.ONE : value;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
