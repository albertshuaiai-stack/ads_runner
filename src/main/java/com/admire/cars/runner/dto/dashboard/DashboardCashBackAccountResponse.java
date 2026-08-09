package com.admire.cars.runner.dto.dashboard;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record DashboardCashBackAccountResponse(
        @JsonProperty("AccountByUserName") List<CashBackAccountByUserNameItem> accountByUserName,
        @JsonProperty("AccountByPlatform") List<CashBackAccountByPlatformItem> accountByPlatform,
        @JsonProperty("BalanceByUserName") List<BalanceByUserNameItem> balanceByUserName,
        @JsonProperty("BalanceByPlatform") List<BalanceByPlatformItem> balanceByPlatform
) {
}
