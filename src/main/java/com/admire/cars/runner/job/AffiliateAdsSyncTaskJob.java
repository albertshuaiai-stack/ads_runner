package com.admire.cars.runner.job;

import com.admire.cars.runner.service.AffiliateAdsSyncTaskAsyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class AffiliateAdsSyncTaskJob implements Job {

    @Autowired
    private AffiliateAdsSyncTaskAsyncService asyncService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap jobDataMap = context.getMergedJobDataMap();
        Long taskId = jobDataMap.getLongValue("taskId");
        if (taskId == null || taskId <= 0) {
            throw new JobExecutionException("taskId is required in job data map");
        }
        asyncService.syncAdsNow(taskId);
    }
}
