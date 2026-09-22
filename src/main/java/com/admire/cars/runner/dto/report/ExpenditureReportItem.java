package com.admire.cars.runner.dto.report;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record ExpenditureReportItem(
        @JsonProperty("payMonth") String payMonth,
        @JsonProperty("payType") String payType,
        @JsonProperty("PayCurrency") String payCurrency,
        @JsonProperty("PayAmount") BigDecimal payAmount
) {
}
