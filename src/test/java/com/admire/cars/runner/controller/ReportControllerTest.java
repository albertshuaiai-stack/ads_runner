package com.admire.cars.runner.controller;

import com.admire.cars.runner.dto.report.ExpenditureReportItem;
import com.admire.cars.runner.service.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

class ReportControllerTest {

    @Test
    void getExpenditureReport_returnsGroupedItems() {
        ReportService reportService = Mockito.mock(ReportService.class);
        ReportController controller = new ReportController(reportService);
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getAttribute("userId")).thenReturn(1L);

        List<ExpenditureReportItem> payload = List.of(
                new ExpenditureReportItem("202606", "Media By", "$", new BigDecimal("1234.0000")));
        when(reportService.getExpenditureReport(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), 1L)).thenReturn(payload);

        var response = controller.getExpenditureReport(
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30),
                request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("Media By", response.getBody().get(0).payType());
    }
}
