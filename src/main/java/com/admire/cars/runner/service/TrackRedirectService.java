package com.admire.cars.runner.service;

import com.admire.cars.runner.dto.TrackClickMeta;
import com.admire.cars.runner.dto.TrackRedirectResult;
import com.admire.cars.runner.entity.RanSiteIdPool;
import com.admire.cars.runner.entity.TrackCampaign;
import com.admire.cars.runner.entity.TrackClick;
import com.admire.cars.runner.repository.RanSiteIdPoolRepository;
import com.admire.cars.runner.repository.TrackCampaignRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class TrackRedirectService {

    private final TrackCampaignRepository campaignRepository;
    private final RanSiteIdPoolRepository poolRepository;
    private final TrackClickAsyncService trackClickAsyncService;

    // 热路径缓存, 跳转时不查库 / cache for redirect hot path, no db access during redirect
    private final Cache<String, TrackCampaign> campaignCache = Caffeine.newBuilder()
            .maximumSize(2000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    private final Cache<String, PoolSnapshot> poolCache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    public TrackRedirectService(
            TrackCampaignRepository campaignRepository,
            RanSiteIdPoolRepository poolRepository,
            TrackClickAsyncService trackClickAsyncService) {
        this.campaignRepository = campaignRepository;
        this.poolRepository = poolRepository;
        this.trackClickAsyncService = trackClickAsyncService;
    }

    /**
     * Resolve the affiliate target url for a click.
     * 重要: 该方法不读取 UA / IP 等任何访客特征做分支, 所有访客走完全相同的路径, 避免 cloak
     * Important: no visitor based branching here, every visitor follows the same path to avoid cloaking.
     *
     * @return null when the campaign is missing or not runnable
     */
    public TrackRedirectResult resolve(String slug, String gclid, String landingPageUrl, TrackClickMeta meta) {
        TrackCampaign campaign = campaignCache.get(slug, key -> campaignRepository.findBySlug(key).orElse(null));
        if (campaign == null || !TrackCampaign.STATUS_RUNNING.equalsIgnoreCase(campaign.getStatus())) {
            log.warn("track campaign not runnable, slug={}", slug);
            return null;
        }

        String clickId = UUID.randomUUID().toString().replace("-", "");
        RanSiteIdPool selected = resolveSiteId(campaign);
        String siteId = selected == null ? campaign.getFixedSiteId() : selected.getSiteId();

        String targetUrl = campaign.getOfferUrlTemplate()
                .replace("{siteId}", encode(siteId == null ? "" : siteId))
                .replace("{clickId}", encode(clickId));

        TrackClick click = new TrackClick();
        click.setClickId(clickId);
        click.setSlug(slug);
        click.setAdsOwner(campaign.getAdsOwner());
        click.setCampaignName(campaign.getCampaignName());
        click.setSiteId(siteId);
        click.setPlatform(selected == null ? null : selected.getPlatform());
        click.setAdvertiser(selected == null ? null : selected.getAdvertiser());
        // 保留 gclid 仅用于自身归因分析 / keep gclid for own attribution analysis
        click.setGclid(gclid);
        click.setTargetUrl(targetUrl);
        click.setLandingPageUrl(landingPageUrl == null ? campaign.getLandingPageUrl() : landingPageUrl);
        click.setClickTime(LocalDateTime.now());
        if (meta != null) {
            click.setIp(meta.getIp());
            click.setUserAgent(meta.getUserAgent());
            click.setReferer(meta.getReferer());
            click.setDevice(meta.getDevice());
            click.setCountry(meta.getCountry());
        }

        trackClickAsyncService.recordClick(click, selected == null ? null : selected.getId());

        return new TrackRedirectResult(clickId, siteId, targetUrl);
    }

    // 按策略选取 siteId / pick a site id by rotation strategy
    private RanSiteIdPool resolveSiteId(TrackCampaign campaign) {
        if (TrackCampaign.ROTATE_FIXED.equalsIgnoreCase(campaign.getRotateStrategy())) {
            return null;
        }

        String poolCode = campaign.getPoolCode();
        if (poolCode == null || poolCode.isBlank()) {
            return null;
        }

        PoolSnapshot snapshot = poolCache.get(poolCode, this::loadPool);
        if (snapshot == null || !LocalDate.now().equals(snapshot.date)) {
            // 跨天后重置当日计数 / reset daily counters on a new day
            poolCache.invalidate(poolCode);
            snapshot = poolCache.get(poolCode, this::loadPool);
        }
        if (snapshot == null || snapshot.entries.isEmpty()) {
            log.warn("no active site id in pool, poolCode={}", poolCode);
            return null;
        }

        PoolEntry entry = pickWeighted(snapshot.entries);
        if (entry == null) {
            log.warn("all site ids reached daily cap, poolCode={}", poolCode);
            return null;
        }
        entry.usedToday.incrementAndGet();
        return entry.entity;
    }

    private PoolSnapshot loadPool(String poolCode) {
        List<RanSiteIdPool> items = poolRepository.findByPoolCodeAndStatus(poolCode, RanSiteIdPool.STATUS_ACTIVE);
        List<PoolEntry> entries = new ArrayList<>();
        for (RanSiteIdPool item : items) {
            long usedToday = item.getUsedToday() == null ? 0L : item.getUsedToday();
            entries.add(new PoolEntry(item, new AtomicLong(usedToday)));
        }
        return new PoolSnapshot(LocalDate.now(), entries);
    }

    // 加权随机, 跳过达到日上限的条目 / weighted random, skipping entries that hit the daily cap
    private PoolEntry pickWeighted(List<PoolEntry> entries) {
        List<PoolEntry> available = new ArrayList<>();
        int totalWeight = 0;
        for (PoolEntry entry : entries) {
            long cap = entry.entity.getDailyCap() == null ? 0L : entry.entity.getDailyCap();
            if (cap > 0 && entry.usedToday.get() >= cap) {
                continue;
            }
            available.add(entry);
            totalWeight += weightOf(entry);
        }
        if (available.isEmpty()) {
            return null;
        }
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int cursor = 0;
        for (PoolEntry entry : available) {
            cursor += weightOf(entry);
            if (roll < cursor) {
                return entry;
            }
        }
        return available.get(available.size() - 1);
    }

    private int weightOf(PoolEntry entry) {
        Integer weight = entry.entity.getWeight();
        return weight == null || weight <= 0 ? 1 : weight;
    }

    private String encode(String value) {
        return UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8);
    }

    private static class PoolSnapshot {
        private final LocalDate date;
        private final List<PoolEntry> entries;

        private PoolSnapshot(LocalDate date, List<PoolEntry> entries) {
            this.date = date;
            this.entries = entries;
        }
    }

    private static class PoolEntry {
        private final RanSiteIdPool entity;
        private final AtomicLong usedToday;

        private PoolEntry(RanSiteIdPool entity, AtomicLong usedToday) {
            this.entity = entity;
            this.usedToday = usedToday;
        }
    }
}
