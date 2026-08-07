package com.admire.cars.runner.dto.dashboard;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ShiftLinkMatrixDashboardItem(
        @JsonProperty("Campaign Name") String campaignName,
        @JsonProperty("Total Capacity") long totalCapacity,
        @JsonProperty("Consumed") long consumed,
        @JsonProperty("To Be Consumed") long toBeConsumed
) {
}
