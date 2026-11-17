package com.admire.cars.runner.service;

import com.admire.cars.runner.config.NormalShiftLinkDailyConfig;
import com.admire.cars.runner.entity.AdsNormalInfo;
import com.admire.cars.runner.repository.AdsNormalInfoRepository;
import com.admire.cars.runner.repository.ShiftLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Normal 自动任务每日额度: 每天先给 base 条, 每次点击上报把新增点击数换成额度累加到当天。
 * Daily allowance for normal auto tasks: base links per day plus allowance earned from each click delta.
 * 额度按天累计、按当天已生成数扣减, 因此某个小时产不完会自然结转到后续小时。
 * Allowance accumulates per day and is compared with links already generated today, so unused budget carries over.
 */
@Service
public class NormalShiftLinkQuotaService {

    private static final Logger log = LoggerFactory.getLogger(NormalShiftLinkQuotaService.class);

    private final AdsNormalInfoRepository adsNormalInfoRepository;
    private final ShiftLinkRepository shiftLinkRepository;
    private final NormalShiftLinkDailyConfig config;

    public NormalShiftLinkQuotaService(AdsNormalInfoRepository adsNormalInfoRepository,
                                       ShiftLinkRepository shiftLinkRepository,
                                       NormalShiftLinkDailyConfig config) {
        this.adsNormalInfoRepository = adsNormalInfoRepository;
        this.shiftLinkRepository = shiftLinkRepository;
        this.config = config;
    }

    // 接收 Google Ads 上报的累计点击数, 只把比上次多的部分换成额度 / earn allowance from the click delta only
    @Transactional
    public void recordClicks(String adsOwner, String campaignName, long clicks) {
        if (!config.isEnabled()) {
            return;
        }
        Optional<AdsNormalInfo> found = adsNormalInfoRepository
                .findByCampainNameAndAdsOwner(campaignName, adsOwner);
        if (found.isEmpty()) {
            log.info("[NormalQuota] no normal campaign adsOwner={} campaign={} clicks={}", adsOwner, campaignName, clicks);
            return;
        }

        AdsNormalInfo ads = found.get();
        String today = LocalDate.now().toString();
        long base = Math.max(config.getDailyBase(), 0L);
        long allowance = today.equals(ads.getQuotaDate())
                ? nullToValue(ads.getDailyAllowance(), base) : base;
        Long lastClicks = ads.getLastReportClicks();
        long delta = lastClicks == null ? 0L : Math.max(clicks - lastClicks, 0L);
        long earned = saturatingMultiply(delta, Math.max(config.getClickMultiplier(), 0L));
        long nextAllowance = saturatingAdd(allowance, earned);
        long nextClicks = lastClicks == null ? clicks : Math.max(lastClicks, clicks);

        ads.setQuotaDate(today);
        ads.setDailyAllowance(nextAllowance);
        ads.setLastReportClicks(nextClicks);
        adsNormalInfoRepository.save(ads);
        log.info("[NormalQuota] clicks recorded adsId={} adsOwner={} campaign={} clicks={} lastClicks={} delta={} earned={} allowance={}",
                ads.getId(), adsOwner, campaignName, clicks, lastClicks, delta, earned, nextAllowance);
    }

    // 生成前判定: 当天已生成数是否还在额度内 / whether today's generated links stay within the allowance
    @Transactional(readOnly = true)
    public boolean allowGeneration(AdsNormalInfo ads) {
        if (!config.isEnabled()) {
            log.info("[NormalQuota] disabled adsId={}", ads.getId());
            return true;
        }

        LocalDate today = LocalDate.now();
        long base = Math.max(config.getDailyBase(), 0L);
        long allowance = today.toString().equals(ads.getQuotaDate())
                ? nullToValue(ads.getDailyAllowance(), base) : base;
        String owner = ads.getAdsOwner() == null ? "" : ads.getAdsOwner();
        String campaign = ads.getCampainName() == null ? "" : ads.getCampainName();
        long generatedToday = shiftLinkRepository.countNormalCreatedSince(owner, campaign, today.atStartOfDay());
        boolean allowed = generatedToday < allowance;
        log.info("[NormalQuota] check adsId={} adsOwner={} campaign={} dayStart={} generatedToday={} allowance={} allowed={}",
                ads.getId(), owner, campaign, today.atStartOfDay(), generatedToday, allowance, allowed);
        return allowed;
    }

    private static long nullToValue(Long value, long fallback) {
        return value == null ? fallback : Math.max(value, 0L);
    }

    private static long saturatingMultiply(long value, long multiplier) {
        if (value == 0 || multiplier == 0) {
            return 0L;
        }
        return value > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : value * multiplier;
    }

    private static long saturatingAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
