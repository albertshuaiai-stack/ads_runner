package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsNormalPostBack;
import com.admire.cars.runner.repository.AdsNormalPostBackRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 导入联盟后台导出的历史账单 CSV, 按订单号幂等, 并复用现有归因逻辑归到 normaltask
 * Import historical affiliate bills; idempotent by order no, attributed with the existing rules
 */
@Slf4j
@Service
public class PostBackImportService {

    public record ImportResult(int totalRows, int imported, int skipped, int matched, int partial, int unmatched) {
    }

    private final AdsNormalPostBackRepository postBackRepository;
    private final PostBackAttributionService postBackAttributionService;

    public PostBackImportService(AdsNormalPostBackRepository postBackRepository,
                                 PostBackAttributionService postBackAttributionService) {
        this.postBackRepository = postBackRepository;
        this.postBackAttributionService = postBackAttributionService;
    }

    @Transactional
    public ImportResult importCsvForOwner(String adsOwner, InputStream input) throws IOException {
        if (!StringUtils.hasText(adsOwner)) {
            throw new IllegalArgumentException("adsOwner is required");
        }

        List<String> lines;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            lines = reader.lines().filter(StringUtils::hasText).toList();
        }
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("CSV is empty");
        }

        Map<String, Integer> header = headerIndex(parseLine(lines.get(0)));
        if (!header.containsKey("orderid")) {
            throw new IllegalArgumentException("CSV must contain an 'Order ID' column");
        }

        int total = 0;
        int imported = 0;
        int skipped = 0;
        int matched = 0;
        int partial = 0;
        int unmatched = 0;
        for (int i = 1; i < lines.size(); i++) {
            total++;
            AdsNormalPostBack postBack = toPostBack(parseLine(lines.get(i)), header, adsOwner);
            if (postBack == null) {
                skipped++;
                continue;
            }
            if (postBackRepository.findByAdsOwnerAndOrderNo(adsOwner, postBack.getOrderNo()).isPresent()) {
                skipped++;
                continue;
            }
            postBackRepository.save(postBack);
            postBackAttributionService.attribute(adsOwner, postBack.getOrderNo());
            String status = postBack.getMatchStatus();
            if (PostBackAttributionService.MATCHED.equals(status)) {
                matched++;
            } else if (PostBackAttributionService.PARTIAL.equals(status)) {
                partial++;
            } else {
                unmatched++;
            }
            imported++;
        }
        log.info("POSTBACK_IMPORT_DONE owner={} total={} imported={} skipped={} matched={} partial={} unmatched={}",
                adsOwner, total, imported, skipped, matched, partial, unmatched);
        return new ImportResult(total, imported, skipped, matched, partial, unmatched);
    }

    private AdsNormalPostBack toPostBack(List<String> cells, Map<String, Integer> header, String adsOwner) {
        String orderNo = get(cells, header, "orderid");
        if (!StringUtils.hasText(orderNo)) {
            return null;
        }
        AdsNormalPostBack postBack = new AdsNormalPostBack();
        postBack.setAdsOwner(adsOwner);
        postBack.setAffiliateSite("BonusArrive");
        postBack.setOrderNo(orderNo);
        postBack.setOrderTime(get(cells, header, "actiontime"));
        postBack.setStatus(get(cells, header, "status"));
        postBack.setAdvertiserShopName(get(cells, header, "advertisername"));
        postBack.setAdvertiserShopId(get(cells, header, "advertiserid"));
        postBack.setOrderAmount(decimal(get(cells, header, "gmv")));
        postBack.setUserCommissionAmount(decimal(get(cells, header, "commission")));
        postBack.setSubId(get(cells, header, "subid"));
        // Sub ID2 / click_time 不在账单里 / not present in the exported bill
        return postBack;
    }

    private Map<String, Integer> headerIndex(List<String> headerCells) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < headerCells.size(); i++) {
            String key = headerCells.get(i)
                    .replace("\uFEFF", "").trim().toLowerCase()
                    .replace(" ", "").replace("_", "");
            index.put(key, i);
        }
        return index;
    }

    private String get(List<String> cells, Map<String, Integer> header, String key) {
        Integer position = header.get(key);
        if (position == null || position >= cells.size()) {
            return null;
        }
        String value = cells.get(position).trim();
        return value.isEmpty() ? null : value;
    }

    private BigDecimal decimal(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // 带引号字段的 CSV 行解析 / CSV line parser supporting quoted cells
    private List<String> parseLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                cells.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString().trim());
        return cells;
    }
}
