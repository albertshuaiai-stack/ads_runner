package com.admire.cars.runner.service;

import com.admire.cars.runner.constant.Constant;
import com.admire.cars.runner.entity.AdsNormalInfo;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.event.AdsAutoTaskAction;
import com.admire.cars.runner.event.AdsAutoTaskRegistrationEvent;
import com.admire.cars.runner.repository.AdsNormalInfoRepository;
import com.admire.cars.runner.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional
public class AdsNormalInfoService {

    // 大小写不敏感匹配 subid= / sub_id= 两种写法, 统一成 subid={subid}; 不误伤 sub2id= / match both spellings, keep sub2id= untouched
    private static final Pattern SUBID_PARAM_PATTERN =
            Pattern.compile("[?&](?:subid|sub_id)=([^&]*)", Pattern.CASE_INSENSITIVE);

    // sub2id 家族: sub2id / sub_id2 / subid2 / the sub2id family of param spellings
    private static final Pattern SUB2ID_PARAM_PATTERN =
            Pattern.compile("[?&](?:sub2id|sub_id2|subid2)=([^&]*)", Pattern.CASE_INSENSITIVE);

    private final AdsNormalInfoRepository adsNormalInfoRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AdsNormalInfoService(
            AdsNormalInfoRepository adsNormalInfoRepository,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher) {
        this.adsNormalInfoRepository = adsNormalInfoRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    public AdsNormalInfo create(AdsNormalInfo adsNormalInfo, Long currentUserId) {
        adsNormalInfo.setAdsOwner(resolveAdsOwner(currentUserId));
        validateAndNormalize(adsNormalInfo);
        adsNormalInfo.setCreateDate(LocalDateTime.now());
        AdsNormalInfo saved = adsNormalInfoRepository.save(adsNormalInfo);
        publishAutoTaskRegistration(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public AdsNormalInfo getById(Long id) {
        return adsNormalInfoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ADS_NORMAL_INFO not found: " + id));
    }

    public Page<AdsNormalInfo> search(String campainName, String platformName, String status, String adsOwner, Long currentUserId, Pageable pageable) {
        User currentUser = getCurrentUser(currentUserId);
        boolean admin = isAdmin(currentUser);
        String scopedAdsOwner = admin ? adsOwner : currentUser.getUserPhoneNumber();
        Specification<AdsNormalInfo> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(campainName)) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("campainName")),
                        "%" + campainName.toLowerCase() + "%"));
            }
            if (StringUtils.hasText(platformName)) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("platformName")),
                        platformName.toLowerCase()));
            }
            if (StringUtils.hasText(status)) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("status")),
                        status.toLowerCase()));
            }
            if (StringUtils.hasText(scopedAdsOwner)) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("adsOwner")),
                        scopedAdsOwner.toLowerCase()));
            }

            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        return adsNormalInfoRepository.findAll(specification, pageable);
    }

    public AdsNormalInfo update(Long id, AdsNormalInfo updateData, Long currentUserId) {
        AdsNormalInfo existing = getById(id);

        if (updateData.getCampainName() != null) {
            existing.setCampainName(updateData.getCampainName());
        }
        if (updateData.getCampainCountry() != null) {
            existing.setCampainCountry(updateData.getCampainCountry());
        }
        if (updateData.getPlatformName() != null) {
            existing.setPlatformName(updateData.getPlatformName());
        }
        if (updateData.getAffiliteUrl() != null) {
            existing.setAffiliteUrl(updateData.getAffiliteUrl());
        }
        if (updateData.getLandingPageUrl() != null) {
            existing.setLandingPageUrl(updateData.getLandingPageUrl());
        }
        if (updateData.getDynamicProxyInfo() != null) {
            existing.setDynamicProxyInfo(updateData.getDynamicProxyInfo());
        }
        if (updateData.getDynamicProxyInfoBackup() != null) {
            existing.setDynamicProxyInfoBackup(updateData.getDynamicProxyInfoBackup());
        }
        if (updateData.getIntervalTime() != null) {
            existing.setIntervalTime(updateData.getIntervalTime());
        }
        if (updateData.getStatus() != null) {
            existing.setStatus(updateData.getStatus());
        }
        existing.setAdsOwner(resolveAdsOwner(currentUserId));

        validateAndNormalize(existing);
        existing.setUpdateDate(LocalDateTime.now());
        AdsNormalInfo saved = adsNormalInfoRepository.save(existing);
        publishAutoTaskRegistration(saved);
        return saved;
    }

    public void delete(Long id) {
        AdsNormalInfo existing = getById(id);
        publishAutoTaskDelete(existing);
        adsNormalInfoRepository.delete(existing);
    }

    private void validateAndNormalize(AdsNormalInfo adsNormalInfo) {
        if (adsNormalInfo == null) {
            throw new IllegalArgumentException("ADS_NORMAL_INFO is required");
        }
        if (!StringUtils.hasText(adsNormalInfo.getCampainName())) {
            throw new IllegalArgumentException("campainName is required");
        }
        if (!StringUtils.hasText(adsNormalInfo.getCampainCountry())) {
            throw new IllegalArgumentException("campainCountry is required");
        }
        if (!StringUtils.hasText(adsNormalInfo.getPlatformName())) {
            throw new IllegalArgumentException("platformName is required");
        }
        if (!StringUtils.hasText(adsNormalInfo.getAdsOwner())) {
            throw new IllegalArgumentException("adsOwner is required");
        }

        User owner = userRepository.findByUserPhoneNumber(adsNormalInfo.getAdsOwner().trim())
                .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found by phone number: " + adsNormalInfo.getAdsOwner()));

        adsNormalInfo.setPlatformName(adsNormalInfo.getPlatformName().trim());
        adsNormalInfo.setAdsOwner(owner.getUserPhoneNumber());
        adsNormalInfo.setCampainName(adsNormalInfo.getCampainName().trim());
        adsNormalInfo.setCampainCountry(adsNormalInfo.getCampainCountry().trim());
        adsNormalInfo.setStatus(normalizeStatus(adsNormalInfo.getStatus()));

        if (adsNormalInfo.getCampainName().length() > 32) {
            throw new IllegalArgumentException("campainName must be at most 32 characters");
        }
        if (adsNormalInfo.getCampainCountry().length() > 8) {
            throw new IllegalArgumentException("campainCountry must be at most 8 characters");
        }
        if (adsNormalInfo.getPlatformName().length() > 32) {
            throw new IllegalArgumentException("platformName must be at most 32 characters");
        }
        if (adsNormalInfo.getAffiliteUrl() != null && adsNormalInfo.getAffiliteUrl().length() > 128) {
            throw new IllegalArgumentException("affiliteUrl must be at most 128 characters");
        }
        if (adsNormalInfo.getLandingPageUrl() != null && adsNormalInfo.getLandingPageUrl().length() > 128) {
            throw new IllegalArgumentException("landingPageUrl must be at most 128 characters");
        }
        if (adsNormalInfo.getDynamicProxyInfo() != null && adsNormalInfo.getDynamicProxyInfo().length() > 256) {
            throw new IllegalArgumentException("dynamicProxyInfo must be at most 256 characters");
        }
        if (adsNormalInfo.getDynamicProxyInfoBackup() != null && adsNormalInfo.getDynamicProxyInfoBackup().length() > 256) {
            throw new IllegalArgumentException("dynamicProxyInfoBackup must be at most 256 characters");
        }
        if (adsNormalInfo.getStatus().length() > 16) {
            throw new IllegalArgumentException("status must be at most 16 characters");
        }
        adsNormalInfo.setAffiliteUrl(ensureSubIdPlaceholder(adsNormalInfo.getAffiliteUrl()));
    }

    // 联盟链接必须带 subid={subid}: 已有 subid= 的把值换成占位符并去重, 完全没有的追加; 避免 subid= 与 subid={subid} 并存
    // The affiliate URL must carry subid={subid}: rewrite an existing subid= value into the placeholder, drop duplicates, append when missing
    static String ensureSubIdPlaceholder(String url) {
        if (!StringUtils.hasText(url)) {
            return url;
        }
        Matcher matcher = SUBID_PARAM_PATTERN.matcher(url);
        List<int[]> spans = new ArrayList<>();
        String firstValue = null;
        while (matcher.find()) {
            spans.add(new int[]{matcher.start(), matcher.end(), matcher.start(1), matcher.end(1)});
            if (firstValue == null) {
                firstValue = url.substring(matcher.start(1), matcher.end(1));
            }
        }
        String normalized;
        if (spans.isEmpty()) {
            String appended = url + (url.contains("?") ? "&" : "?") + "subid={subid}";
            normalized = appended.length() <= 128 ? appended : url;
        } else {
            StringBuilder rebuilt = new StringBuilder(url);
            // 从后往前删多余的 subid= 参数, 再把第一处统一成小写 subid={subid} / drop duplicates, then normalize the first to lowercase subid={subid}
            for (int i = spans.size() - 1; i >= 1; i--) {
                rebuilt.delete(spans.get(i)[0], spans.get(i)[1]);
            }
            String normalizedToken = url.charAt(spans.get(0)[0]) + "subid={subid}";
            rebuilt.replace(spans.get(0)[0], spans.get(0)[1], normalizedToken);
            String result = rebuilt.toString();
            normalized = result.length() <= 128 ? result : url;
        }
        return ensureSub2IdPlaceholder(normalized);
    }

    // sub2id 冗余位: 空/占位符的统一成 sub2id={subid}(运行时填同一个 clickId), 没有 sub2id 的补上;
    // 有业务值(如 {adid} 任务标记)的不动
    // Mirror slot: normalize empty/placeholder sub2id into sub2id={subid} and append when absent; real values stay
    static String ensureSub2IdPlaceholder(String url) {
        if (!StringUtils.hasText(url)) {
            return url;
        }
        Matcher matcher = SUB2ID_PARAM_PATTERN.matcher(url);
        List<int[]> spans = new ArrayList<>();
        String firstValue = null;
        while (matcher.find()) {
            spans.add(new int[]{matcher.start(), matcher.end(), matcher.start(1), matcher.end(1)});
            if (firstValue == null) {
                firstValue = url.substring(matcher.start(1), matcher.end(1));
            }
        }
        if (spans.isEmpty()) {
            String appended = url + (url.contains("?") ? "&" : "?") + "sub2id={subid}";
            return appended.length() <= 128 ? appended : url;
        }
        StringBuilder rebuilt = new StringBuilder(url);
        // 从后往前删多余的 sub2id= 参数 / drop duplicates from the tail
        for (int i = spans.size() - 1; i >= 1; i--) {
            rebuilt.delete(spans.get(i)[0], spans.get(i)[1]);
        }
        String value = firstValue == null ? "" : firstValue.trim();
        boolean blank = value.isEmpty()
                || "{subid}".equalsIgnoreCase(value)
                || "{subid2}".equalsIgnoreCase(value);
        if (blank) {
            String normalizedToken = url.charAt(spans.get(0)[0]) + "sub2id={subid}";
            rebuilt.replace(spans.get(0)[0], spans.get(0)[1], normalizedToken);
        }
        String normalized = rebuilt.toString();
        return normalized.length() <= 128 ? normalized : url;
    }

    private String normalizeStatus(String status) {
        String normalized = StringUtils.hasText(status) ? status.trim().toUpperCase() : "RUNNING";
        if (!"PAUSED".equals(normalized) && !"RUNNING".equals(normalized)) {
            throw new IllegalArgumentException("status must be PAUSED or RUNNING");
        }
        return normalized;
    }

    private void publishAutoTaskRegistration(AdsNormalInfo saved) {
        Long intervalTime = saved.getIntervalTime();
        if (intervalTime == null) {
            return;
        }
        if (intervalTime <= 0) {
            throw new IllegalArgumentException("intervalTime must be greater than 0");
        }

        eventPublisher.publishEvent(new AdsAutoTaskRegistrationEvent(
                AdsAutoTaskAction.UPSERT,
                saved.getId(),
                saved.getAdsOwner(),
                Constant.ADS_TYPE_NORMAL,
                intervalTime,
                saved.getStatus(),
                saved.getCampainCountry(),
                saved.getPlatformName(),
                saved.getCampainName()));
    }

    private void publishAutoTaskDelete(AdsNormalInfo existing) {
        eventPublisher.publishEvent(new AdsAutoTaskRegistrationEvent(
                AdsAutoTaskAction.DELETE,
                existing.getId(),
                existing.getAdsOwner(),
                Constant.ADS_TYPE_NORMAL,
                existing.getIntervalTime(),
                existing.getStatus(),
                existing.getCampainCountry(),
                existing.getPlatformName(),
                existing.getCampainName()));
    }

    private String resolveAdsOwner(Long currentUserId) {
        return getCurrentUser(currentUserId).getUserPhoneNumber();
    }

    private User getCurrentUser(Long currentUserId) {
        return userRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found by id: " + currentUserId));
    }

    private boolean isAdmin(User user) {
        return user.getUserRole() != null
                && java.util.Arrays.stream(user.getUserRole().split(","))
                .map(String::trim)
                .anyMatch(role -> "admin".equalsIgnoreCase(role));
    }
}
