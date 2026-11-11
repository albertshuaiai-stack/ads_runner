package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.ShiftLinkHistory;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.ShiftLinkHistoryRepository;
import com.admire.cars.runner.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// 历史投放数据录入：单条维护 + 批量粘贴导入，结果汇入 Gmail/平台/商家统计
// history data entry: single edit plus bulk paste import, merged into gmail/platform/merchant stats
@Slf4j
@Service
@Transactional
public class ShiftLinkHistoryService {

    private static final int MAX_NAME_LENGTH = 32;
    private static final int MAX_IMPORT_LINES = 5000;
    private static final Set<String> BANNED_VALUES = Set.of(
            "1", "y", "yes", "true", "banned", "ban", "locked", "lock", "是", "封", "已封", "被封", "封号");
    private static final Set<String> ACTIVE_VALUES = Set.of(
            "", "0", "n", "no", "false", "active", "running", "ok", "否", "正常", "未封");

    private final ShiftLinkHistoryRepository repository;
    private final UserRepository userRepository;

    public ShiftLinkHistoryService(ShiftLinkHistoryRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<ShiftLinkHistory> list(Long currentUserId) {
        User user = getCurrentUser(currentUserId);
        return isAdmin(user)
                ? repository.findAllByOrderByUserNameAscPlatformNameAscAdsNameAsc()
                : repository.findByAdsOwnerOrderByUserNameAscPlatformNameAscAdsNameAsc(user.getUserPhoneNumber());
    }

    // 单条新增，同组已存在则覆盖 / create one row, overwrite when the group already exists
    public ShiftLinkHistory save(ShiftLinkHistory input, Long currentUserId) {
        User user = getCurrentUser(currentUserId);
        normalize(input);
        ShiftLinkHistory saved = upsert(user.getUserPhoneNumber(), input).row();
        log.info("[ShiftLinkHistory] saved owner={} gmail={} platform={} merchant={} links={} banned={}",
                saved.getAdsOwner(), saved.getUserName(), saved.getPlatformName(), saved.getAdsName(),
                saved.getLinkCount(), saved.getBanned());
        return saved;
    }

    public ShiftLinkHistory update(Long id, ShiftLinkHistory input, Long currentUserId) {
        ShiftLinkHistory existing = getOwned(id, currentUserId);
        normalize(input);
        existing.setUserName(input.getUserName());
        existing.setPlatformName(input.getPlatformName());
        existing.setAdsName(input.getAdsName());
        existing.setLinkCount(input.getLinkCount());
        existing.setBanned(input.getBanned());
        existing.setUpdateDate(LocalDateTime.now());
        ShiftLinkHistory saved = repository.save(existing);
        log.info("[ShiftLinkHistory] updated id={} banned={} links={}", id, saved.getBanned(), saved.getLinkCount());
        return saved;
    }

    public void delete(Long id, Long currentUserId) {
        repository.delete(getOwned(id, currentUserId));
        log.info("[ShiftLinkHistory] deleted id={}", id);
    }

    // 批量导入：每行 gmail, platform, merchant, banned[, links]，逗号或 Tab 分隔
    // bulk import: one row per line as gmail, platform, merchant, banned[, links], comma or tab separated
    public ImportResult importText(String text, Long currentUserId) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("Import text is required");
        }
        User user = getCurrentUser(currentUserId);
        String owner = user.getUserPhoneNumber();
        String[] lines = text.split("\\r?\\n");
        if (lines.length > MAX_IMPORT_LINES) {
            throw new IllegalArgumentException("At most " + MAX_IMPORT_LINES + " lines per import");
        }

        int created = 0;
        int updated = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#") || (i == 0 && isHeader(line))) {
                continue;
            }
            try {
                ShiftLinkHistory row = parseLine(line);
                normalize(row);
                if (upsert(owner, row).created()) {
                    created++;
                } else {
                    updated++;
                }
            } catch (IllegalArgumentException e) {
                if (errors.size() < 50) {
                    errors.add("Line " + (i + 1) + ": " + e.getMessage());
                }
            }
        }
        log.info("[ShiftLinkHistory] import done owner={} created={} updated={} failed={}",
                owner, created, updated, errors.size());
        return new ImportResult(created, updated, errors);
    }

    private UpsertResult upsert(String owner, ShiftLinkHistory input) {
        ShiftLinkHistory existing = repository.findByAdsOwnerAndUserNameAndPlatformNameAndAdsName(
                owner, input.getUserName(), input.getPlatformName(), input.getAdsName()).orElse(null);
        if (existing != null) {
            existing.setLinkCount(input.getLinkCount());
            existing.setBanned(input.getBanned());
            existing.setUpdateDate(LocalDateTime.now());
            return new UpsertResult(repository.save(existing), false);
        }
        input.setId(null);
        input.setAdsOwner(owner);
        input.setCreateDate(LocalDateTime.now());
        return new UpsertResult(repository.save(input), true);
    }

    private static ShiftLinkHistory parseLine(String line) {
        String[] parts = line.contains("\t") ? line.split("\t", -1) : line.split("[,，]", -1);
        if (parts.length < 3) {
            throw new IllegalArgumentException("expected gmail, platform, merchant, banned[, links]");
        }
        ShiftLinkHistory row = new ShiftLinkHistory();
        row.setUserName(parts[0]);
        row.setPlatformName(parts[1]);
        row.setAdsName(parts[2]);
        row.setBanned(parts.length > 3 ? parseBanned(parts[3]) : Boolean.FALSE);
        row.setLinkCount(parts.length > 4 ? parseLinkCount(parts[4]) : null);
        return row;
    }

    private static Boolean parseBanned(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (BANNED_VALUES.contains(normalized)) {
            return Boolean.TRUE;
        }
        if (ACTIVE_VALUES.contains(normalized)) {
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("invalid banned value: " + value + " (use yes/no)");
    }

    private static Long parseLinkCount(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid links value: " + value);
        }
    }

    private static boolean isHeader(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.startsWith("gmail") || lower.startsWith("user") || lower.startsWith("email");
    }

    private static void normalize(ShiftLinkHistory row) {
        if (row == null) {
            throw new IllegalArgumentException("history row is required");
        }
        row.setUserName(requireName(row.getUserName(), "gmail"));
        row.setPlatformName(requireName(row.getPlatformName(), "platform"));
        row.setAdsName(requireName(row.getAdsName(), "merchant"));
        // 未填链接数按 1 计，表示投放过该商家 / blank links count as 1, meaning the merchant was promoted
        if (row.getLinkCount() == null) {
            row.setLinkCount(1L);
        }
        if (row.getLinkCount() < 0) {
            throw new IllegalArgumentException("links must not be negative");
        }
        if (row.getBanned() == null) {
            row.setBanned(Boolean.FALSE);
        }
    }

    private static String requireName(String value, String field) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(field + " must be at most " + MAX_NAME_LENGTH + " characters");
        }
        return trimmed;
    }

    private ShiftLinkHistory getOwned(Long id, Long currentUserId) {
        ShiftLinkHistory existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("SHIFT_LINK_HISTORY not found: " + id));
        User user = getCurrentUser(currentUserId);
        if (!isAdmin(user) && !user.getUserPhoneNumber().equals(existing.getAdsOwner())) {
            throw new IllegalArgumentException("Unauthorized: you can only modify your own history data");
        }
        return existing;
    }

    private User getCurrentUser(Long currentUserId) {
        return userRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found: " + currentUserId));
    }

    private static boolean isAdmin(User user) {
        return user.getUserRole() != null && user.getUserRole().toLowerCase(Locale.ROOT).contains("admin");
    }

    private record UpsertResult(ShiftLinkHistory row, boolean created) {
    }

    public record ImportResult(int createdCount, int updatedCount, List<String> errors) {
    }
}
