package com.admire.cars.runner.job;

import org.quartz.JobDataMap;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.DisallowConcurrentExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

@DisallowConcurrentExecution
public abstract class AdsAutoTaskJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(AdsAutoTaskJob.class);

    @Override
    public final void execute(JobExecutionContext context) {
        String jobGroup = context.getJobDetail().getKey().getGroup();
        String jobId = context.getJobDetail().getKey().getName();
        JobDataMap jobDataMap = context.getMergedJobDataMap();
        Instant start = Instant.now();
        log.info("AUTO_JOB_START jobGroup={} jobId={} start={} adsType={} adsOwner={} adsId={}",
                jobGroup,
                jobId,
                start,
                jobDataMap.getString("adsType"),
                jobDataMap.getString("adsOwner"),
                jobDataMap.getLongValue("adsId"));
        try {
            executeTask(context);
            log.info("AUTO_JOB_END jobGroup={} jobId={} end={}", jobGroup, jobId, Instant.now());
        } catch (Exception ex) {
            log.error("AUTO_JOB_EXCEPTION jobGroup={} jobId={} end={}", jobGroup, jobId, Instant.now(), ex);
            throw new IllegalStateException("Quartz auto job failed", ex);
        }
    }

    protected abstract void executeTask(JobExecutionContext context);
}
