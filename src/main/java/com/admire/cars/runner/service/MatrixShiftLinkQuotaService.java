package com.admire.cars.runner.service;

import com.admire.cars.runner.config.MatrixShiftLinkDailyConfig;
import com.admire.cars.runner.repository.ShiftLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Matrix 自动任务的每日配额与次日清理: 生成前清掉非当天生成的链, 再按当天条数决定是否放行。
 * Daily quota and next-day purge for matrix auto tasks: drop links created before today, then check today's count.
 */
@Service
public class MatrixShiftLinkQuotaService {

    private static final Logger log = LoggerFactory.getLogger(MatrixShiftLinkQuotaService.class);

    private final ShiftLinkRepository shiftLinkRepository;
    private final MatrixShiftLinkDailyConfig config;

    public MatrixShiftLinkQuotaService(ShiftLinkRepository shiftLinkRepository,
                                       MatrixShiftLinkDailyConfig config) {
        this.shiftLinkRepository = shiftLinkRepository;
        this.config = config;
    }

    // 返回本次任务是否允许生成 / whether this job run is allowed to generate a new link
    @Transactional
    public boolean allowGeneration(Long adsId, String adsOwner, String adsName) {
        if (!config.isEnabled()) {
            log.info("[MatrixQuota] disabled adsId={} adsOwner={} campaign={} allowed=true", adsId, adsOwner, adsName);
            return true;
        }

        // 与 createShiftLink 落库值保持一致: owner 会 trim, campaign 名原样保存 / match persisted values
        String owner = adsOwner == null ? "" : adsOwner.trim();
        String campaign = adsName == null ? "" : adsName;
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        int purged = shiftLinkRepository.deleteMatrixCreatedBefore(owner, campaign, dayStart);
        long todayCount = shiftLinkRepository.countMatrixCreatedSince(owner, campaign, dayStart);
        boolean allowed = !config.isLimitReached(todayCount);
        log.info("[MatrixQuota] check adsId={} adsOwner={} campaign={} dayStart={} purgedStale={} todayCount={} dailyLimit={} allowed={}",
                adsId, owner, campaign, dayStart, purged, todayCount, config.getDailyLimit(), allowed);
        return allowed;
    }
}
