package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsPlatform;
import com.admire.cars.runner.entity.GmailPlatformLink;
import com.admire.cars.runner.entity.MiddleLink;
import com.admire.cars.runner.entity.ShiftLink;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.AdsPlatformRepository;
import com.admire.cars.runner.repository.GmailPlatformLinkRepository;
import com.admire.cars.runner.repository.MiddleLinkRepository;
import com.admire.cars.runner.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// 中间链接：上传归类存档 -> 限时解析落地页 -> 归档 Matrix
// middle link: upload and classify -> resolve landing pages within a time window -> archive to Matrix
@Slf4j
@Service
@Transactional
public class MiddleLinkService {

    private static final int MAX_REDIRECT_HOPS = 10;
    private static final int MAX_CONSECUTIVE_FAILURES = 25;
    private static final long MAX_TARGET_COUNT = 200L;
    private static final long MAX_WINDOW_SECONDS = 3600L;
    private static final String RESOLVE_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final MiddleLinkRepository middleLinkRepository;
    private final GmailPlatformLinkRepository gmailPlatformLinkRepository;
    private final AdsPlatformRepository adsPlatformRepository;
    private final UserRepository userRepository;
    private final ShiftLinkService shiftLinkService;
    private final ExecutorService resolveExecutor = Executors.newCachedThreadPool();

    public MiddleLinkService(MiddleLinkRepository middleLinkRepository,
                             GmailPlatformLinkRepository gmailPlatformLinkRepository,
                             AdsPlatformRepository adsPlatformRepository,
                             UserRepository userRepository,
                             ShiftLinkService shiftLinkService) {
        this.middleLinkRepository = middleLinkRepository;
        this.gmailPlatformLinkRepository = gmailPlatformLinkRepository;
        this.adsPlatformRepository = adsPlatformRepository;
        this.userRepository = userRepository;
        this.shiftLinkService = shiftLinkService;
    }

    // 批量上传并按 Match ID 归类 / batch upload and classify by match id
    public List<MiddleLink> batchCreate(String brandName, List<String> urls, Long currentUserId) {
        String brand = trimToNull(brandName);
        if (brand == null) {
            throw new IllegalArgumentException("brandName is required");
        }
        // 落地页归档 Matrix 时作为 adsName，最长 32 / used as adsName when archiving to Matrix
        if (brand.length() > 32) {
            throw new IllegalArgumentException("brandName must be at most 32 characters");
        }
        if (urls == null || urls.isEmpty()) {
            throw new IllegalArgumentException("urls is required");
        }

        User currentUser = getCurrentUser(currentUserId);
        String owner = currentUser.getUserPhoneNumber();
        List<GmailPlatformLink> rules = gmailPlatformLinkRepository.findByAdsOwner(owner);

        List<MiddleLink> created = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String rawUrl : urls) {
            String url = trimToNull(rawUrl);
            if (url == null || !seen.add(url)) {
                continue;
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                throw new IllegalArgumentException("Invalid url: " + url);
            }
            if (url.length() > 1024) {
                throw new IllegalArgumentException("url must be at most 1024 characters: " + url);
            }

            MiddleLink link = new MiddleLink();
            link.setFullUrl(url);
            link.setBrandName(brand);
            link.setAdsOwner(owner);
            link.setResolveStatus(MiddleLink.STATUS_PENDING);
            link.setResolvedCount(0L);
            link.setTargetCount(24L);

            GmailPlatformLink matchedRule = GmailPlatformLinkService.matchRule(rules, url);
            if (matchedRule != null) {
                link.setUserName(matchedRule.getUserName());
                link.setPlatformName(matchedRule.getPlatformName());
            }
            created.add(middleLinkRepository.save(link));
        }
        return created;
    }

    @Transactional(readOnly = true)
    public Page<MiddleLink> search(String brandName, String resolveStatus, String adsOwner, Long currentUserId, Pageable pageable) {
        String normalizedBrand = trimToNull(brandName);
        String normalizedStatus = trimToNull(resolveStatus);
        User currentUser = currentUserId == null ? null : getCurrentUser(currentUserId);
        boolean admin = currentUser != null && isAdmin(currentUser);
        String scopedAdsOwner;
        if (currentUser != null) {
            scopedAdsOwner = admin ? trimToNull(adsOwner) : currentUser.getUserPhoneNumber();
        } else {
            scopedAdsOwner = trimToNull(adsOwner);
        }

        Specification<MiddleLink> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(normalizedBrand)) {
                predicates.add(cb.like(cb.lower(root.get("brandName")), "%" + normalizedBrand.toLowerCase() + "%"));
            }
            if (StringUtils.hasText(normalizedStatus)) {
                predicates.add(cb.equal(cb.upper(root.get("resolveStatus")), normalizedStatus.toUpperCase()));
            }
            if (StringUtils.hasText(scopedAdsOwner)) {
                predicates.add(cb.equal(cb.lower(root.get("adsOwner")), scopedAdsOwner.toLowerCase()));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
        return middleLinkRepository.findAll(specification, pageable);
    }

    public void delete(Long id, Long currentUserId) {
        MiddleLink existing = getOwned(id, currentUserId);
        if (MiddleLink.STATUS_RESOLVING.equals(existing.getResolveStatus())) {
            throw new IllegalArgumentException("Cannot delete a link that is resolving");
        }
        middleLinkRepository.delete(existing);
    }

    // 启动限时解析 / start resolving landing pages within a time window
    public MiddleLink startResolve(Long id, Long targetCount, Long windowSeconds, Long intervalSeconds, Long currentUserId) {
        MiddleLink link = getOwned(id, currentUserId);
        // RESOLVING 可能是上次进程退出留下的孤儿状态，允许重新启动 / RESOLVING may be orphaned after a restart, allow re-start
        if (MiddleLink.STATUS_RESOLVING.equals(link.getResolveStatus())) {
            log.info("MiddleLink resolve re-started id={} while status was RESOLVING", id);
        }

        long target = targetCount != null && targetCount > 0 ? Math.min(targetCount, MAX_TARGET_COUNT) : 24L;
        long window = windowSeconds != null && windowSeconds > 0 ? Math.min(windowSeconds, MAX_WINDOW_SECONDS) : 600L;
        long interval = intervalSeconds != null && intervalSeconds > 0 ? intervalSeconds : 3L;

        link.setTargetCount(target);
        link.setResolveStatus(MiddleLink.STATUS_RESOLVING);
        link.setLastError(null);
        link.setUpdateDate(LocalDateTime.now());
        MiddleLink saved = middleLinkRepository.save(link);

        resolveExecutor.submit(() -> resolveTask(saved.getId(), window, interval));
        return saved;
    }

    // 手动停止解析 / stop resolving manually
    public MiddleLink stopResolve(Long id, Long currentUserId) {
        MiddleLink link = getOwned(id, currentUserId);
        if (!MiddleLink.STATUS_RESOLVING.equals(link.getResolveStatus())) {
            throw new IllegalArgumentException("This link is not resolving");
        }
        link.setResolveStatus(MiddleLink.STATUS_STOPPED);
        link.setUpdateDate(LocalDateTime.now());
        return middleLinkRepository.save(link);
    }

    // 解析任务：时间窗内反复请求中间链接收集落地页 / resolve task: poll the middle link within the window
    private void resolveTask(Long middleLinkId, long windowSeconds, long intervalSeconds) {
        long deadline = System.currentTimeMillis() + windowSeconds * 1000L;
        try {
            MiddleLink link = middleLinkRepository.findById(middleLinkId).orElse(null);
            if (link == null) {
                return;
            }
            Set<String> collected = new LinkedHashSet<>(parseLines(link.getResolvedUrls()));
            int consecutiveFailures = 0;
            String lastFailure = null;

            while (collected.size() < link.getTargetCount() && System.currentTimeMillis() < deadline) {
                // 每轮检查停止标记 / check the stop flag every iteration
                MiddleLink state = middleLinkRepository.findById(middleLinkId).orElse(null);
                if (state == null || !MiddleLink.STATUS_RESOLVING.equals(state.getResolveStatus())) {
                    break;
                }
                String landingUrl = null;
                try {
                    landingUrl = resolveFinalUrl(link.getFullUrl());
                } catch (Exception e) {
                    lastFailure = e.getMessage();
                    log.warn("MiddleLink resolve request failed id={} url={} error={}",
                            middleLinkId, truncate(link.getFullUrl(), 200), e.getMessage());
                }

                if (StringUtils.hasText(landingUrl) && !landingUrl.equals(link.getFullUrl()) && collected.add(landingUrl)) {
                    consecutiveFailures = 0;
                    archiveToMatrix(link, landingUrl);
                    MiddleLink fresh = middleLinkRepository.findById(middleLinkId).orElse(null);
                    if (fresh == null) {
                        return;
                    }
                    fresh.setResolvedUrls(String.join("\n", collected));
                    fresh.setResolvedCount((long) collected.size());
                    fresh.setUpdateDate(LocalDateTime.now());
                    middleLinkRepository.save(fresh);
                } else if (!StringUtils.hasText(landingUrl) || landingUrl.equals(link.getFullUrl())) {
                    consecutiveFailures++;
                    // 连续失败提前终止，避免空转整个时间窗 / abort early on persistent failures
                    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                        log.warn("MiddleLink resolve aborted id={} consecutiveFailures={}", middleLinkId, consecutiveFailures);
                        break;
                    }
                }

                try {
                    Thread.sleep(intervalSeconds * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            MiddleLink fresh = middleLinkRepository.findById(middleLinkId).orElse(null);
            if (fresh == null) {
                return;
            }
            fresh.setResolvedCount((long) collected.size());
            fresh.setResolvedUrls(String.join("\n", collected));
            // 手动停止的保持 STOPPED，不覆盖状态 / keep STOPPED when stopped manually
            if (MiddleLink.STATUS_STOPPED.equals(fresh.getResolveStatus())) {
                fresh.setLastError("Stopped manually, resolved " + collected.size() + " of " + fresh.getTargetCount());
            } else if (collected.isEmpty()) {
                fresh.setResolveStatus(MiddleLink.STATUS_FAILED);
                fresh.setLastError(lastFailure != null
                        ? truncate("Resolve failed: " + lastFailure, 1000)
                        : "No landing page resolved within the time window");
            } else {
                fresh.setResolveStatus(MiddleLink.STATUS_DONE);
                if (collected.size() < fresh.getTargetCount()) {
                    fresh.setLastError("Resolved " + collected.size() + " of " + fresh.getTargetCount() + " within the time window");
                }
            }
            fresh.setUpdateDate(LocalDateTime.now());
            middleLinkRepository.save(fresh);
        } catch (Exception e) {
            log.error("MiddleLink resolve task failed id={} error={}", middleLinkId, e.getMessage(), e);
            middleLinkRepository.findById(middleLinkId).ifPresent(link -> {
                link.setResolveStatus(MiddleLink.STATUS_FAILED);
                link.setLastError(e.getMessage() != null && e.getMessage().length() > 1000
                        ? e.getMessage().substring(0, 1000) : e.getMessage());
                link.setUpdateDate(LocalDateTime.now());
                middleLinkRepository.save(link);
            });
        }
    }

    // 落地页归档为 Matrix 类型 Shift Link / archive a landing page as a Matrix shift link
    private void archiveToMatrix(MiddleLink link, String landingUrl) {
        if (landingUrl.length() > 1024) {
            log.warn("MiddleLink landing url too long, skipped id={} length={}", link.getId(), landingUrl.length());
            return;
        }

        String platformName = trimToNull(link.getPlatformName());
        if (platformName != null) {
            // 平台不存在则自动建档 / create the platform when missing
            String truncatedPlatform = platformName.length() > 32 ? platformName.substring(0, 32) : platformName;
            AdsPlatform platform = adsPlatformRepository.findByPlatformNameIgnoreCase(truncatedPlatform).orElse(null);
            if (platform == null) {
                AdsPlatform created = new AdsPlatform();
                created.setPlatformName(truncatedPlatform);
                adsPlatformRepository.save(created);
                platformName = truncatedPlatform;
            }
        }

        ShiftLink shiftLink = new ShiftLink();
        shiftLink.setAdsType("Matrix");
        shiftLink.setAdsName(link.getBrandName());
        shiftLink.setPlatformName(platformName != null ? platformName : "Unknown");
        shiftLink.setFullUrl(landingUrl);
        // landingPageUrl 只保留 origin+path: 若与 fullUrl 完全相同, replace 后模板退化成 {lpurl}, 跟踪参数全部丢失
        // keep only origin+path so the tracking template keeps the query string after {lpurl}
        String landingBaseUrl = stripQueryAndFragment(landingUrl);
        if (landingBaseUrl.equals(landingUrl)) {
            log.warn("MiddleLink landing url has no query, tracking template keeps no params id={}", link.getId());
        }
        shiftLink.setLandingPageUrl(landingBaseUrl);
        shiftLink.setDisplayNumber(5L);
        shiftLink.setUserName(trimToNull(link.getUserName()));
        shiftLink.setAdsOwner(link.getAdsOwner());
        shiftLink.setRemarks(("from middle link #" + link.getId()).length() > 64
                ? "from middle link" : "from middle link #" + link.getId());

        try {
            shiftLinkService.createShiftLink(shiftLink, null);
        } catch (IllegalArgumentException e) {
            // 已存在等校验失败仅跳过 / skip validation failures like duplicates
            log.warn("MiddleLink archive to matrix skipped id={} error={}", link.getId(), e.getMessage());
        }
    }

    // 去掉 query 与 fragment, 得到落地页基础地址 / strip query and fragment to get the bare landing page
    private static String stripQueryAndFragment(String url) {
        int end = url.length();
        int queryIndex = url.indexOf('?');
        int fragmentIndex = url.indexOf('#');
        if (queryIndex >= 0) {
            end = Math.min(end, queryIndex);
        }
        if (fragmentIndex >= 0) {
            end = Math.min(end, fragmentIndex);
        }
        return url.substring(0, end);
    }

    // 跟随 302 链取最终落地页 URL / follow the redirect chain to the final landing page url
    private String resolveFinalUrl(String startUrl) throws java.io.IOException {
        OkHttpClient client = new OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();

        String current = sanitizeUriValue(startUrl);
        for (int hop = 0; hop < MAX_REDIRECT_HOPS; hop++) {
            Request request = new Request.Builder()
                    .url(current)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("User-Agent", RESOLVE_USER_AGENT)
                    .get()
                    .build();
            try (Response response = client.newCall(request).execute()) {
                int statusCode = response.code();
                if (statusCode >= 300 && statusCode < 400) {
                    String location = response.header("Location");
                    if (!StringUtils.hasText(location)) {
                        return null;
                    }
                    // 跳转目标常含 |、空格等非法字符，先清洗再解析 / redirect targets often contain | or spaces, sanitize first
                    current = toUri(current).resolve(toUri(location.trim())).toString();
                    continue;
                }
                // 跟随过至少一跳后到达商家站：200 正常，403 多为反爬拦截，URL 本身即落地页
                // after at least one hop we are at the merchant site; 403 is bot protection, the url is the landing page
                if (hop > 0) {
                    return current;
                }
                // 第一跳直接 200：中间链接本身即落地页 / first hop 2xx: the link itself is the landing page
                return statusCode >= 200 && statusCode < 300 ? current : null;
            }
        }
        return null;
    }

    // 与 AdsHttpClientTool 相同的 URI 清洗：转义 |、空格、非法 % 等 / same URI sanitize as AdsHttpClientTool
    private static URI toUri(String value) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException e) {
            return URI.create(sanitizeUriValue(value));
        }
    }

    private static String sanitizeUriValue(String value) {
        int prefixEnd = findAuthorityPrefixEnd(value);
        StringBuilder sanitized = new StringBuilder(value.length() + 16);
        sanitized.append(value, 0, prefixEnd);
        for (int i = prefixEnd; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '%' && !hasValidPercentEncoding(value, i)) {
                sanitized.append("%25");
            } else if (ch == ' ') {
                sanitized.append("%20");
            } else if (ch == '|') {
                sanitized.append("%7C");
            } else if (ch == '[') {
                sanitized.append("%5B");
            } else if (ch == ']') {
                sanitized.append("%5D");
            } else if (ch == '"') {
                sanitized.append("%22");
            } else if (ch == '{') {
                sanitized.append("%7B");
            } else if (ch == '}') {
                sanitized.append("%7D");
            } else {
                sanitized.append(ch);
            }
        }
        return sanitized.toString();
    }

    private static int findAuthorityPrefixEnd(String value) {
        int schemeIndex = value.indexOf("://");
        if (schemeIndex < 0) {
            return 0;
        }
        int authorityStart = schemeIndex + 3;
        int authorityEnd = value.indexOf('/', authorityStart);
        return authorityEnd >= 0 ? authorityEnd : value.length();
    }

    private static boolean hasValidPercentEncoding(String value, int index) {
        if (index + 2 >= value.length()) {
            return false;
        }
        return isHexDigit(value.charAt(index + 1)) && isHexDigit(value.charAt(index + 2));
    }

    private static boolean isHexDigit(char ch) {
        return (ch >= '0' && ch <= '9')
                || (ch >= 'a' && ch <= 'f')
                || (ch >= 'A' && ch <= 'F');
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private List<String> parseLines(String text) {
        List<String> lines = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return lines;
        }
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }
        return lines;
    }

    private MiddleLink getOwned(Long id, Long currentUserId) {
        MiddleLink existing = middleLinkRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("MIDDLE_LINK not found: " + id));
        User currentUser = getCurrentUser(currentUserId);
        if (!isAdmin(currentUser) && !currentUser.getUserPhoneNumber().equals(existing.getAdsOwner())) {
            throw new IllegalArgumentException("Unauthorized: you can only operate your own middle links");
        }
        return existing;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private User getCurrentUser(Long currentUserId) {
        return userRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found: " + currentUserId));
    }

    private boolean isAdmin(User user) {
        return user.getUserRole() != null && user.getUserRole().toLowerCase().contains("admin");
    }
}
