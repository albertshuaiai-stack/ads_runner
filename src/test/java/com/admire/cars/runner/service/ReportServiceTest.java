package com.admire.cars.runner.service;

import com.admire.cars.runner.dto.report.ExpenditureReportItem;
import com.admire.cars.runner.entity.OutcomeType;
import com.admire.cars.runner.entity.ToolOutcome;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

class ReportServiceTest {

    @Test
    void getExpenditureReport_groupsByMonthAndTypeAndConvertsToUsd() {
        ToolOutcomeService toolOutcomeService = Mockito.mock(ToolOutcomeService.class);
        CurrencyExchangeService currencyExchangeService = Mockito.mock(CurrencyExchangeService.class);
        ReportService reportService = new ReportService(toolOutcomeService, currencyExchangeService);

        ToolOutcome mediaByJune = new ToolOutcome();
        mediaByJune.setPayDate(LocalDate.of(2026, 6, 10));
        mediaByJune.setOutcomeType(OutcomeType.MEDIA_BY);
        mediaByJune.setOutcomeAmount(new BigDecimal("100"));
        mediaByJune.setCurrency("CNY");

        ToolOutcome staticIpJune = new ToolOutcome();
        staticIpJune.setPayDate(LocalDate.of(2026, 6, 18));
        staticIpJune.setOutcomeType(OutcomeType.STATIC_IP);
        staticIpJune.setOutcomeAmount(new BigDecimal("20"));
        staticIpJune.setCurrency("USD");

        ToolOutcome staticIpJuly = new ToolOutcome();
        staticIpJuly.setPayDate(LocalDate.of(2026, 7, 2));
        staticIpJuly.setOutcomeType(OutcomeType.STATIC_IP);
        staticIpJuly.setOutcomeAmount(new BigDecimal("50"));
        staticIpJuly.setCurrency("CNY");

        when(toolOutcomeService.findForReport(any(), any(), anyLong()))
                .thenReturn(List.of(mediaByJune, staticIpJune, staticIpJuly));
        when(currencyExchangeService.toUsdUsingLatestRate(new BigDecimal("100"), "CNY"))
                .thenReturn(new BigDecimal("14.0000"));
        when(currencyExchangeService.toUsdUsingLatestRate(new BigDecimal("20"), "USD"))
                .thenReturn(new BigDecimal("20.0000"));
        when(currencyExchangeService.toUsdUsingLatestRate(new BigDecimal("50"), "CNY"))
                .thenReturn(new BigDecimal("7.0000"));

        List<ExpenditureReportItem> result = reportService.getExpenditureReport(
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 7, 31),
                1L);

        assertEquals(3, result.size());
        assertEquals("202606", result.get(0).payMonth());
        assertEquals("Media By", result.get(0).payType());
        assertEquals("$", result.get(0).payCurrency());
        assertEquals(new BigDecimal("14.0000"), result.get(0).payAmount());

        assertEquals("202606", result.get(1).payMonth());
        assertEquals("Static IP", result.get(1).payType());
        assertEquals(new BigDecimal("20.0000"), result.get(1).payAmount());

        assertEquals("202607", result.get(2).payMonth());
        assertEquals("Static IP", result.get(2).payType());
        assertEquals(new BigDecimal("7.0000"), result.get(2).payAmount());
    }
}
