package com.admire.cars.runner.job;

import com.admire.cars.runner.repository.*;
import com.admire.cars.runner.entity.HouseKeepingLog;
import com.admire.cars.runner.entity.ShiftLinkStat;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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

    @Autowired
    private ShiftLinkStatRepository shiftLinkStatRepository;

    @Value("${housekeeping.shift-link-log.retention-days:7}")
    private int shiftLinkLogRetentionDays;

    @Value("${housekeeping.normal.shift-link.retention-days:1}")
    private int normalShiftLinkRetentionDays;

    @Value("${housekeeping.matrix.shift-link.retention-days:1}")
    private int matrixShiftLinkRetentionDays;

    @Value("${housekeeping.matrix.shift-link.absolute-retention-days:30}")
    private int matrixShiftLinkAbsoluteRetentionDays;

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
        // 删除前先归档计数，保证统计不受影响 / archive counts before deletion so stats stay intact
        archiveExpiredShiftLinks(cutoff, "Normal");
        long deleted = shiftLinkRepository.deleteByCreateDateBeforeAndAdsTypeNormal(cutoff);
        log.info("HOUSEKEEPING_SHIFT_LINK_NORMAL_PURGED cutoff={} deletedCount={}", cutoff, deleted);
        return deleted;
    }

    private long purgeMatrixShiftLinks() {
        LocalDateTime consumedCutoff = LocalDateTime.now().minusDays(matrixShiftLinkRetentionDays);
        archiveExpiredShiftLinks(consumedCutoff, "Matrix");
        long deleted = shiftLinkRepository.deleteByCreateDateBeforeAndAdsTypeMatrix(consumedCutoff);
        log.info("HOUSEKEEPING_SHIFT_LINK_MATRIX_PURGED cutoff={} deletedCount={}", consumedCutoff, deleted);

        // 绝对清理：30 天后无论是否消耗完都删除 / absolute purge after 30 days regardless of consumption
        LocalDateTime absoluteCutoff = LocalDateTime.now().minusDays(matrixShiftLinkAbsoluteRetentionDays);
        archiveExpiredShiftLinks(absoluteCutoff, "Matrix");
        deleted += shiftLinkRepository.deleteMatrixByCreateDateBefore(absoluteCutoff);
        log.info("HOUSEKEEPING_SHIFT_LINK_MATRIX_ABSOLUTE_PURGED cutoff={} deletedCount={}", absoluteCutoff, deleted);
        return deleted;
    }

    // 过期 Shift Link 计数归档进 SHIFT_LINK_STAT / archive expired shift link counts into SHIFT_LINK_STAT
    private void archiveExpiredShiftLinks(LocalDateTime cutoff, String adsType) {
        List<Object[]> rows = shiftLinkRepository.aggregateExpiredByAdsType(cutoff, adsType);
        long archivedCount = 0L;
        for (Object[] row : rows) {
            String owner = row[0] == null ? null : String.valueOf(row[0]);
            String userName = row[1] == null ? null : String.valueOf(row[1]);
            String platformName = row[2] == null ? null : String.valueOf(row[2]);
            String adsName = row[3] == null ? null : String.valueOf(row[3]);
            long linkCount = row[4] instanceof Number number ? number.longValue() : 0L;
            String sampleUrl = row[5] == null ? null : String.valueOf(row[5]);
            if (owner == null || userName == null || platformName == null || adsName == null || linkCount <= 0) {
                continue;
            }

            ShiftLinkStat stat = shiftLinkStatRepository
                    .findByAdsOwnerAndUserNameAndPlatformNameAndAdsName(owner, userName, platformName, adsName)
                    .orElseGet(() -> {
                        ShiftLinkStat created = new ShiftLinkStat();
                        created.setAdsOwner(owner);
                        created.setUserName(userName);
                        created.setPlatformName(platformName);
                        created.setAdsName(adsName);
                        created.setLinkCount(0L);
                        return created;
                    });
            stat.setLinkCount(stat.getLinkCount() + linkCount);
            if (stat.getSampleUrl() == null && sampleUrl != null) {
                stat.setSampleUrl(sampleUrl.length() > 1024 ? sampleUrl.substring(0, 1024) : sampleUrl);
            }
            stat.setUpdateDate(LocalDateTime.now());
            shiftLinkStatRepository.save(stat);
            archivedCount += linkCount;
        }
        if (archivedCount > 0) {
            log.info("HOUSEKEEPING_SHIFT_LINK_ARCHIVED adsType={} cutoff={} archivedCount={}", adsType, cutoff, archivedCount);
        }
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
