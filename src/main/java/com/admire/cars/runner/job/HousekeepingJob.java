package com.admire.cars.runner.job;

import com.admire.cars.runner.repository.ShiftLinkLogRepository;
import com.admire.cars.runner.repository.ShiftLinkRepository;
import com.admire.cars.runner.repository.NormalTaskRedirectLogRepository;
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
    private NormalTaskRedirectLogRepository normalTaskRedirectLogRepository;

    @Value("${housekeeping.shift-link-log.retention-days:7}")
    private int shiftLinkLogRetentionDays;

    @Value("${housekeeping.shift-link.retention-days:1}")
    private int shiftLinkRetentionDays;

    @Value("${housekeeping.normal-task-redirect-log.retention-days:2}")
    private int normalTaskRedirectLogRetentionDays;

    @Override
    public void execute(JobExecutionContext context) {
        log.info("HOUSEKEEPING_JOB_START shiftLinkLogRetentionDays={} shiftLinkRetentionDays={} normalTaskRedirectLogRetentionDays={}",
                shiftLinkLogRetentionDays, shiftLinkRetentionDays, normalTaskRedirectLogRetentionDays);
        try {
            purgeShiftLinkLogs();
            purgeNormalShiftLinks();
            purgeNormalTaskRedirectLog();
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
        LocalDateTime cutoff = LocalDateTime.now().minusDays(shiftLinkRetentionDays);
        int deleted = shiftLinkRepository.deleteByCreateDateBeforeAndAdsTypeNormal(cutoff);
        log.info("HOUSEKEEPING_SHIFT_LINK_NORMAL_PURGED cutoff={} deletedCount={}", cutoff, deleted);
    }


    private void purgeNormalTaskRedirectLog() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(normalTaskRedirectLogRetentionDays);
        int deleted = normalTaskRedirectLogRepository.deleteByCreateDateBefore(cutoff);
        log.info("HOUSEKEEPING_NORMAL_TASK_REDIRECT_LOG_PURGED cutoff={} deletedCount={}", cutoff, deleted);
    }
}
