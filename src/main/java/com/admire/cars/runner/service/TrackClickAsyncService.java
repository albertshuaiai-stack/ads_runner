package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.RanSiteIdPool;
import com.admire.cars.runner.entity.TrackClick;
import com.admire.cars.runner.repository.RanSiteIdPoolRepository;
import com.admire.cars.runner.repository.TrackClickRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
public class TrackClickAsyncService {

    private final TrackClickRepository trackClickRepository;
    private final RanSiteIdPoolRepository poolRepository;

    public TrackClickAsyncService(
            TrackClickRepository trackClickRepository,
            RanSiteIdPoolRepository poolRepository) {
        this.trackClickRepository = trackClickRepository;
        this.poolRepository = poolRepository;
    }

    // 异步落库, 不阻塞跳转 / async persistence, never blocks the redirect
    @Async("adsAsyncExecutor")
    @Transactional
    public void recordClick(TrackClick click, Long poolId) {
        try {
            trackClickRepository.save(click);
        } catch (Exception e) {
            log.error("failed to save track click, clickId={}", click.getClickId(), e);
        }

        if (poolId == null) {
            return;
        }
        try {
            poolRepository.findById(poolId).ifPresent(pool -> {
                long usedToday = pool.getUsedToday() == null ? 0L : pool.getUsedToday();
                long totalUsed = pool.getTotalUsed() == null ? 0L : pool.getTotalUsed();
                pool.setUsedToday(usedToday + 1);
                pool.setTotalUsed(totalUsed + 1);
                pool.setLastUsedAt(LocalDateTime.now());
                poolRepository.save(pool);
            });
        } catch (Exception e) {
            log.error("failed to update site id usage, poolId={}", poolId, e);
        }
    }
}
