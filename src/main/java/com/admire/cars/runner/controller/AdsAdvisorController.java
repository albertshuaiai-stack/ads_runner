package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.AdsCampaignPlan;
import com.admire.cars.runner.service.AdsAdvisorService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 投放顾问: 投前方案评估与每日复盘 / ads advisor: pre-launch plan evaluation and daily review
 */
@Slf4j
@RestController
@RequestMapping("/api/advisor")
public class AdsAdvisorController {

    private final AdsAdvisorService advisorService;

    public AdsAdvisorController(AdsAdvisorService advisorService) {
        this.advisorService = advisorService;
    }

    @GetMapping("/plans")
    public ResponseEntity<?> plans(@RequestParam(required = false) String adsOwner, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(advisorService.listPlans(adsOwner, userId(request)));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (RuntimeException ex) {
            return serverError("plans", adsOwner, ex);
        }
    }

    // 只评估不保存, 供填写时预览 / evaluate without saving, used for live preview
    @PostMapping("/plans/evaluate")
    public ResponseEntity<?> evaluate(@RequestBody(required = false) AdsCampaignPlan plan) {
        return ResponseEntity.ok(advisorService.evaluate(plan));
    }

    @PostMapping("/plans")
    public ResponseEntity<?> save(@RequestBody(required = false) AdsCampaignPlan plan,
                                  @RequestParam(required = false) String adsOwner,
                                  HttpServletRequest request) {
        try {
            return ResponseEntity.ok(advisorService.savePlan(plan, adsOwner, userId(request)));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (RuntimeException ex) {
            return serverError("save plan", adsOwner, ex);
        }
    }

    @DeleteMapping("/plans/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, HttpServletRequest request) {
        try {
            advisorService.deletePlan(id, userId(request));
            return ResponseEntity.ok(Map.of("success", true, "deletedId", id));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (RuntimeException ex) {
            return serverError("delete plan", String.valueOf(id), ex);
        }
    }

    @GetMapping("/review")
    public ResponseEntity<?> review(@RequestParam(required = false) String adsOwner, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(advisorService.review(adsOwner, userId(request)));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (RuntimeException ex) {
            return serverError("review", adsOwner, ex);
        }
    }

    // 未预期异常写日志并把原因带回页面 / log unexpected errors and return the reason to the page
    private ResponseEntity<Map<String, Object>> serverError(String action, String adsOwner, RuntimeException ex) {
        log.error("ADS_ADVISOR_FAILED action={} adsOwner={} error={}", action, adsOwner, ex.toString(), ex);
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", action + " failed: " + ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    private Long userId(HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        return uid == null ? null : (Long) uid;
    }
}
