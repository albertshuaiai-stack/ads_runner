package com.admire.cars.runner.dto.dashboard;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record BalanceByPlatformItem(
        @JsonProperty("platformName") String platformName,
        @JsonProperty("Total Balance") BigDecimal totalBalance
) {
}
