package com.admire.cars.runner.config;

import com.admire.cars.runner.job.HousekeepingJob;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

@Configuration
public class HousekeepingSchedulerConfig {

    private static final Logger log = LoggerFactory.getLogger(HousekeepingSchedulerConfig.class);

    private static final String JOB_NAME = "housekeeping-job";
    private static final String JOB_GROUP = "HOUSEKEEPING";
    private static final String TRIGGER_NAME = "housekeeping-trigger";

    /** Daily at 04:00 UTC */
    private static final String CRON_DAILY_UTC_0400 = "0 0 4 * * ?";

    @Bean
    public JobDetail housekeepingJobDetail() {
        return JobBuilder.newJob(HousekeepingJob.class)
                .withIdentity(JOB_NAME, JOB_GROUP)
                .withDescription("Daily housekeeping: purge expired shift_link_log and normal shift_link records")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger housekeepingTrigger(JobDetail housekeepingJobDetail) {
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(TRIGGER_NAME, JOB_GROUP)
                .forJob(housekeepingJobDetail)
                .withSchedule(CronScheduleBuilder
                        .cronSchedule(CRON_DAILY_UTC_0400)
                        .inTimeZone(TimeZone.getTimeZone("UTC"))
                        .withMisfireHandlingInstructionDoNothing())
                .build();
        log.info("HOUSEKEEPING_TRIGGER_REGISTERED cron='{}' timezone=UTC", CRON_DAILY_UTC_0400);
        return trigger;
    }
}
