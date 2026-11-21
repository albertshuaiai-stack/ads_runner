package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsCampaignCost;
import com.admire.cars.runner.repository.AdsCampaignCostRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Google Ads 花费台账: 按 campaign 记录累计花费, 删除 Normal 任务或在 Google Ads 删除/重建广告后历史投入仍保留
 * Ad cost ledger per Google Ads campaign; history survives deleting the task or removing/recreating the campaign
 */
@Slf4j
@Service
public class AdsCampaignCostService {

    static final String ID_KEY_PREFIX = "ID:";
    static final String NAME_KEY_PREFIX = "NAME:";
    static final String STATUS_REPLACED = "REPLACED";
    // 旧脚本无 id: 同名累计花费跌破一半视为删除后重建的新 campaign / old script: a drop below half means a recreated campaign
    static final BigDecimal RESET_RATIO = new BigDecimal("0.5");
    // 小于 1 个货币单位的回落按无效点击退款处理 / drops under one currency unit are treated as invalid-click credits
    static final BigDecimal RESET_MIN_DROP = BigDecimal.ONE;
    // 新脚本首次带 id: 不低于旧记录 95% 才认定为同一 campaign / adopt the legacy row only when cost is at least 95% of it
    static final BigDecimal ADOPT_RATIO = new BigDecimal("0.95");
    private static final int NAME_MAX_LENGTH = 128;
    private static final int KEY_MAX_LENGTH = 160;

    private final AdsCampaignCostRepository repository;

    public AdsCampaignCostService(AdsCampaignCostRepository repository) {
        this.repository = repository;
    }

    // 独立事务, 台账失败不回滚换链 / own transaction so a ledger failure never rolls back link rotation
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String adsOwner, String campaignId, String campaignName, String status,
                       BigDecimal cost, String currency, Long normalAdsId) {
        if (!StringUtils.hasText(adsOwner) || !StringUtils.hasText(campaignName) || cost == null || cost.signum() < 0) {
            return;
        }
        String name = truncate(campaignName.trim(), NAME_MAX_LENGTH);
        String id = StringUtils.hasText(campaignId) ? campaignId.trim() : null;
        String normalizedCurrency = StringUtils.hasText(currency) ? currency.trim().toUpperCase(Locale.ROOT) : null;

        AdsCampaignCost row;
        if (id != null) {
            row = repository.findByAdsOwnerAndCampaignKey(adsOwner, ID_KEY_PREFIX + id)
                    .orElseGet(() -> adoptLegacyOrCreate(adsOwner, id, name, cost, normalizedCurrency));
        } else {
            if (repository.existsByAdsOwnerAndCampaignNameAndCampaignIdIsNotNull(adsOwner, name)) {
                log.info("CAMPAIGN_COST_LEGACY_ROW_IGNORED owner={} campaign={} cost={} reason=id rows exist",
                        adsOwner, name, cost);
                return;
            }
            row = legacyRow(adsOwner, name, cost, normalizedCurrency);
        }
        row.setCampaignName(name);
        if (StringUtils.hasText(status)) {
            row.setCampaignStatus(truncate(status.trim().toUpperCase(Locale.ROOT), 16));
        }
        row.setLifetimeCost(cost);
        if (normalizedCurrency != null) {
            row.setCostCurrency(normalizedCurrency);
        }
        if (normalAdsId != null) {
            row.setNormalAdsId(normalAdsId);
        }
        row.setLastReportTime(LocalDateTime.now());
        repository.save(row);
        log.info("CAMPAIGN_COST_RECORDED owner={} key={} campaign={} status={} cost={} currency={} normalAdsId={}",
                adsOwner, row.getCampaignKey(), name, row.getCampaignStatus(), cost, row.getCostCurrency(),
                row.getNormalAdsId());
    }

    // owner 为空时返回全部 / all owners when owner is empty
    @Transactional(readOnly = true)
    public List<AdsCampaignCost> listForOwner(String adsOwner) {
        return adsOwner == null ? repository.findAll() : repository.findByAdsOwner(adsOwner);
    }

    // 与 MySQL 默认排序规则一致, 名称不区分大小写 / case-insensitive like the MySQL default collation
    public static String matchKey(String owner, String campaignName) {
        return (owner == null ? "" : owner) + "\u0000"
                + (campaignName == null ? "" : campaignName.trim().toLowerCase(Locale.ROOT));
    }

    public static Map<String, List<AdsCampaignCost>> groupByCampaign(List<AdsCampaignCost> rows) {
        Map<String, List<AdsCampaignCost>> grouped = new LinkedHashMap<>();
        for (AdsCampaignCost row : rows) {
            grouped.computeIfAbsent(matchKey(row.getAdsOwner(), row.getCampaignName()), key -> new ArrayList<>()).add(row);
        }
        return grouped;
    }

    // 新脚本首次带 id 上报: 旧的按名称记录若是同一 campaign 就接管, 否则保留为历史 / adopt the name-keyed legacy row if it is the same campaign
    private AdsCampaignCost adoptLegacyOrCreate(String owner, String id, String name, BigDecimal cost, String currency) {
        Optional<AdsCampaignCost> legacy = repository.findByAdsOwnerAndCampaignKey(owner, NAME_KEY_PREFIX + name);
        if (legacy.isPresent()) {
            AdsCampaignCost row = legacy.get();
            if (sameCampaign(row, cost, currency)) {
                log.info("CAMPAIGN_COST_LEGACY_ADOPTED owner={} campaign={} campaignId={} legacyCost={} cost={}",
                        owner, name, id, row.getLifetimeCost(), cost);
                row.setCampaignKey(ID_KEY_PREFIX + id);
                row.setCampaignId(id);
                return row;
            }
            log.info("CAMPAIGN_COST_LEGACY_KEPT owner={} campaign={} campaignId={} legacyCost={} cost={}",
                    owner, name, id, row.getLifetimeCost(), cost);
        }
        return newRow(owner, ID_KEY_PREFIX + id, id, name);
    }

    private boolean sameCampaign(AdsCampaignCost legacy, BigDecimal cost, String currency) {
        BigDecimal legacyCost = legacy.getLifetimeCost();
        if (legacyCost == null || legacyCost.signum() == 0) {
            return true;
        }
        if (!sameCurrency(legacy.getCostCurrency(), currency)) {
            return false;
        }
        return cost.compareTo(legacyCost.multiply(ADOPT_RATIO)) >= 0;
    }

    // 旧脚本按名称记账; 花费大幅回落说明广告被删除重建, 旧记录归档保留 / old script: archive the row when the campaign was recreated
    private AdsCampaignCost legacyRow(String owner, String name, BigDecimal cost, String currency) {
        String key = NAME_KEY_PREFIX + name;
        Optional<AdsCampaignCost> found = repository.findByAdsOwnerAndCampaignKey(owner, key);
        if (found.isEmpty()) {
            return newRow(owner, key, null, name);
        }
        AdsCampaignCost row = found.get();
        if (!looksRecreated(row, cost, currency)) {
            return row;
        }
        String archivedKey = truncate(key, KEY_MAX_LENGTH - 14) + "@" + System.currentTimeMillis();
        log.warn("CAMPAIGN_COST_RESET_ARCHIVED owner={} campaign={} archivedKey={} archivedCost={} newCost={}",
                owner, name, archivedKey, row.getLifetimeCost(), cost);
        row.setCampaignKey(archivedKey);
        row.setCampaignStatus(STATUS_REPLACED);
        // 先落库改名, 否则新行插入会撞唯一键 / flush the rename first, otherwise the insert hits the unique key
        repository.saveAndFlush(row);
        AdsCampaignCost fresh = newRow(owner, key, null, name);
        fresh.setNormalAdsId(row.getNormalAdsId());
        return fresh;
    }

    private boolean looksRecreated(AdsCampaignCost row, BigDecimal cost, String currency) {
        BigDecimal stored = row.getLifetimeCost();
        if (stored == null || stored.signum() <= 0 || !sameCurrency(row.getCostCurrency(), currency)) {
            return false;
        }
        return cost.compareTo(stored.multiply(RESET_RATIO)) < 0
                && stored.subtract(cost).compareTo(RESET_MIN_DROP) >= 0;
    }

    private static boolean sameCurrency(String left, String right) {
        return left == null || right == null || left.equalsIgnoreCase(right);
    }

    private static AdsCampaignCost newRow(String owner, String key, String id, String name) {
        AdsCampaignCost row = new AdsCampaignCost();
        row.setAdsOwner(owner);
        row.setCampaignKey(truncate(key, KEY_MAX_LENGTH));
        row.setCampaignId(id);
        row.setCampaignName(name);
        row.setLifetimeCost(BigDecimal.ZERO);
        return row;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
