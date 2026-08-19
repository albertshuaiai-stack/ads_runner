package com.admire.cars.runner.job;

import com.admire.cars.runner.service.CurrencyExchangeRateSyncService;
import org.junit.jupiter.api.Test;
import org.quartz.JobExecutionContext;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verify;

class CurrencyExchangeRateJobTest {

    @Test
    void execute_invokesSyncService() {
        CurrencyExchangeRateSyncService syncService = Mockito.mock(CurrencyExchangeRateSyncService.class);
        CurrencyExchangeRateJob job = new CurrencyExchangeRateJob();
        ReflectionTestUtils.setField(job, "currencyExchangeRateSyncService", syncService);

        job.execute(Mockito.mock(JobExecutionContext.class));

        verify(syncService).syncLatestRate();
    }
}
