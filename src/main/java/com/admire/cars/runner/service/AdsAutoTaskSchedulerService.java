package com.admire.cars.runner.service;

import com.admire.cars.runner.event.AdsAutoTaskRegistrationEvent;
import com.admire.cars.runner.job.AdsAutoTaskJob;
import com.admire.cars.runner.job.MatrixAdsAutoTaskJob;
import com.admire.cars.runner.job.NormalAdsAutoTaskJob;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.Trigger.TriggerState;
import org.quartz.JobBuilder;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class AdsAutoTaskSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(AdsAutoTaskSchedulerService.class);

    private final Scheduler scheduler;

    public AdsAutoTaskSchedulerService(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void upsert(AdsAutoTaskRegistrationEvent event) {
        try {
            String groupName = buildGroupName(event.adsOwner(), event.adsType());
            JobKey jobKey = JobKey.jobKey(buildJobName(event.adsId()), groupName);
            TriggerKey triggerKey = TriggerKey.triggerKey(buildTriggerName(event.adsId()), groupName);
            boolean jobExists = scheduler.checkExists(jobKey);
            Class<? extends AdsAutoTaskJob> jobClass = resolveJobClass(event.adsType());

            if (event.intervalTime() != null && event.intervalTime() > 0) {
                JobDetail jobDetail = JobBuilder.newJob(jobClass)
                        .withIdentity(jobKey)
                        .usingJobData(buildJobDataMap(event))
                        .storeDurably()
                        .build();

                Trigger trigger = TriggerBuilder.newTrigger()
                        .withIdentity(triggerKey)
                        .forJob(jobKey)
                        .startNow()
                        .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                                .withIntervalInMinutes(Math.toIntExact(event.intervalTime()))
                                .repeatForever()
                                .withMisfireHandlingInstructionFireNow())
                        .build();

                if (jobExists) {
                    scheduler.addJob(jobDetail, true, true);
                    if (scheduler.checkExists(triggerKey)) {
                        scheduler.rescheduleJob(triggerKey, trigger);
                    } else {
                        scheduler.scheduleJob(trigger);
                    }
                } else {
                    scheduler.scheduleJob(jobDetail, trigger);
                }
                
                // Debug: log trigger state
                TriggerKey tk = TriggerKey.triggerKey(buildTriggerName(event.adsId()), buildGroupName(event.adsOwner(), event.adsType()));
                Trigger savedTrigger = scheduler.getTrigger(tk);
                log.info("AUTO_JOB_SCHEDULED jobGroup={} jobId={} intervalMinutes={} adsType={} nextFireTime={} finalFireTime={}",
                        groupName, jobKey.getName(), event.intervalTime(), event.adsType(), 
                        savedTrigger != null ? savedTrigger.getNextFireTime() : "NULL",
                        savedTrigger != null ? savedTrigger.getFinalFireTime() : "NULL");
            }

            if ("PAUSED".equalsIgnoreCase(event.status()) && jobExists) {
                scheduler.pauseJob(jobKey);
                log.info("AUTO_JOB_PAUSED jobGroup={} jobId={}", groupName, jobKey.getName());
            } else if ("RUNNING".equalsIgnoreCase(event.status()) && jobExists) {
                TriggerState triggerState = scheduler.getTriggerState(triggerKey);
                if (triggerState == TriggerState.PAUSED) {
                    scheduler.resumeJob(jobKey);
                    scheduler.triggerJob(jobKey);
                    log.info("AUTO_JOB_RESUMED jobGroup={} jobId={} startNow=true", groupName, jobKey.getName());
                } else if (event.intervalTime() == null || event.intervalTime() <= 0) {
                    scheduler.triggerJob(jobKey);
                    log.info("AUTO_JOB_TRIGGERED jobGroup={} jobId={} startNow=true", groupName, jobKey.getName());
                }
            }
        } catch (SchedulerException e) {
            throw new IllegalStateException("Failed to register Quartz auto task", e);
        }
    }

    public void delete(AdsAutoTaskRegistrationEvent event) {
        try {
            String groupName = buildGroupName(event.adsOwner(), event.adsType());
            JobKey jobKey = JobKey.jobKey(buildJobName(event.adsId()), groupName);
            if (!scheduler.checkExists(jobKey)) {
                return;
            }

            scheduler.deleteJob(jobKey);
            boolean groupEmpty = scheduler.getJobKeys(GroupMatcher.jobGroupEquals(groupName)).isEmpty();
            log.info("AUTO_JOB_DELETED jobGroup={} jobId={} groupEmpty={}", groupName, jobKey.getName(), groupEmpty);
        } catch (SchedulerException e) {
            throw new IllegalStateException("Failed to delete Quartz auto task", e);
        }
    }

    public ExecuteTimeInfo getExecuteTimeInfo(Long adsId, String adsOwner, String adsType) {
        try {
            String groupName = buildGroupName(adsOwner, adsType);
            TriggerKey triggerKey = TriggerKey.triggerKey(buildTriggerName(adsId), groupName);
            Trigger trigger = scheduler.getTrigger(triggerKey);
            if (trigger == null) {
                return new ExecuteTimeInfo(null, null);
            }
            return new ExecuteTimeInfo(
                    toLocalDateTime(trigger.getPreviousFireTime()),
                    toLocalDateTime(trigger.getNextFireTime()));
        } catch (SchedulerException e) {
            throw new IllegalStateException("Failed to resolve Quartz auto task execution times", e);
        }
    }

    private JobDataMap buildJobDataMap(AdsAutoTaskRegistrationEvent event) {
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put("adsId", event.adsId());
        jobDataMap.put("adsOwner", event.adsOwner());
        jobDataMap.put("adsType", event.adsType());
        return jobDataMap;
    }

    private String buildGroupName(String adsOwner, String adsType) {
        return adsOwner + "-" + adsType;
    }

    private String buildJobName(Long adsId) {
        return "ads-task-" + adsId;
    }

    private String buildTriggerName(Long adsId) {
        return "ads-trigger-" + adsId;
    }

    private Class<? extends AdsAutoTaskJob> resolveJobClass(String adsType) {
        if ("Matrix".equalsIgnoreCase(adsType)) {
            return MatrixAdsAutoTaskJob.class;
        }
        return NormalAdsAutoTaskJob.class;
    }

    private LocalDateTime toLocalDateTime(java.util.Date value) {
        if (value == null) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(value.getTime()), ZoneId.systemDefault());
    }

    public record ExecuteTimeInfo(LocalDateTime lastExecuteTime, LocalDateTime nextExecuteTime) {
    }
}
