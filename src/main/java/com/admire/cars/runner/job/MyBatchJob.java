package com.admire.cars.runner.job;

import org.quartz.Job;
import org.quartz.JobExecutionContext;

public class MyBatchJob implements Job{

    @Override
    public void execute(JobExecutionContext context) {
        System.out.println("Quartz job running...");
    }

}
