package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.HouseKeepingLog;
import com.admire.cars.runner.service.HouseKeepingLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/house-keeping")
public class HouseKeepingController {

    private final HouseKeepingLogService houseKeepingLogService;

    public HouseKeepingController(HouseKeepingLogService houseKeepingLogService) {
        this.houseKeepingLogService = houseKeepingLogService;
    }

    @GetMapping
    public ResponseEntity<Page<HouseKeepingLog>> getAllHouseKeepingLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        Page<HouseKeepingLog> logs = houseKeepingLogService.getAllHouseKeepingLogs(
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "houseKeepingDate").and(Sort.by(Sort.Direction.DESC, "id"))));
        return ResponseEntity.ok(logs);
    }
}
