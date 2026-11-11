package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.ShiftLink;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.ShiftLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;

/**
 * 接收 Google Ads 报表点击数, 按"与上次换链时的点击基准比较"决定是否换链。
 * Rotate shift links by comparing lifetime clicks with the base recorded at the last switch.
 */
@Service
public class TrackReportService {

    private static final Logger log = LoggerFactory.getLogger(TrackReportService.class);
    private static final String MATRIX_ADS_TYPE = "Matrix";
    private static final String RUNNING_STATUS = "RUNNING";

    private final UserService userService;
    private final ShiftLinkRepository shiftLinkRepository;

    public TrackReportService(UserService userService, ShiftLinkRepository shiftLinkRepository) {
        this.userService = userService;
        this.shiftLinkRepository = shiftLinkRepository;
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
                continue;
            }
            long clicks = row.clicks() == null ? 0L : Math.max(row.clicks(), 0L);
            if (redistribute(adsOwner, row.name().trim(), clicks)) {
                updatedCampaigns++;
            }
        }
        return updatedCampaigns;
    }

    // 只处理当前使用中的 link: clicks - clickBase 达到 displayNumber 就换下一条, 并把新基准记为当前 clicks
    // Only the active link is evaluated; rotation depends on clicks, never on how often the script runs
    private boolean redistribute(String adsOwner, String campaignName, long clicks) {
        List<ShiftLink> links = shiftLinkRepository
                .findByAdsOwnerAndAdsNameAndAdsTypeOrderBySeqNumberAsc(adsOwner, campaignName, MATRIX_ADS_TYPE)
                .stream()
                .filter(link -> RUNNING_STATUS.equalsIgnoreCase(link.getStatus()))
                .sorted(Comparator.comparing((ShiftLink link) ->
                                link.getSeqNumber() == null ? 0L : link.getSeqNumber())
                        .thenComparing(ShiftLink::getId))
                .toList();
        if (links.isEmpty()) {
            log.info("click report ignored, no matrix shift link campaign={} owner={}", campaignName, adsOwner);
            return false;
        }

        for (int i = 0; i < links.size(); i++) {
            ShiftLink link = links.get(i);
            long capacity = link.getDisplayNumber() == null ? 0L : link.getDisplayNumber();
            long used = link.getDisplayTimes() == null ? 0L : link.getDisplayTimes();
            if (used >= capacity) {
                // 已耗尽的链直接跳过 / exhausted links are skipped
                continue;
            }

            Long base = link.getClickBase();
            if (base == null) {
                // 首次接入: 以当前累计点击为基准, 之前的点击不计入本条
                link.setClickBase(clicks);
                link.setDisplayTimes(0L);
                shiftLinkRepository.save(link);
                log.info("click base initialized campaign={} linkId={} base={}", campaignName, link.getId(), clicks);
                return true;
            }

            long consumed = Math.max(clicks - base, 0L);
            if (consumed >= capacity) {
                link.setDisplayTimes(capacity);
                shiftLinkRepository.save(link);
                if (i + 1 < links.size()) {
                    ShiftLink next = links.get(i + 1);
                    next.setClickBase(clicks);
                    next.setDisplayTimes(0L);
                    shiftLinkRepository.save(next);
                    log.info("shift link rotated campaign={} fromId={} toId={} newBase={}",
                            campaignName, link.getId(), next.getId(), clicks);
                } else {
                    log.info("shift link exhausted campaign={} linkId={} no next link", campaignName, link.getId());
                }
                return true;
            }

            if (used != consumed) {
                link.setDisplayTimes(consumed);
                shiftLinkRepository.save(link);
                return true;
            }
            return false;
        }
        return false;
    }

    public record CampaignClicks(String name, Long clicks) {
    }
}
