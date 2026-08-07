package com.admire.cars.runner.dto.dashboard;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ShiftLinkNormalDashboardItem(
        @JsonProperty("Campaign Name") String campaignName,
        @JsonProperty("Total Link") long totalLink,
        @JsonProperty("Consumed Link") long consumedLink,
        @JsonProperty("To Be Consumed Link") long toBeConsumedLink
) {
}
