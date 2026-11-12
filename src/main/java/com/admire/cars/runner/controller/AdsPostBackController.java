package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.AdsNormalPostBack;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.service.AdsNormalPostBackService;
import com.admire.cars.runner.service.PostBackAttributionService;
import com.admire.cars.runner.service.PostBackImportService;
import com.admire.cars.runner.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/postback")
public class AdsPostBackController {

    private final AdsNormalPostBackService adsNormalPostBackService;

    private final PostBackAttributionService postBackAttributionService;

    private final PostBackImportService postBackImportService;

    private final UserService userService;

    public AdsPostBackController(AdsNormalPostBackService adsNormalPostBackService,
                                 PostBackAttributionService postBackAttributionService,
                                 PostBackImportService postBackImportService,
                                 UserService userService) {
        this.adsNormalPostBackService = adsNormalPostBackService;
        this.postBackAttributionService = postBackAttributionService;
        this.postBackImportService = postBackImportService;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<Page<AdsNormalPostBack>> search(
            @RequestParam(required = false) String adsOwner,
            @RequestParam(required = false) String affiliateSite,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        try {
            Page<AdsNormalPostBack> postBacks = adsNormalPostBackService.search(
                    adsOwner,
                    affiliateSite,
                    orderNo,
                    status,
                    getUserIdOrNull(request),
                    PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id")));
            return ResponseEntity.ok(postBacks);
        } catch (IllegalArgumentException ex) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    // 导入联盟后台导出的历史账单 CSV / import a historical affiliate bill csv
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> importCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "platform", required = false) String platform,
            HttpServletRequest request) {
        Map<String, Object> body = new HashMap<>();
        if (file == null || file.isEmpty()) {
            return badRequest(new IllegalArgumentException("CSV file is required"));
        }
        try {
            Long userId = getUserIdOrNull(request);
            if (userId == null) {
                throw new IllegalArgumentException("Unauthorized");
            }
            User user = userService.getUserById(userId);
            PostBackImportService.ImportResult result = postBackImportService
                    .importCsvForOwner(user.getUserPhoneNumber(), platform, file.getInputStream());
            body.put("success", true);
            body.put("totalRows", result.totalRows());
            body.put("imported", result.imported());
            body.put("skipped", result.skipped());
            body.put("matched", result.matched());
            body.put("partial", result.partial());
            body.put("unmatched", result.unmatched());
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        } catch (IOException ex) {
            return badRequest(new IllegalArgumentException("Cannot read CSV file"));
        }
    }

    // Click 区数据 / click tab data
    @GetMapping("/click-summary")
    public ResponseEntity<Map<String, Object>> clickSummary(
            @RequestParam(required = false) String adsOwner,
            @RequestParam(defaultValue = "500") int size,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(postBackAttributionService.clickSummary(
                    adsOwner, getUserIdOrNull(request), size));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        }
    }

    // 删除单条 Post Back 记录 / delete one post back row
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id, HttpServletRequest request) {
        try {
            adsNormalPostBackService.deleteById(id, getUserIdOrNull(request));
            return ResponseEntity.ok(Map.of("success", true, "deletedId", id));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        }
    }

    // 按后台上架的 normaltask 汇总点击与下单 / click and order stats grouped by listed normal task
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats(
            @RequestParam(required = false) String adsOwner,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(postBackAttributionService.stats(adsOwner, getUserIdOrNull(request)));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        }
    }

    // 点击ID台账 / click id ledger
    @GetMapping("/clicks")
    public ResponseEntity<?> clicks(
            @RequestParam(required = false) String adsOwner,
            @RequestParam(required = false) Long normalAdsId,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(postBackAttributionService.clicks(adsOwner, normalAdsId, getUserIdOrNull(request)));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        }
    }

    // 指定点击ID的下单明细 / orders attributed to one click id
    @GetMapping("/clicks/{clickId}")
    public ResponseEntity<?> clickDetail(
            @PathVariable String clickId,
            @RequestParam(required = false) String adsOwner,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(postBackAttributionService.clickDetail(clickId, adsOwner, getUserIdOrNull(request)));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        }
    }

    // 未归因订单 / orders that could not be attributed
    @GetMapping("/unmatched")
    public ResponseEntity<?> unmatched(
            @RequestParam(required = false) String adsOwner,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        try {
            PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1),
                    Sort.by(Sort.Direction.DESC, "id"));
            return ResponseEntity.ok(postBackAttributionService.unmatched(adsOwner, getUserIdOrNull(request), pageable));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        }
    }

    // 补录点击台账后重跑归因 / re-run attribution after click ids are backfilled
    @PostMapping("/reattribute")
    public ResponseEntity<Map<String, Object>> reattribute(
            @RequestParam(required = false) String adsOwner,
            @RequestParam(defaultValue = "500") int size,
            HttpServletRequest request) {
        try {
            PageRequest pageable = PageRequest.of(0, Math.min(Math.max(size, 1), 5000));
            int matched = postBackAttributionService.reattribute(
                    postBackAttributionService.resolveOwnerForRead(adsOwner, getUserIdOrNull(request)), pageable);
            return ResponseEntity.ok(Map.of("success", true, "matched", matched));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        }
    }

    // 人工把订单绑定到点击ID / manually bind an order to a click id
    @PostMapping("/bind")
    public ResponseEntity<Map<String, Object>> bind(
            @RequestParam String orderNo,
            @RequestParam String clickId,
            @RequestParam(required = false) String adsOwner,
            HttpServletRequest request) {
        if (!StringUtils.hasText(orderNo) || !StringUtils.hasText(clickId)) {
            return badRequest(new IllegalArgumentException("orderNo and clickId are required"));
        }
        try {
            postBackAttributionService.bind(resolveOwnerForWrite(adsOwner, request), orderNo.trim(), clickId.trim());
            return ResponseEntity.ok(Map.of("success", true, "orderNo", orderNo.trim(), "clickId", clickId.trim()));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex);
        }
    }

    private String resolveOwnerForWrite(String adsOwner, HttpServletRequest request) {
        return postBackAttributionService.resolveOwnerForWrite(adsOwner, getUserIdOrNull(request));
    }

    private ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    private Long getUserIdOrNull(HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        if (uid == null) {
            return null;
        }
        return (Long) uid;
    }

}
