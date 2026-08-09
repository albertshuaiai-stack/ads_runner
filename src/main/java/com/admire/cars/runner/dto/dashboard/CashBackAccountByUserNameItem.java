package com.admire.cars.runner.dto.dashboard;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CashBackAccountByUserNameItem(
        @JsonProperty("userName") String userName,
        @JsonProperty("Total") long total,
        @JsonProperty("Running") long running,
        @JsonProperty("Paused") long paused,
        @JsonProperty("Locked") long locked
) {
}
