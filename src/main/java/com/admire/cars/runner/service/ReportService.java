package com.admire.cars.runner.service;

import com.admire.cars.runner.dto.report.ExpenditureReportItem;
import com.admire.cars.runner.entity.ToolOutcome;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class ReportService {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");
    private static final String PAY_CURRENCY = "$";

    private final ToolOutcomeService toolOutcomeService;
    private final CurrencyExchangeService currencyExchangeService;

    public ReportService(ToolOutcomeService toolOutcomeService, CurrencyExchangeService currencyExchangeService) {
        this.toolOutcomeService = toolOutcomeService;
        this.currencyExchangeService = currencyExchangeService;
    }

    public List<ExpenditureReportItem> getExpenditureReport(
            LocalDate payDateBegin,
            LocalDate payDateEnd,
            Long currentUserId) {
        List<ToolOutcome> outcomes = toolOutcomeService.findForReport(payDateBegin, payDateEnd, currentUserId);
        Map<String, BigDecimal> aggregated = new TreeMap<>();

        for (ToolOutcome outcome : outcomes) {
            if (outcome.getPayDate() == null || outcome.getOutcomeType() == null || outcome.getOutcomeAmount() == null) {
                continue;
            }
            String payMonth = outcome.getPayDate().format(MONTH_FORMATTER);
            String payType = outcome.getOutcomeType().getDisplayName();
            BigDecimal usdAmount = currencyExchangeService.toUsdUsingLatestRate(
                    outcome.getOutcomeAmount(), outcome.getCurrency());
            String key = payMonth + "|" + payType;
            aggregated.merge(key, usdAmount, BigDecimal::add);
        }

        List<ExpenditureReportItem> result = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : aggregated.entrySet()) {
            String[] parts = entry.getKey().split("\\|", 2);
            result.add(new ExpenditureReportItem(parts[0], parts[1], PAY_CURRENCY, entry.getValue()));
        }

        result.sort(Comparator.comparing(ExpenditureReportItem::payMonth)
                .thenComparing(ExpenditureReportItem::payType, String.CASE_INSENSITIVE_ORDER));
        return result;
    }
}
