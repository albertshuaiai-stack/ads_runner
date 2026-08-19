package com.admire.cars.runner.job;

import com.admire.cars.runner.service.CurrencyExchangeRateSyncService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

@DisallowConcurrentExecution
public class CurrencyExchangeRateJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(CurrencyExchangeRateJob.class);

    @Autowired
    private CurrencyExchangeRateSyncService currencyExchangeRateSyncService;

    @Override
    public void execute(JobExecutionContext context) {
        log.info("CURRENCY_EXCHANGE_RATE_JOB_START");
        try {
            currencyExchangeRateSyncService.syncLatestRate();
            log.info("CURRENCY_EXCHANGE_RATE_JOB_END");
        } catch (Exception ex) {
            log.error("CURRENCY_EXCHANGE_RATE_JOB_FAILED", ex);
            throw new IllegalStateException("Currency exchange rate job failed", ex);
        }
    }
}
