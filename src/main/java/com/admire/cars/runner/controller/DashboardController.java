package com.admire.cars.runner.controller;

import com.admire.cars.runner.dto.dashboard.DashboardShiftLinkResponse;
import com.admire.cars.runner.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/shift-link")
    @Operation(summary = "Get shift link dashboard")
    public ResponseEntity<DashboardShiftLinkResponse> getShiftLinkDashboard(HttpServletRequest request) {
        return ResponseEntity.ok(dashboardService.getShiftLinkDashboard(getUserId(request)));
    }

    private Long getUserId(HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        if (uid == null) {
            throw new IllegalArgumentException("userId not found in request");
        }
        return (Long) uid;
    }
}
