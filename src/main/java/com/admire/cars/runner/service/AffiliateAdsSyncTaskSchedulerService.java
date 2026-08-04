package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AffiliateAdsSyncConfig;
import com.admire.cars.runner.entity.AffiliateAdsSyncTask;
import com.admire.cars.runner.job.AffiliateAdsSyncTaskJob;
import com.admire.cars.runner.repository.AffiliateAdsSyncConfigRepository;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class AffiliateAdsSyncTaskSchedulerService {

    private final Scheduler scheduler;
    private final AffiliateAdsSyncTaskService affiliateAdsSyncTaskService;
    private final AffiliateAdsSyncConfigRepository affiliateAdsSyncConfigRepository;

    public AffiliateAdsSyncTaskSchedulerService(
            Scheduler scheduler,
            AffiliateAdsSyncTaskService affiliateAdsSyncTaskService,
            AffiliateAdsSyncConfigRepository affiliateAdsSyncConfigRepository) {
        this.scheduler = scheduler;
        this.affiliateAdsSyncTaskService = affiliateAdsSyncTaskService;
        this.affiliateAdsSyncConfigRepository = affiliateAdsSyncConfigRepository;
    }

    public ScheduleInfo scheduleTask(Long taskId, Long currentUserId) {
        AffiliateAdsSyncTask task = affiliateAdsSyncTaskService.getById(taskId, currentUserId);
        if (!"SCHEDULER".equalsIgnoreCase(task.getSyncType())) {
            throw new IllegalArgumentException("syncType must be SCHEDULER to create Quartz job");
        }
        if (!StringUtils.hasText(task.getCron())) {
            throw new IllegalArgumentException("cron is required when syncType is SCHEDULER");
        }

        AffiliateAdsSyncConfig config = affiliateAdsSyncConfigRepository.findById(task.getAffiliateAdsSyncConfigId())
                .orElseThrow(() -> new IllegalArgumentException("AFFILIATE_ADS_SYNC_CONFIG not found: " + task.getAffiliateAdsSyncConfigId()));

        String groupName = buildGroupName(task.getAdsOwner());
        String jobName = buildJobName(task.getAdsOwner(), config.getSyncName(), task.getId());
        String triggerName = buildTriggerName(task.getAdsOwner(), config.getSyncName(), task.getId());

        JobKey jobKey = JobKey.jobKey(jobName, groupName);
        TriggerKey triggerKey = TriggerKey.triggerKey(triggerName, groupName);

        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put("taskId", task.getId());

        JobDetail jobDetail = JobBuilder.newJob(AffiliateAdsSyncTaskJob.class)
                .withIdentity(jobKey)
                .usingJobData(jobDataMap)
                .storeDurably()
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .forJob(jobKey)
                .withSchedule(CronScheduleBuilder.cronSchedule(task.getCron())
                        .withMisfireHandlingInstructionDoNothing())
                .build();

        try {
            if (scheduler.checkExists(jobKey)) {
                scheduler.addJob(jobDetail, true, true);
                if (scheduler.checkExists(triggerKey)) {
                    scheduler.rescheduleJob(triggerKey, trigger);
                } else {
                    scheduler.scheduleJob(trigger);
                }
            } else {
                scheduler.scheduleJob(jobDetail, trigger);
            }
        } catch (SchedulerException e) {
            throw new IllegalStateException("Failed to schedule AFFILIATE_ADS_SYNC_TASK", e);
        }

        return new ScheduleInfo(jobName, triggerName, task.getCron(), groupName);
    }

    private String buildGroupName(String adsOwner) {
        return "AFFILIATE-SYNC-" + safeToken(adsOwner);
    }

    private String buildJobName(String adsOwner, String syncName, Long taskId) {
        return safeToken(adsOwner) + "-" + safeToken(syncName) + "-" + taskId + "-job";
    }

    private String buildTriggerName(String adsOwner, String syncName, Long taskId) {
        return safeToken(adsOwner) + "-" + safeToken(syncName) + "-" + taskId + "-trigger";
    }

    private String safeToken(String value) {
        if (value == null) {
            return "NA";
        }
        return value.trim().replaceAll("[^a-zA-Z0-9_-]+", "-");
    }

    public record ScheduleInfo(String jobName, String triggerName, String cron, String groupName) {
    }
}
