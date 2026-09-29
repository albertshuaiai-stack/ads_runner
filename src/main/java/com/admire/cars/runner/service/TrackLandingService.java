package com.admire.cars.runner.service;

import com.admire.cars.runner.dto.TrackClickMeta;
import com.admire.cars.runner.entity.ShiftLink;
import com.admire.cars.runner.entity.TrackClick;
import com.admire.cars.runner.repository.ShiftLinkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 按落地页路由 / Route by landing page.
 * <p>
 * Google 的 tracking template 形如 https://track.example.com/?url={lpurl}
 * 点击带来的 url 参数就是广告的落地页, 直接拿它在 SHIFT_LINK 里找已入库的联盟链接,
 * 按用量最少优先(轮流)取一条并 302 过去, 不区分归属.
 * <p>
 * The url param carries the ad landing page. Look it up in SHIFT_LINK, pick the least used
 * entry (round robin) and 302 to it. No owner scoping.
 */
@Slf4j
@Service
public class TrackLandingService {

    private static final String RUNNING_STATUS = "RUNNING";

    private final ShiftLinkRepository shiftLinkRepository;
    private final ShiftLinkConsumeAsyncService shiftLinkConsumeAsyncService;
    private final TrackClickAsyncService trackClickAsyncService;

    public TrackLandingService(
            ShiftLinkRepository shiftLinkRepository,
            ShiftLinkConsumeAsyncService shiftLinkConsumeAsyncService,
            TrackClickAsyncService trackClickAsyncService) {
        this.shiftLinkRepository = shiftLinkRepository;
        this.shiftLinkConsumeAsyncService = shiftLinkConsumeAsyncService;
        this.trackClickAsyncService = trackClickAsyncService;
    }

    /**
     * @return 命中的联盟链接, 未命中返回 null / the matched affiliate url, or null when nothing matches
     */
    @Transactional(readOnly = true)
    public String resolve(String landingUrl, String gclid, TrackClickMeta meta) {
        if (landingUrl == null || landingUrl.isBlank()) {
            return null;
        }

        List<ShiftLink> candidates = shiftLinkRepository.findRotatingForLanding(landingUrl.trim(), RUNNING_STATUS);
        if (candidates.isEmpty()) {
            log.warn("no shift link found for landing url={}", landingUrl);
            return null;
        }

        ShiftLink selected = candidates.get(0);
        String targetUrl = selected.getFullUrl();

        // 点击日志, clickId 仅内部留档, 当前不注入到跳转链接
        // click log only; the click id is not injected into the redirect url for now
        TrackClick click = new TrackClick();
        click.setClickId(UUID.randomUUID().toString().replace("-", ""));
        click.setCampaignName(selected.getAdsName());
        click.setPlatform(selected.getPlatformName());
        click.setGclid(gclid);
        click.setTargetUrl(targetUrl);
        click.setLandingPageUrl(landingUrl.trim());
        click.setClickTime(LocalDateTime.now());
        if (meta != null) {
            click.setIp(meta.getIp());
            click.setUserAgent(meta.getUserAgent());
            click.setReferer(meta.getReferer());
            click.setDevice(meta.getDevice());
            click.setCountry(meta.getCountry());
        }

        trackClickAsyncService.recordClick(click, null);
        // 计数 +1, 实现轮流 / bump usage so the next click picks another entry
        shiftLinkConsumeAsyncService.recordConsume(selected.getId());

        return targetUrl;
    }
}
