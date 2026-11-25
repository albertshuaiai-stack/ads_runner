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
 * 接收 Google Ads 报表点击数, 与当前链的换链基准比较, 达到配置阈值就换成最新未用过的链。
 * Matrix 与 Normal 都按真实点击消耗, 与脚本拉取频率无关。
 * Rotate to the newest unused link when lifetime clicks minus the active link's base reach the configured threshold.
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
    private final AdsCampaignCostService campaignCostService;

    public TrackReportService(UserService userService,
                              ShiftLinkRepository shiftLinkRepository,
                              ShiftLinkRotationHelper rotationHelper,
                              NormalShiftLinkQuotaService normalQuotaService,
                              AdsNormalInfoRepository adsNormalInfoRepository,
                              AdsCampaignCostService campaignCostService) {
        this.userService = userService;
        this.shiftLinkRepository = shiftLinkRepository;
        this.rotationHelper = rotationHelper;
        this.normalQuotaService = normalQuotaService;
        this.adsNormalInfoRepository = adsNormalInfoRepository;
        this.campaignCostService = campaignCostService;
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
            boolean enabled = isEnabled(row.status());
            log.info("click report row owner={} campaign={} campaignId={} status={} clicks={} cost={} currency={}",
                    adsOwner, campaignName, row.id(), row.status(), clicks, row.cost(), row.currency());
            if (!enabled) {
                // 暂停的广告只记花费, 不参与换链和额度, 避免与同名在投广告的点击数混淆
                // paused campaigns only update the cost ledger; their clicks must not drive rotation or quota
                recordCost(adsOwner, row, campaignName, false);
                continue;
            }
            boolean matrixChanged = redistribute(adsOwner, campaignName, clicks, MATRIX_ADS_TYPE);
            boolean normalChanged = redistribute(adsOwner, campaignName, clicks, NORMAL_ADS_TYPE);
            // 累计点击增量换算成当天 Normal 生成额度 / turn the click delta into today's normal allowance
            normalQuotaService.recordClicks(adsOwner, campaignName, clicks);
            recordCost(adsOwner, row, campaignName, true);
            if (matrixChanged || normalChanged) {
                updatedCampaigns++;
            }
        }
        return updatedCampaigns;
    }

    // 旧脚本不带状态, 视为在投 / rows from old scripts carry no status and are treated as enabled
    private static boolean isEnabled(String status) {
        return !StringUtils.hasText(status) || "ENABLED".equalsIgnoreCase(status.trim());
    }

    // 花费写入按 campaign 的台账(删除任务/广告后仍保留); 在投广告同时刷新任务上的最近花费, 兼容旧读取方
    // Cost goes to the per-campaign ledger that survives deletions; enabled rows also refresh the task field for old readers
    private void recordCost(String adsOwner, CampaignClicks row, String campaignName, boolean enabled) {
        BigDecimal cost = row.cost();
        if (cost == null || cost.signum() < 0) {
            return;
        }
        List<AdsNormalInfo> tasks = adsNormalInfoRepository.findByAdsOwnerAndCampainNameOrderByIdDesc(adsOwner, campaignName);
        AdsNormalInfo task = tasks.isEmpty() ? null : tasks.get(0);
        try {
            campaignCostService.record(adsOwner, row.id(), campaignName, row.status(), cost, row.currency(),
                    task == null ? null : task.getId());
        } catch (RuntimeException e) {
            log.warn("CAMPAIGN_COST_RECORD_FAILED owner={} campaign={} campaignId={} cost={} error={}",
                    adsOwner, campaignName, row.id(), cost, e.getMessage());
        }
        if (task == null) {
            log.info("ad cost ledger only, no normal campaign owner={} campaign={} cost={}", adsOwner, campaignName, cost);
            return;
        }
        if (!enabled) {
            return;
        }
        String normalizedCurrency = StringUtils.hasText(row.currency())
                ? row.currency().trim().toUpperCase(Locale.ROOT) : task.getCostCurrency();
        task.setLastReportCost(cost);
        task.setCostCurrency(normalizedCurrency);
        adsNormalInfoRepository.save(task);
        log.info("ad cost recorded adsId={} owner={} campaign={} cost={} currency={}",
                task.getId(), adsOwner, campaignName, cost, normalizedCurrency);
    }

    // 在用的链 clicks - clickBase 达到阈值就换成最新未用过的链, 新链基准记为当前 clicks; 与脚本拉取频率无关
    // When the active link reaches the threshold, switch to the newest unused link; independent of fetch frequency
    private boolean redistribute(String adsOwner, String campaignName, long clicks, String adsType) {
        List<ShiftLink> links = rotationHelper.loadPool(adsOwner, campaignName, adsType);
        if (links.isEmpty()) {
            return false;
        }
        long capacity = rotationHelper.capacity(adsType);
        Optional<ShiftLink> current = rotationHelper.current(links);

        if (current.isPresent()) {
            ShiftLink link = current.get();
            long consumed = Math.max(clicks - link.getClickBase(), 0L);
            long previous = link.getDisplayTimes() == null ? 0L : link.getDisplayTimes();
            boolean alreadyUsedUp = rotationHelper.isUsedUp(link, capacity);
            if (!alreadyUsedUp && consumed < capacity) {
                if (previous == consumed) {
                    return false;
                }
                link.setDisplayTimes(consumed);
                shiftLinkRepository.save(link);
                return true;
            }
            if (!alreadyUsedUp) {
                // 本次刚用完, 冻结它的点击数 / used up just now, freeze its click count
                link.setDisplayTimes(consumed);
                shiftLinkRepository.save(link);
            }
            return activateNewest(links, clicks, adsType, campaignName, capacity, link, !alreadyUsedUp);
        }
        // 还没有启用过任何链(新 campaign 或在用链已被清理) / nothing active yet, e.g. new campaign or purged link
        return activateNewest(links, clicks, adsType, campaignName, capacity, null, false);
    }

    // 启用最新未用过的链, 以当前累计点击为基准 / activate the newest unused link with the current lifetime clicks as base
    private boolean activateNewest(List<ShiftLink> links, long clicks, String adsType, String campaignName,
                                   long capacity, ShiftLink from, boolean fromJustUsedUp) {
        // Matrix 换链必须换到不同平台或邮箱的落地页; 找不到候选就保持当前链 / Matrix must switch to a different platform or email
        boolean matrix = MATRIX_ADS_TYPE.equals(adsType);
        Optional<ShiftLink> next = matrix
                ? rotationHelper.newestUnusedExcludingSameSource(links, from)
                : rotationHelper.newestUnused(links);
        if (next.isEmpty()) {
            if (fromJustUsedUp) {
                log.warn("shift link exhausted type={} campaign={} linkId={} reason={} fallback={}",
                        adsType, campaignName, from.getId(),
                        matrix ? "no link with different platform or email" : "no unused link",
                        matrix ? "keep current link" : "bare lpurl");
            }
            // Matrix 不换到同源落地页, 继续用当前链直到出现不同平台或邮箱的新链
            return !matrix && fromJustUsedUp;
        }
        ShiftLink link = next.get();
        link.setClickBase(clicks);
        link.setDisplayTimes(0L);
        shiftLinkRepository.save(link);
        if (from == null) {
            log.info("click base initialized type={} campaign={} linkId={} base={} capacity={}",
                    adsType, campaignName, link.getId(), clicks, capacity);
        } else {
            log.info("shift link rotated type={} campaign={} fromId={} toId={} newBase={} capacity={} unusedLeft={}",
                    adsType, campaignName, from.getId(), link.getId(), clicks, capacity,
                    links.stream().filter(item -> item.getClickBase() == null).count() - 1);
        }
        return true;
    }

    // cost: 累计花费(账户币种), currency: 账户币种代码, id: Google Ads campaign id(旧脚本为空), status: ENABLED/PAUSED(旧脚本为空)
    // lifetime cost and account currency; id and status are empty for old scripts
    public record CampaignClicks(String name, Long clicks, BigDecimal cost, String currency, String id, String status) {
    }
}
