package com.admire.cars.runner.job;

import com.admire.cars.runner.repository.ShiftLinkLogRepository;
import com.admire.cars.runner.repository.ShiftLinkRepository;
import com.admire.cars.runner.repository.AdsTaskLogRepository;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;

@DisallowConcurrentExecution
public class HousekeepingJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(HousekeepingJob.class);

    @Autowired
    private ShiftLinkLogRepository shiftLinkLogRepository;

    @Autowired
    private ShiftLinkRepository shiftLinkRepository;

    @Autowired
    private AdsTaskLogRepository adsTaskLogRepository;

    @Value("${housekeeping.shift-link-log.retention-days:7}")
    private int shiftLinkLogRetentionDays;

    @Value("${housekeeping.normal.shift-link.retention-days:1}")
    private int normalShiftLinkRetentionDays;

    @Value("${housekeeping.matrix.shift-link.retention-days:5}")
    private int matrixShiftLinkRetentionDays;

    @Value("${housekeeping.ads-task-log.retention-days:2}")
    private int adsTaskLogRetentionDays;

    @Override
    public void execute(JobExecutionContext context) {
        log.info("HOUSEKEEPING_JOB_START shiftLinkLogRetentionDays={} normalShiftLinkRetentionDays={} " +
                        "matrixShiftLinkRetentionDays={} adsTaskLogRetentionDays={}",
                shiftLinkLogRetentionDays, normalShiftLinkRetentionDays, matrixShiftLinkRetentionDays, adsTaskLogRetentionDays);
        try {
            validateRetentionDays();
            purgeShiftLinkLogs();
            purgeNormalShiftLinks();
            purgeMatrixShiftLinks();
            purgeAdsTaskLog();
            log.info("HOUSEKEEPING_JOB_END");
        } catch (Exception ex) {
            log.error("HOUSEKEEPING_JOB_FAILED", ex);
            throw new IllegalStateException("Housekeeping job failed", ex);
        }
    }

    private void purgeShiftLinkLogs() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(shiftLinkLogRetentionDays);
        int deleted = shiftLinkLogRepository.deleteByCreateDateBefore(cutoff);
        log.info("HOUSEKEEPING_SHIFT_LINK_LOG_PURGED cutoff={} deletedCount={}", cutoff, deleted);
    }

    private void purgeNormalShiftLinks() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(normalShiftLinkRetentionDays);
        int deleted = shiftLinkRepository.deleteByCreateDateBeforeAndAdsTypeNormal(cutoff);
        log.info("HOUSEKEEPING_SHIFT_LINK_NORMAL_PURGED cutoff={} deletedCount={}", cutoff, deleted);
    }

    private void purgeMatrixShiftLinks() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(matrixShiftLinkRetentionDays);
        int deleted = shiftLinkRepository.deleteByCreateDateBeforeAndAdsTypeMatrix(cutoff);
        log.info("HOUSEKEEPING_SHIFT_LINK_MATRIX_PURGED cutoff={} deletedCount={}", cutoff, deleted);
    }

    private void purgeAdsTaskLog() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(adsTaskLogRetentionDays);
        int deleted = adsTaskLogRepository.deleteByCreateDateBefore(cutoff);
        log.info("HOUSEKEEPING_ADS_TASK_LOG_PURGED cutoff={} deletedCount={}", cutoff, deleted);
    }

    private void validateRetentionDays() {
        if (shiftLinkLogRetentionDays < 0 || normalShiftLinkRetentionDays < 0
                || matrixShiftLinkRetentionDays < 0 || adsTaskLogRetentionDays < 0) {
            throw new IllegalArgumentException(
                    "Retention days must be >= 0: shiftLinkLogRetentionDays=" + shiftLinkLogRetentionDays
                            + ", normalShiftLinkRetentionDays=" + normalShiftLinkRetentionDays
                            + ", matrixShiftLinkRetentionDays=" + matrixShiftLinkRetentionDays
                            + ", adsTaskLogRetentionDays=" + adsTaskLogRetentionDays);
        }
    }
}
