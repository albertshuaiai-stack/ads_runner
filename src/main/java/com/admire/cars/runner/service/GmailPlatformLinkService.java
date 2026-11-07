package com.admire.cars.runner.service;

import com.admire.cars.runner.dto.GmailPlatformStatDto;
import com.admire.cars.runner.dto.GmailPlatformStatsResponse;
import com.admire.cars.runner.dto.MerchantRatingDto;
import com.admire.cars.runner.entity.GmailPlatformLink;
import com.admire.cars.runner.entity.ShiftLinkStat;
import com.admire.cars.runner.entity.ToolAccount;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.GmailPlatformLinkRepository;
import com.admire.cars.runner.repository.ShiftLinkStatRepository;
import com.admire.cars.runner.repository.ToolAccountRepository;
import com.admire.cars.runner.repository.ToolEmailRepository;
import com.admire.cars.runner.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class GmailPlatformLinkService {

    private final GmailPlatformLinkRepository repository;
    private final ToolEmailRepository toolEmailRepository;
    private final ShiftLinkStatRepository shiftLinkStatRepository;
    private final ToolAccountRepository toolAccountRepository;
    private final UserRepository userRepository;

    public GmailPlatformLinkService(GmailPlatformLinkRepository repository,
                                    ToolEmailRepository toolEmailRepository,
                                    ShiftLinkStatRepository shiftLinkStatRepository,
                                    ToolAccountRepository toolAccountRepository,
                                    UserRepository userRepository) {
        this.repository = repository;
        this.toolEmailRepository = toolEmailRepository;
        this.shiftLinkStatRepository = shiftLinkStatRepository;
        this.toolAccountRepository = toolAccountRepository;
        this.userRepository = userRepository;
    }

    public GmailPlatformLink create(GmailPlatformLink rule, Long currentUserId) {
        if (rule == null) {
            throw new IllegalArgumentException("GMAIL_PLATFORM_LINK is required");
        }
        User currentUser = getCurrentUser(currentUserId);
        rule.setAdsOwner(currentUser.getUserPhoneNumber());
        validateAndNormalize(rule);
        rule.setCreateDate(LocalDateTime.now());
        return repository.save(rule);
    }

    @Transactional(readOnly = true)
    public GmailPlatformLink getById(Long id, Long currentUserId) {
        GmailPlatformLink e = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("GMAIL_PLATFORM_LINK not found: " + id));
        ensureAccess(e, currentUserId, "read");
        return e;
    }

    @Transactional(readOnly = true)
    public Page<GmailPlatformLink> search(String userName, String platformName, String adsOwner, Long currentUserId, Pageable pageable) {
        String normalizedUserName = trimToNull(userName);
        String normalizedPlatformName = trimToNull(platformName);
        User currentUser = currentUserId == null ? null : getCurrentUser(currentUserId);
        boolean admin = currentUser != null && isAdmin(currentUser);
        String scopedAdsOwner;
        if (currentUser != null) {
            scopedAdsOwner = admin ? trimToNull(adsOwner) : currentUser.getUserPhoneNumber();
        } else {
            scopedAdsOwner = trimToNull(adsOwner);
        }

        Specification<GmailPlatformLink> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(normalizedUserName)) {
                predicates.add(cb.like(cb.lower(root.get("userName")), "%" + normalizedUserName.toLowerCase() + "%"));
            }
            if (StringUtils.hasText(normalizedPlatformName)) {
                predicates.add(cb.like(cb.lower(root.get("platformName")), "%" + normalizedPlatformName.toLowerCase() + "%"));
            }
            if (StringUtils.hasText(scopedAdsOwner)) {
                predicates.add(cb.equal(cb.lower(root.get("adsOwner")), scopedAdsOwner.toLowerCase()));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
        return repository.findAll(specification, pageable);
    }

    public GmailPlatformLink update(Long id, GmailPlatformLink updateData, Long currentUserId) {
        if (updateData == null) {
            throw new IllegalArgumentException("updateData is required");
        }
        GmailPlatformLink existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("GMAIL_PLATFORM_LINK not found: " + id));
        ensureAccess(existing, currentUserId, "modify");

        if (updateData.getUserName() != null) existing.setUserName(updateData.getUserName());
        if (updateData.getPlatformName() != null) existing.setPlatformName(updateData.getPlatformName());
        if (updateData.getMatchId() != null) existing.setMatchId(updateData.getMatchId());
        if (updateData.getRemarks() != null) existing.setRemarks(updateData.getRemarks());

        validateAndNormalize(existing);
        existing.setUpdateDate(LocalDateTime.now());
        return repository.save(existing);
    }

    public void delete(Long id, Long currentUserId) {
        GmailPlatformLink existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("GMAIL_PLATFORM_LINK not found: " + id));
        ensureAccess(existing, currentUserId, "modify");
        repository.delete(existing);
    }

    // 按唯一 ID 匹配规则 / match a rule by its unique match id
    @Transactional(readOnly = true)
    public GmailPlatformLink matchByUrl(String fullUrl, Long currentUserId) {
        if (!StringUtils.hasText(fullUrl)) {
            return null;
        }
        User currentUser = getCurrentUser(currentUserId);
        boolean admin = isAdmin(currentUser);
        List<GmailPlatformLink> rules = admin ? repository.findAll() : repository.findByAdsOwner(currentUser.getUserPhoneNumber());
        return matchRule(rules, fullUrl);
    }

    // 承载用户 ID 的参数名，按优先级取第一个 / params carrying the user id, first hit wins
    private static final List<String> USER_ID_PARAM_NAMES = List.of(
            "u1", "sid", "subid", "subid1", "aff_sub", "sub_id", "ransiteid");

    // 上传归类：链接提取出的用户 ID 等于规则 Match ID，或链接直接包含 Match ID，最长 ID 胜出
    // classify: extracted user id equals match id, or url simply contains it; longest id wins
    public static GmailPlatformLink matchRule(List<GmailPlatformLink> rules, String fullUrl) {
        if (rules == null || rules.isEmpty() || !StringUtils.hasText(fullUrl)) {
            return null;
        }
        String normalizedUrl = fullUrl.trim().toLowerCase(java.util.Locale.ROOT);
        String extractedUserId = extractUserId(parseQueryParams(rawQueryOf(normalizedUrl)));

        GmailPlatformLink best = null;
        for (GmailPlatformLink rule : rules) {
            String matchId = rule.getMatchId();
            if (!StringUtils.hasText(matchId)) {
                continue;
            }
            String normalizedId = matchId.trim().toLowerCase(java.util.Locale.ROOT);
            boolean hit = normalizedId.equals(extractedUserId) || normalizedUrl.contains(normalizedId);
            if (hit && (best == null || normalizedId.length() > best.getMatchId().trim().length())) {
                best = rule;
            }
        }
        return best;
    }

    private static String rawQueryOf(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            return uri.getRawQuery();
        } catch (java.net.URISyntaxException e) {
            return null;
        }
    }

    private static Map<String, String> parseQueryParams(String rawQuery) {
        Map<String, String> params = new LinkedHashMap<>();
        if (!StringUtils.hasText(rawQuery)) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            if (!StringUtils.hasText(pair)) {
                continue;
            }
            int eq = pair.indexOf('=');
            String name = decode(eq >= 0 ? pair.substring(0, eq) : pair);
            String value = decode(eq >= 0 ? pair.substring(eq + 1) : "");
            if (StringUtils.hasText(name)) {
                params.put(name, value);
            }
        }
        return params;
    }

    private static String decode(String value) {
        try {
            return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }

    // 提取用户 ID：u1=用户IDx点击ID 形式取 x 前段 / extract user id, linksynergy style: head before 'x'
    private static String extractUserId(Map<String, String> params) {
        for (String name : USER_ID_PARAM_NAMES) {
            String value = params.get(name);
            if (!StringUtils.hasText(value)) {
                continue;
            }
            int sep = value.indexOf('x');
            String head = sep > 0 ? value.substring(0, sep) : value;
            if (head.length() >= 3) {
                return head;
            }
        }
        return null;
    }

    // Gmail -> 返利平台 -> 商家 统计 / stats grouped by gmail, platform and merchant
    @Transactional(readOnly = true)
    public GmailPlatformStatsResponse getStats(Long currentUserId) {
        User currentUser = getCurrentUser(currentUserId);
        boolean admin = isAdmin(currentUser);

        // 只读历史聚合表：创建时记录，删除 Shift Link 不影响统计
        // read archived stats only: recorded at creation, deleting shift links never shrinks stats
        List<ShiftLinkStat> archivedStats = admin
                ? shiftLinkStatRepository.findAll()
                : shiftLinkStatRepository.findByAdsOwner(currentUser.getUserPhoneNumber());
        List<Object[]> rows = new ArrayList<>();
        for (ShiftLinkStat archived : archivedStats) {
            rows.add(new Object[]{
                    archived.getUserName(),
                    archived.getPlatformName(),
                    archived.getAdsName(),
                    archived.getLinkCount(),
                    archived.getSampleUrl(),
            });
        }

        // 防御性合并同组重复行 / defensive merge of duplicated group rows
        Map<String, Object[]> mergedRows = new LinkedHashMap<>();
        for (Object[] row : rows) {
            long rowCount = row[3] instanceof Number number ? number.longValue() : 0L;
            String mergeKey = String.valueOf(row[0]) + "|" + String.valueOf(row[1]) + "|" + String.valueOf(row[2]);
            Object[] existing = mergedRows.get(mergeKey);
            if (existing == null) {
                mergedRows.put(mergeKey, new Object[]{row[0], row[1], row[2], rowCount, row[4]});
            } else {
                // 重复行不累加计数 / duplicate rows are not counted
                long existingCount = existing[3] instanceof Number number ? number.longValue() : 0L;
                existing[3] = Math.max(existingCount, rowCount);
                if (existing[4] == null) {
                    existing[4] = row[4];
                }
            }
        }
        rows = new ArrayList<>(mergedRows.values());

        // 返利账号状态表：userName|platform -> status / cash back account status map
        Map<String, String> accountStatusByKey = new LinkedHashMap<>();
        List<ToolAccount> accounts = admin
                ? toolAccountRepository.findAll()
                : toolAccountRepository.findAll().stream()
                        .filter(account -> currentUser.getUserPhoneNumber().equals(account.getAdsOwner()))
                        .toList();
        for (ToolAccount account : accounts) {
            String key = accountKey(account.getUserName(), account.getPlatformName());
            if (key != null) {
                accountStatusByKey.put(key, trimToNull(account.getStatus()));
            }
        }

        Map<String, GmailPlatformStatDto> grouped = new LinkedHashMap<>();
        Map<String, MerchantAccumulator> merchantStats = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String rowUserName = row[0] == null ? "" : String.valueOf(row[0]);
            String rowPlatformName = row[1] == null ? "" : String.valueOf(row[1]);
            String rowAdsName = row[2] == null ? "" : String.valueOf(row[2]);
            long rowLinkCount = row[3] instanceof Number number ? number.longValue() : 0L;
            String sampleUrl = row.length > 4 && row[4] != null ? String.valueOf(row[4]) : null;
            String merchantUrl = extractSiteRoot(sampleUrl);

            String key = accountKey(rowUserName, rowPlatformName);
            GmailPlatformStatDto stat = grouped.computeIfAbsent(key,
                    k -> new GmailPlatformStatDto(rowUserName, rowPlatformName,
                            accountStatusByKey.get(key),
                            0L, 0L, new ArrayList<>()));
            stat.setLinkCount(stat.getLinkCount() + rowLinkCount);
            stat.getMerchants().add(new GmailPlatformStatDto.MerchantStat(rowAdsName, rowLinkCount, merchantUrl));

            // 商家维度累计：推广账号封禁分布 / per-merchant ban distribution of promoting accounts
            MerchantAccumulator merchant = merchantStats.computeIfAbsent(rowAdsName,
                    k -> new MerchantAccumulator(merchantUrl));
            merchant.linkCount += rowLinkCount;
            String status = accountStatusByKey.get(key);
            if (merchant.accountKeys.add(key)) {
                if ("LOCKED".equalsIgnoreCase(status)) {
                    merchant.bannedAccounts++;
                } else {
                    merchant.activeAccounts++;
                }
            }
        }

        List<GmailPlatformStatDto> stats = new ArrayList<>(grouped.values());
        for (GmailPlatformStatDto stat : stats) {
            stat.setMerchantCount(stat.getMerchants().size());
            stat.getMerchants().sort(Comparator.comparing(GmailPlatformStatDto.MerchantStat::getAdsName));
        }
        stats.sort(Comparator.comparing(GmailPlatformStatDto::getUserName)
                .thenComparing(GmailPlatformStatDto::getPlatformName));

        List<MerchantRatingDto> merchants = new ArrayList<>();
        for (Map.Entry<String, MerchantAccumulator> entry : merchantStats.entrySet()) {
            MerchantAccumulator acc = entry.getValue();
            long total = acc.bannedAccounts + acc.activeAccounts;
            // 全封=AVOID，部分封=CAUTION，全活=SAFE / all banned=AVOID, partial=CAUTION, none=SAFE
            String rating = total == 0 ? "UNKNOWN"
                    : acc.bannedAccounts == 0 ? "SAFE"
                    : acc.bannedAccounts == total ? "AVOID" : "CAUTION";
            merchants.add(new MerchantRatingDto(entry.getKey(), acc.linkCount, total,
                    acc.bannedAccounts, acc.activeAccounts, rating, acc.merchantUrl));
        }
        merchants.sort(Comparator.comparing((MerchantRatingDto m) -> ratingOrder(m.getRating()))
                .thenComparing(MerchantRatingDto::getBannedAccounts, Comparator.reverseOrder())
                .thenComparing(MerchantRatingDto::getAdsName));

        return new GmailPlatformStatsResponse(stats, merchants);
    }

    private static int ratingOrder(String rating) {
        return switch (rating) {
            case "AVOID" -> 0;
            case "CAUTION" -> 1;
            case "SAFE" -> 2;
            default -> 3;
        };
    }

    private static String accountKey(String userName, String platformName) {
        if (!StringUtils.hasText(userName) || !StringUtils.hasText(platformName)) {
            return null;
        }
        return userName.trim().toLowerCase(java.util.Locale.ROOT) + "|"
                + platformName.trim().toLowerCase(java.util.Locale.ROOT);
    }

    // 取链接的 scheme://host 作为商家站点根 / take scheme://host as the merchant site root
    private static String extractSiteRoot(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        try {
            java.net.URI uri = new java.net.URI(url.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            return uri.getScheme() + "://" + uri.getHost();
        } catch (java.net.URISyntaxException e) {
            return null;
        }
    }

    // 商家统计累加器 / per-merchant accumulator
    private static class MerchantAccumulator {
        private final String merchantUrl;
        private long linkCount;
        private long bannedAccounts;
        private long activeAccounts;
        private final java.util.Set<String> accountKeys = new java.util.HashSet<>();

        private MerchantAccumulator(String merchantUrl) {
            this.merchantUrl = merchantUrl;
        }
    }

    private void validateAndNormalize(GmailPlatformLink rule) {
        if (!StringUtils.hasText(rule.getAdsOwner())) {
            throw new IllegalArgumentException("adsOwner is required");
        }

        rule.setUserName(trimToNull(rule.getUserName()));
        rule.setPlatformName(trimToNull(rule.getPlatformName()));
        rule.setMatchId(trimToNull(rule.getMatchId()));
        rule.setRemarks(trimToNull(rule.getRemarks()));

        if (rule.getUserName() == null) {
            throw new IllegalArgumentException("userName is required");
        }
        toolEmailRepository.findByUserName(rule.getUserName())
                .orElseThrow(() -> new IllegalArgumentException("TOOL_EMAIL not found by userName: " + rule.getUserName()));
        if (rule.getPlatformName() == null) {
            throw new IllegalArgumentException("platformName is required");
        }
        if (rule.getMatchId() == null) {
            throw new IllegalArgumentException("matchId is required");
        }
        // 太短容易子串误命中 / too short ids cause false substring hits
        if (rule.getMatchId().length() < 4) {
            throw new IllegalArgumentException("matchId must be at least 4 characters");
        }

        validateLength(rule.getUserName(), "userName", 32);
        validateLength(rule.getPlatformName(), "platformName", 128);
        validateLength(rule.getMatchId(), "matchId", 128);
        validateLength(rule.getRemarks(), "remarks", 1024);
    }

    private void validateLength(String value, String fieldName, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must be at most " + maxLength + " characters");
        }
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private void ensureAccess(GmailPlatformLink rule, Long currentUserId, String action) {
        User currentUser = getCurrentUser(currentUserId);
        if (!isAdmin(currentUser) && !currentUser.getUserPhoneNumber().equals(rule.getAdsOwner())) {
            throw new IllegalArgumentException("Unauthorized: you can only " + action + " your own gmail platform links");
        }
    }

    private User getCurrentUser(Long currentUserId) {
        return userRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found: " + currentUserId));
    }

    private boolean isAdmin(User user) {
        return user.getUserRole() != null && user.getUserRole().toLowerCase().contains("admin");
    }
}
