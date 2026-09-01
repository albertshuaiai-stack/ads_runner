package com.admire.cars.runner.job;

import com.admire.cars.runner.repository.*;
import com.admire.cars.runner.entity.HouseKeepingLog;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDate;
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

    @Autowired
    private AdsMatrixInfoRepository adsMatrixInfoRepository;

    @Autowired
    private AdsNormalInfoRepository adsNormalInfoRepository;


    @Autowired
    private HouseKeepingLogRepository houseKeepingLogRepository;

    @Value("${housekeeping.shift-link-log.retention-days:7}")
    private int shiftLinkLogRetentionDays;

    @Value("${housekeeping.normal.shift-link.retention-days:1}")
    private int normalShiftLinkRetentionDays;

    @Value("${housekeeping.matrix.shift-link.retention-days:1}")
    private int matrixShiftLinkRetentionDays;

    @Value("${housekeeping.ads-task-log.retention-days:1}")
    private int adsTaskLogRetentionDays;

    @Override
    public void execute(JobExecutionContext context) {
        log.info("HOUSEKEEPING_JOB_START shiftLinkLogRetentionDays={} normalShiftLinkRetentionDays={} " +
                        "matrixShiftLinkRetentionDays={} adsTaskLogRetentionDays={}",
                shiftLinkLogRetentionDays, normalShiftLinkRetentionDays, matrixShiftLinkRetentionDays, adsTaskLogRetentionDays);
        
        final LocalDateTime jobStartTime = LocalDateTime.now();
        HouseKeepingLog houseKeepingLog = new HouseKeepingLog();
        houseKeepingLog.setStartDate(jobStartTime);
        houseKeepingLog.setHouseKeepingDate(jobStartTime.toLocalDate());
        
        try {
            validateRetentionDays();
            
            long purgeShiftLinkLog = purgeShiftLinkLogs();
            houseKeepingLog.setPurgeShiftLinkLog(purgeShiftLinkLog);
            
            long purgeNormalShiftLink = purgeNormalShiftLinks();
            houseKeepingLog.setPurgeNormalShiftLink(purgeNormalShiftLink);
            
            long purgeMatrixShiftLink = purgeMatrixShiftLinks();
            houseKeepingLog.setPurgeMatrixShiftLing(purgeMatrixShiftLink);
            
            long purgeAdsTaskLog = purgeAdsTaskLog();
            houseKeepingLog.setPurgeAdsTaskLog(purgeAdsTaskLog);
            
            final LocalDateTime jobEndTime = LocalDateTime.now();
            houseKeepingLog.setEndDate(jobEndTime);
            long durationMillis = java.time.temporal.ChronoUnit.MILLIS.between(jobStartTime, jobEndTime);
            houseKeepingLog.setDuration(durationMillis);
            
            houseKeepingLogRepository.save(houseKeepingLog);
            log.info("HOUSEKEEPING_JOB_END totalPurged={}", 
                    purgeShiftLinkLog + purgeNormalShiftLink + purgeMatrixShiftLink + purgeAdsTaskLog);

           int revertMatrixCount = adsMatrixInfoRepository.revertSuccessAndFailedCount();
           int revertNormalCount = adsNormalInfoRepository.revertSuccessAndFailedCount();

           log.info("HOUSEKEEPING_REVERT_SUCCESS_AND_FAILED_COUNT matrixCount={} normalCount={}", revertMatrixCount, revertNormalCount);

        } catch (Exception ex) {
            log.error("HOUSEKEEPING_JOB_FAILED", ex);
            throw new IllegalStateException("Housekeeping job failed", ex);
        }
    }

    private long purgeShiftLinkLogs() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(shiftLinkLogRetentionDays);
        long deleted = shiftLinkLogRepository.deleteByCreateDateBefore(cutoff);
        log.info("HOUSEKEEPING_SHIFT_LINK_LOG_PURGED cutoff={} deletedCount={}", cutoff, deleted);
        return deleted;
    }

    private long purgeNormalShiftLinks() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(normalShiftLinkRetentionDays);
        long deleted = shiftLinkRepository.deleteByCreateDateBeforeAndAdsTypeNormal(cutoff);
        log.info("HOUSEKEEPING_SHIFT_LINK_NORMAL_PURGED cutoff={} deletedCount={}", cutoff, deleted);
        return deleted;
    }

    private long purgeMatrixShiftLinks() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(matrixShiftLinkRetentionDays);
        long deleted = shiftLinkRepository.deleteByCreateDateBeforeAndAdsTypeMatrix(cutoff);
        log.info("HOUSEKEEPING_SHIFT_LINK_MATRIX_PURGED cutoff={} deletedCount={}", cutoff, deleted);
        return deleted;
    }

    private long purgeAdsTaskLog() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(adsTaskLogRetentionDays);
        long deleted = adsTaskLogRepository.deleteByCreateDateBefore(cutoff);
        log.info("HOUSEKEEPING_ADS_TASK_LOG_PURGED cutoff={} deletedCount={}", cutoff, deleted);
        return deleted;
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
