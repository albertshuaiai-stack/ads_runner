package com.admire.cars.runner.dto.dashboard;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record DashboardShiftLinkResponse(
        @JsonProperty("Normal") List<ShiftLinkNormalDashboardItem> normal,
        @JsonProperty("Matrix") List<ShiftLinkMatrixDashboardItem> matrix
) {
}
