package com.admire.cars.runner.job;

import org.quartz.Job;
import org.quartz.JobExecutionContext;

/**
 * Compatibility job for legacy Quartz rows persisted with the old class name.
 */
public class MyBatchJob implements Job {

    @Override
    public void execute(JobExecutionContext context) {
        // Intentionally empty.
    }
}
