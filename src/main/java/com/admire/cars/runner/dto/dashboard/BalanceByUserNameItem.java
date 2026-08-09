package com.admire.cars.runner.dto.dashboard;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record BalanceByUserNameItem(
        @JsonProperty("userName") String userName,
        @JsonProperty("Total Balance") BigDecimal totalBalance
) {
}
