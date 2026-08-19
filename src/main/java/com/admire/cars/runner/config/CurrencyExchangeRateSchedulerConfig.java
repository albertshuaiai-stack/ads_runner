package com.admire.cars.runner.config;

import com.admire.cars.runner.job.CurrencyExchangeRateJob;
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
public class CurrencyExchangeRateSchedulerConfig {

    private static final Logger log = LoggerFactory.getLogger(CurrencyExchangeRateSchedulerConfig.class);

    private static final String JOB_NAME = "currency-exchange-rate-job";
    private static final String JOB_GROUP = "CURRENCY_EXCHANGE_RATE";
    private static final String TRIGGER_NAME = "currency-exchange-rate-trigger";

    /** Daily at 04:00 UTC */
    private static final String CRON_DAILY_UTC_0400 = "0 0 4 * * ?";

    @Bean
    public JobDetail currencyExchangeRateJobDetail() {
        return JobBuilder.newJob(CurrencyExchangeRateJob.class)
                .withIdentity(JOB_NAME, JOB_GROUP)
                .withDescription("Daily currency exchange rate sync: fetch USD to CNY and store in EXCHANGE_RATE")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger currencyExchangeRateTrigger(JobDetail currencyExchangeRateJobDetail) {
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(TRIGGER_NAME, JOB_GROUP)
                .forJob(currencyExchangeRateJobDetail)
                .withSchedule(CronScheduleBuilder
                        .cronSchedule(CRON_DAILY_UTC_0400)
                        .inTimeZone(TimeZone.getTimeZone("UTC"))
                        .withMisfireHandlingInstructionDoNothing())
                .build();
        log.info("CURRENCY_EXCHANGE_RATE_TRIGGER_REGISTERED cron='{}' timezone=UTC", CRON_DAILY_UTC_0400);
        return trigger;
    }
}
