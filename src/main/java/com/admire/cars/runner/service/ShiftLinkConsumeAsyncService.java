package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.ShiftLink;
import com.admire.cars.runner.entity.ShiftLinkLog;
import com.admire.cars.runner.repository.ShiftLinkLogRepository;
import com.admire.cars.runner.repository.ShiftLinkRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShiftLinkConsumeAsyncService {

    private final ShiftLinkRepository shiftLinkRepository;
    private final ShiftLinkLogRepository shiftLinkLogRepository;

    public ShiftLinkConsumeAsyncService(
            ShiftLinkRepository shiftLinkRepository,
            ShiftLinkLogRepository shiftLinkLogRepository) {
        this.shiftLinkRepository = shiftLinkRepository;
        this.shiftLinkLogRepository = shiftLinkLogRepository;
    }

    @Async("adsAsyncExecutor")
    @Transactional
    public void recordConsume(Long shiftLinkId) {
        ShiftLink shiftLink = shiftLinkRepository.findById(shiftLinkId)
                .orElseThrow(() -> new IllegalArgumentException("SHIFT_LINK not found: " + shiftLinkId));

        long previousDisplayTimes = shiftLink.getDisplayTimes() == null ? 0L : shiftLink.getDisplayTimes();
        long nextDisplayTimes = previousDisplayTimes + 1L;
        shiftLink.setDisplayTimes(nextDisplayTimes);
        shiftLinkRepository.save(shiftLink);

        ShiftLinkLog log = new ShiftLinkLog();
        log.setAdsType(shiftLink.getAdsType());
        log.setPlatformName(shiftLink.getPlatformName());
        log.setAdsName(shiftLink.getAdsName());
        log.setFullUrl(shiftLink.getFullUrl());
        log.setDisplayTimes(nextDisplayTimes);
        log.setRemarks(shiftLink.getRemarks());
        log.setAdsOwner(shiftLink.getAdsOwner());
        log.setUserName(shiftLink.getUserName());
        shiftLinkLogRepository.save(log);

    }

    // Matrix 专用: 只写消耗日志, displayTimes 由 /api/track/report 的真实点击数驱动
    // Matrix only: log the consume without incrementing, displayTimes comes from real click reports
    @Async("adsAsyncExecutor")
    @Transactional
    public void recordLogOnly(Long shiftLinkId) {
        ShiftLink shiftLink = shiftLinkRepository.findById(shiftLinkId)
                .orElseThrow(() -> new IllegalArgumentException("SHIFT_LINK not found: " + shiftLinkId));

        ShiftLinkLog log = new ShiftLinkLog();
        log.setAdsType(shiftLink.getAdsType());
        log.setPlatformName(shiftLink.getPlatformName());
        log.setAdsName(shiftLink.getAdsName());
        log.setFullUrl(shiftLink.getFullUrl());
        log.setDisplayTimes(shiftLink.getDisplayTimes() == null ? 0L : shiftLink.getDisplayTimes());
        log.setRemarks(shiftLink.getRemarks());
        log.setAdsOwner(shiftLink.getAdsOwner());
        log.setUserName(shiftLink.getUserName());
        shiftLinkLogRepository.save(log);
    }
}
