package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsNormalInfo;
import com.admire.cars.runner.entity.ShiftLink;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.AdsNormalInfoRepository;
import com.admire.cars.runner.repository.ShiftLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 接收 Google Ads 报表点击数, 与当前链的换链基准比较, 达到配置阈值就换下一条。
 * Matrix 与 Normal 都按真实点击消耗, 与脚本拉取频率无关。
 * Rotate links when lifetime clicks minus the active link's base reach the configured threshold.
 */
@Service
public class TrackReportService {

    private static final Logger log = LoggerFactory.getLogger(TrackReportService.class);
    private static final String MATRIX_ADS_TYPE = "Matrix";
    private static final String NORMAL_ADS_TYPE = "Normal";

    private final UserService userService;
    private final ShiftLinkRepository shiftLinkRepository;
    private final ShiftLinkRotationHelper rotationHelper;
    private final NormalShiftLinkQuotaService normalQuotaService;
    private final AdsNormalInfoRepository adsNormalInfoRepository;

    public TrackReportService(UserService userService,
                              ShiftLinkRepository shiftLinkRepository,
                              ShiftLinkRotationHelper rotationHelper,
                              NormalShiftLinkQuotaService normalQuotaService,
                              AdsNormalInfoRepository adsNormalInfoRepository) {
        this.userService = userService;
        this.shiftLinkRepository = shiftLinkRepository;
        this.rotationHelper = rotationHelper;
        this.normalQuotaService = normalQuotaService;
        this.adsNormalInfoRepository = adsNormalInfoRepository;
    }

    @Transactional
    public int applyCampaignClicks(String apiKey, List<CampaignClicks> rows) {
        User user = userService.getEnabledUserByApiKey(apiKey);
        String adsOwner = user.getUserPhoneNumber();
        if (rows == null || rows.isEmpty()) {
            return 0;
        }

        int updatedCampaigns = 0;
        for (CampaignClicks row : rows) {
            if (row == null || !StringUtils.hasText(row.name())) {
                log.warn("click report row skipped owner={} row={}", adsOwner, row);
                continue;
            }
            long clicks = row.clicks() == null ? 0L : Math.max(row.clicks(), 0L);
            String campaignName = row.name().trim();
            log.info("click report row owner={} campaign={} clicks={} cost={} currency={}",
                    adsOwner, campaignName, clicks, row.cost(), row.currency());
            boolean matrixChanged = redistribute(adsOwner, campaignName, clicks, MATRIX_ADS_TYPE);
            boolean normalChanged = redistribute(adsOwner, campaignName, clicks, NORMAL_ADS_TYPE);
            // 累计点击增量换算成当天 Normal 生成额度 / turn the click delta into today's normal allowance
            normalQuotaService.recordClicks(adsOwner, campaignName, clicks);
            recordCost(adsOwner, campaignName, row.cost(), row.currency());
            if (matrixChanged || normalChanged) {
                updatedCampaigns++;
            }
        }
        return updatedCampaigns;
    }

    // 记录 campaign 累计花费, 供 Post Back 计算投入产出; 旧脚本不带花费时保持原值
    // Store lifetime ad cost for ROI stats; rows without cost (old scripts) keep the previous value
    private void recordCost(String adsOwner, String campaignName, BigDecimal cost, String currency) {
        if (cost == null || cost.signum() < 0) {
            return;
        }
        Optional<AdsNormalInfo> found = adsNormalInfoRepository.findByCampainNameAndAdsOwner(campaignName, adsOwner);
        if (found.isEmpty()) {
            log.info("ad cost skipped, no normal campaign owner={} campaign={} cost={}", adsOwner, campaignName, cost);
            return;
        }
        AdsNormalInfo ads = found.get();
        String normalizedCurrency = StringUtils.hasText(currency)
                ? currency.trim().toUpperCase(Locale.ROOT) : ads.getCostCurrency();
        ads.setLastReportCost(cost);
        ads.setCostCurrency(normalizedCurrency);
        adsNormalInfoRepository.save(ads);
        log.info("ad cost recorded adsId={} owner={} campaign={} cost={} currency={}",
                ads.getId(), adsOwner, campaignName, cost, normalizedCurrency);
    }

    // 只处理当前在用的链: clicks - clickBase 达到阈值就换下一条, 并把新基准记为当前 clicks
    // Only the active link is evaluated; rotation depends on clicks, never on how often the script runs
    private boolean redistribute(String adsOwner, String campaignName, long clicks, String adsType) {
        List<ShiftLink> links = rotationHelper.loadRunning(adsOwner, campaignName, adsType);
        if (links.isEmpty()) {
            return false;
        }
        long capacity = rotationHelper.capacity(adsType);
        int index = rotationHelper.activeIndex(links, capacity);
        if (index < 0) {
            return false;
        }

        ShiftLink link = links.get(index);
        Long base = link.getClickBase();
        if (base == null) {
            // 首次接入: 以当前累计点击为基准, 之前的点击不计入本条
            link.setClickBase(clicks);
            link.setDisplayTimes(0L);
            shiftLinkRepository.save(link);
            log.info("click base initialized type={} campaign={} linkId={} base={} capacity={}",
                    adsType, campaignName, link.getId(), clicks, capacity);
            return true;
        }

        long consumed = Math.max(clicks - base, 0L);
        long previous = link.getDisplayTimes() == null ? 0L : link.getDisplayTimes();
        if (consumed >= capacity) {
            link.setDisplayTimes(consumed);
            shiftLinkRepository.save(link);
            if (index + 1 < links.size()) {
                ShiftLink next = links.get(index + 1);
                next.setClickBase(clicks);
                next.setDisplayTimes(0L);
                shiftLinkRepository.save(next);
                log.info("shift link rotated type={} campaign={} fromId={} toId={} newBase={} capacity={}",
                        adsType, campaignName, link.getId(), next.getId(), clicks, capacity);
            } else {
                // 最后一条也已用完, 后续取模板会回退裸落地页, 不再使用这条链
                log.warn("shift link exhausted type={} campaign={} linkId={} no next link, bare lpurl fallback",
                        adsType, campaignName, link.getId());
            }
            return true;
        }

        if (previous != consumed) {
            link.setDisplayTimes(consumed);
            shiftLinkRepository.save(link);
            return true;
        }
        return false;
    }

    // cost: 累计花费(账户币种), currency: 账户币种代码 / lifetime cost in account currency and its code
    public record CampaignClicks(String name, Long clicks, BigDecimal cost, String currency) {
    }
}
