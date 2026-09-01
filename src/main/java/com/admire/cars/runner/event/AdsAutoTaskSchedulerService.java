package com.admire.cars.runner.event;

import com.admire.cars.runner.constant.Constant;
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
            String jobName = buildJobName(event);
            JobKey jobKey = JobKey.jobKey(jobName, groupName);
            TriggerKey triggerKey = TriggerKey.triggerKey(buildTriggerName(event.adsType(), event.adsId()), groupName);
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

                // Always replace all existing schedules for this adsId on update,
                // then create a fresh job+trigger from the latest config.
                replaceScheduleForAdsId(event, groupName, triggerKey);
                scheduler.scheduleJob(jobDetail, trigger);
                
                // Debug: log trigger state
                TriggerKey tk = TriggerKey.triggerKey(buildTriggerName(event.adsType(), event.adsId()), buildGroupName(event.adsOwner(), event.adsType()));
                Trigger savedTrigger = scheduler.getTrigger(tk);
                log.info("AUTO_JOB_SCHEDULED jobGroup={} jobId={} intervalMinutes={} adsType={} nextFireTime={} finalFireTime={}",
                        groupName, jobKey.getName(), event.intervalTime(), event.adsType(), 
                        savedTrigger != null ? savedTrigger.getNextFireTime() : "NULL",
                        savedTrigger != null ? savedTrigger.getFinalFireTime() : "NULL");
            }

            boolean currentJobExists = scheduler.checkExists(jobKey);
            if ("PAUSED".equalsIgnoreCase(event.status()) && currentJobExists) {
                scheduler.pauseJob(jobKey);
                if (scheduler.checkExists(triggerKey)) {
                    scheduler.pauseTrigger(triggerKey);
                }
                log.info("AUTO_JOB_PAUSED jobGroup={} jobId={}", groupName, jobKey.getName());
            } else if ("RUNNING".equalsIgnoreCase(event.status()) && currentJobExists) {
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
            JobKey jobKey = JobKey.jobKey(buildJobName(event), groupName);
            JobKey legacyJobKey = isNormalAds(event) ? JobKey.jobKey(buildLegacyNormalJobName(event.adsId()), groupName) : null;
            JobKey legacyMatrixJobKey = Constant.ADS_TYPE_MATRIX.equalsIgnoreCase(event.adsType())
                    ? JobKey.jobKey(buildLegacyMatrixJobName(event.adsId()), groupName)
                    : null;
            boolean deleted = false;

            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
                deleted = true;
            }
            if (legacyJobKey != null && scheduler.checkExists(legacyJobKey)) {
                scheduler.deleteJob(legacyJobKey);
                deleted = true;
            }
            if (legacyMatrixJobKey != null && scheduler.checkExists(legacyMatrixJobKey)) {
                scheduler.deleteJob(legacyMatrixJobKey);
                deleted = true;
            }

            if (!deleted) {
                return;
            }
            boolean groupEmpty = scheduler.getJobKeys(GroupMatcher.jobGroupEquals(groupName)).isEmpty();
            log.info("AUTO_JOB_DELETED jobGroup={} jobId={} groupEmpty={}", groupName, jobKey.getName(), groupEmpty);
        } catch (SchedulerException e) {
            throw new IllegalStateException("Failed to delete Quartz auto task", e);
        }
    }

    public ExecuteTimeInfo getExecuteTimeInfo(Long adsId, String adsOwner, String adsType) {
        try {
            String groupName = buildGroupName(adsOwner, adsType);
            TriggerKey triggerKey = TriggerKey.triggerKey(buildTriggerName(adsType, adsId), groupName);
            Trigger trigger = scheduler.getTrigger(triggerKey);
            if (trigger == null) {
                return new ExecuteTimeInfo(null, null);
            }
            return new ExecuteTimeInfo(
                    toLocalDateTime(trigger.getPreviousFireTime()),
                    toLocalDateTime(trigger.getNextFireTime()));
        } catch (SchedulerException e) {
            log.warn("AUTO_JOB_EXECUTE_TIME_RESOLVE_FAILED adsId={} adsOwner={} adsType={}", adsId, adsOwner, adsType, e);
            return new ExecuteTimeInfo(null, null);
        }
    }

    private JobDataMap buildJobDataMap(AdsAutoTaskRegistrationEvent event) {
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put("adsId", event.adsId());
        jobDataMap.put("adsOwner", event.adsOwner());
        jobDataMap.put("adsType", event.adsType());
        jobDataMap.put("jobId", buildJobName(event));
        return jobDataMap;
    }

    private String buildGroupName(String adsOwner, String adsType) {
        return adsOwner + "-" + adsType;
    }

    private String buildJobName(Long adsId) {
        return "ads-task-" + adsId;
    }

    private String buildJobName(AdsAutoTaskRegistrationEvent event) {
        if (isNormalAds(event)) {
            return buildQuartzJobName(event.adsId(), event.adsOwner(), event.campainCountry(), event.platformName(), event.campainName());
        }
        if (Constant.ADS_TYPE_MATRIX.equalsIgnoreCase(event.adsType())) {
            return buildQuartzJobName(event.adsId(), event.adsOwner(), event.campainCountry(), "Matrix", event.campainName());
        }
        return buildJobName(event.adsId());
    }

    private String buildLegacyNormalJobName(Long adsId) {
        return "ads-task-" + adsId;
    }

    private String buildLegacyMatrixJobName(Long adsId) {
        return "matrix-ads-task-" + adsId;
    }

    private String buildTriggerName(String adsType,Long adsId) {
        return adsType + "-trigger-" + adsId;
    }

    private boolean isNormalAds(AdsAutoTaskRegistrationEvent event) {
        return Constant.ADS_TYPE_NORMAL.equalsIgnoreCase(event.adsType());
    }

    private void replaceScheduleForAdsId(AdsAutoTaskRegistrationEvent event, String groupName, TriggerKey triggerKey)
            throws SchedulerException {
        if (scheduler.checkExists(triggerKey)) {
            scheduler.unscheduleJob(triggerKey);
        }

        for (JobKey existingJobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(groupName))) {
            if (isJobForAdsId(existingJobKey.getName(), event.adsId(), event.adsType())) {
                scheduler.deleteJob(existingJobKey);
            }
        }
    }

    private boolean isJobForAdsId(String jobName, Long adsId, String adsType) {
        String idToken = String.valueOf(adsId);
        return jobName.startsWith(idToken + "-")
                || buildLegacyNormalJobName(adsId).equals(jobName)
                || (Constant.ADS_TYPE_MATRIX.equalsIgnoreCase(adsType) && buildLegacyMatrixJobName(adsId).equals(jobName));
    }

    private String buildQuartzJobName(Long adsId, String adsOwner, String campainCountry, String platformName, String campainName) {
        return adsId + "-" + safeToken(adsOwner) + "-" + safeToken(campainCountry) + "-"
                + safeToken(platformName) + "-" + safeToken(campainName);
    }

    private String safeToken(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", "-");
    }

    private Class<? extends AdsAutoTaskJob> resolveJobClass(String adsType) {
        if (Constant.ADS_TYPE_MATRIX.equalsIgnoreCase(adsType)) {
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
