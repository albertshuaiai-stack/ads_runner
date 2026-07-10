package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.ShiftLink;
import com.admire.cars.runner.entity.ShiftLinkAud;
import com.admire.cars.runner.repository.ShiftLinkAudRepository;
import com.admire.cars.runner.repository.ShiftLinkRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShiftLinkConsumeAsyncService {

    private final ShiftLinkRepository shiftLinkRepository;
    private final ShiftLinkAudRepository shiftLinkAudRepository;

    public ShiftLinkConsumeAsyncService(ShiftLinkRepository shiftLinkRepository, ShiftLinkAudRepository shiftLinkAudRepository) {
        this.shiftLinkRepository = shiftLinkRepository;
        this.shiftLinkAudRepository = shiftLinkAudRepository;
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

        ShiftLinkAud audit = new ShiftLinkAud();
        audit.setShiftLinkId(shiftLink.getId());
        audit.setAdsOwner(shiftLink.getAdsOwner());
        audit.setAdsName(shiftLink.getAdsName());
        audit.setAdsType(shiftLink.getAdsType());
        audit.setSeqNumber(shiftLink.getSeqNumber());
        audit.setOperation("CONSUME");
        audit.setOldValue("displayTimes=" + previousDisplayTimes);
        audit.setNewValue("displayTimes=" + nextDisplayTimes);
        audit.setOperator("API_KEY");
        shiftLinkAudRepository.save(audit);
    }
}
