package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsClickTrack;
import com.admire.cars.runner.entity.AdsNormalInfo;
import com.admire.cars.runner.entity.ShiftLink;
import com.admire.cars.runner.repository.AdsClickTrackRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 点击ID台账: 记录每个生成的 subid 属于哪个 normaltask, 并统计真实点击
 * Click id ledger: binds every generated subid to a normal task and counts real hits
 */
@Slf4j
@Service
public class AdsClickTrackService {

    private final AdsClickTrackRepository adsClickTrackRepository;

    public AdsClickTrackService(AdsClickTrackRepository adsClickTrackRepository) {
        this.adsClickTrackRepository = adsClickTrackRepository;
    }

    // 生成 ShiftLink 时落一条点击ID / persist the click id when a shift link is generated
    @Transactional
    public AdsClickTrack recordIssued(String clickId, AdsNormalInfo ads, ShiftLink link, String affiliateUrl) {
        if (!StringUtils.hasText(clickId) || ads == null || link == null) {
            log.warn("CLICK_TRACK_SKIP clickId={} adsId={} linkId={}",
                    clickId, ads == null ? null : ads.getId(), link == null ? null : link.getId());
            return null;
        }
        String normalized = clickId.trim();
        AdsClickTrack track = adsClickTrackRepository.findByClickId(normalized).orElseGet(AdsClickTrack::new);
        track.setClickId(normalized);
        track.setNormalAdsId(ads.getId());
        track.setShiftLinkId(link.getId());
        track.setCampainName(ads.getCampainName());
        track.setPlatformName(ads.getPlatformName());
        track.setAdsOwner(ads.getAdsOwner());
        track.setAffiliateUrl(affiliateUrl);
        track.setFullUrl(link.getFullUrl());
        track.setLandingPageUrl(link.getLandingPageUrl());
        AdsClickTrack saved = adsClickTrackRepository.save(track);
        log.info("CLICK_TRACK_ISSUED clickId={} normalAdsId={} shiftLinkId={} campaign={} owner={}",
                normalized, ads.getId(), link.getId(), ads.getCampainName(), ads.getAdsOwner());
        return saved;
    }

    // /api/click 命中时累加真实点击 / count a real hit when the click redirect endpoint is used
    @Transactional
    public Optional<AdsClickTrack> recordRealClick(String clickId) {
        if (!StringUtils.hasText(clickId)) {
            return Optional.empty();
        }
        Optional<AdsClickTrack> found = adsClickTrackRepository.findByClickId(clickId.trim());
        found.ifPresent(track -> {
            long current = track.getClickCount() == null ? 0L : track.getClickCount();
            track.setClickCount(current + 1);
            track.setLastClickTime(LocalDateTime.now());
            adsClickTrackRepository.save(track);
            log.info("CLICK_TRACK_HIT clickId={} normalAdsId={} clickCount={}",
                    track.getClickId(), track.getNormalAdsId(), current + 1);
        });
        if (found.isEmpty()) {
            log.warn("CLICK_TRACK_UNKNOWN clickId={}", clickId.trim());
        }
        return found;
    }
}
