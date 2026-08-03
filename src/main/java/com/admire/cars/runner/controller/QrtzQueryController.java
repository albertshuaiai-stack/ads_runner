package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.QrtzJobDetail;
import com.admire.cars.runner.entity.QrtzTrigger;
import com.admire.cars.runner.service.QrtzQueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/qrtz")
public class QrtzQueryController {

    private final QrtzQueryService qrtzQueryService;

    public QrtzQueryController(QrtzQueryService qrtzQueryService) {
        this.qrtzQueryService = qrtzQueryService;
    }

    @GetMapping("/triggers")
    public ResponseEntity<Page<QrtzTrigger>> queryTriggers(
            @RequestParam(required = false) String schedName,
            @RequestParam(required = false) String triggerName,
            @RequestParam(required = false) String triggerGroup,
            @RequestParam(required = false) String jobName,
            @RequestParam(required = false) String jobGroup,
            @RequestParam(required = false) String triggerState,
            @RequestParam(required = false) String triggerType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        Page<QrtzTrigger> result = qrtzQueryService.searchTriggers(
                schedName,
                triggerName,
                triggerGroup,
                jobName,
                jobGroup,
                triggerState,
                triggerType,
                PageRequest.of(
                        safePage,
                        safeSize,
                        Sort.by(Sort.Direction.DESC, "nextFireTime")
                                .and(Sort.by(Sort.Direction.ASC, "triggerName"))));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/jobs")
    public ResponseEntity<Page<QrtzJobDetail>> queryJobs(
            @RequestParam(required = false) String schedName,
            @RequestParam(required = false) String jobName,
            @RequestParam(required = false) String jobGroup,
            @RequestParam(required = false) String jobClassName,
            @RequestParam(required = false) String description,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        Page<QrtzJobDetail> result = qrtzQueryService.searchJobs(
                schedName,
                jobName,
                jobGroup,
                jobClassName,
                description,
                PageRequest.of(
                        safePage,
                        safeSize,
                        Sort.by(Sort.Direction.ASC, "jobGroup")
                                .and(Sort.by(Sort.Direction.ASC, "jobName"))));
        return ResponseEntity.ok(result);
    }
}
