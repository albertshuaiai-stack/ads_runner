package com.admire.cars.runner.controller;

import com.admire.cars.runner.dto.report.ExpenditureReportItem;
import com.admire.cars.runner.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping({"/api/report", "/report"})
@Tag(name = "Report")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/expenditure")
    @Operation(summary = "Get expenditure report grouped by pay month and pay type")
    public ResponseEntity<List<ExpenditureReportItem>> getExpenditureReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate payDateBegin,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate payDateEnd,
            HttpServletRequest request) {
        return ResponseEntity.ok(reportService.getExpenditureReport(payDateBegin, payDateEnd, getUserId(request)));
    }

    private Long getUserId(HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        if (uid == null) {
            throw new IllegalArgumentException("userId not found in request");
        }
        return (Long) uid;
    }
}
