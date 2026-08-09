package com.admire.cars.runner.dto.dashboard;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record IncomeExpenditureItem(
        @JsonProperty("month") String month,
        @JsonProperty("income") BigDecimal income,
        @JsonProperty("expenditure") BigDecimal expenditure,
        @JsonProperty("currency") String currency
) {
}
