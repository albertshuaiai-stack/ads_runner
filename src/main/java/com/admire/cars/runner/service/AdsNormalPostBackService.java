package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsNormalPostBack;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.AdsNormalPostBackRepository;
import com.admire.cars.runner.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

@Slf4j
@Service
@Transactional
public class AdsNormalPostBackService {

    private final AdsNormalPostBackRepository adsNormalPostBackRepository;

    private final UserService userService;

    private final UserRepository userRepository;

    public AdsNormalPostBackService(AdsNormalPostBackRepository adsNormalPostBackRepository, UserService userService, UserRepository userRepository) {
        this.adsNormalPostBackRepository = adsNormalPostBackRepository;
        this.userService = userService;
        this.userRepository = userRepository;
    }

    public AdsNormalPostBack create(AdsNormalPostBack adsNormalPostBack, String apiKey) {
        if (adsNormalPostBack == null) {
            throw new IllegalArgumentException("ADS_NORMAL_POST_BACK is required");
        }
        User user = userService.getEnabledUserByApiKey(apiKey);
        String adsOwner = user.getUserPhoneNumber();
        adsNormalPostBack.setAdsOwner(adsOwner);
        validateAndNormalize(adsNormalPostBack);

        var existingOpt = adsNormalPostBackRepository
                .findByAdsOwnerAndOrderNo(adsOwner, adsNormalPostBack.getOrderNo());
        if (existingOpt.isEmpty()) {
            return adsNormalPostBackRepository.save(adsNormalPostBack);
        }
        // 状态变更回调也要刷新业务字段: 首条 pending 常缺 sub_id, 不刷新会永久归因失败, 且不能清掉归因结果
        // refresh business fields on status updates; a pending postback without sub_id stays unattributed,
        // and saving the incoming entity would also wipe the attribution columns
        AdsNormalPostBack existing = existingOpt.get();
        existing.setStatus(adsNormalPostBack.getStatus());
        existing.setAffiliateSite(adsNormalPostBack.getAffiliateSite());
        existing.setAdvertiserShopId(adsNormalPostBack.getAdvertiserShopId());
        existing.setAdvertiserShopName(adsNormalPostBack.getAdvertiserShopName());
        existing.setSignId(adsNormalPostBack.getSignId());
        existing.setOrderTime(adsNormalPostBack.getOrderTime());
        existing.setOrderAmount(adsNormalPostBack.getOrderAmount());
        existing.setUserCommissionAmount(adsNormalPostBack.getUserCommissionAmount());
        // 后续回调未带 sub_id 时保留原值, 否则已归因订单会被冲掉 / keep the stored sub ids when a later callback omits them
        if (StringUtils.hasText(adsNormalPostBack.getSubId())) {
            existing.setSubId(adsNormalPostBack.getSubId());
        }
        if (StringUtils.hasText(adsNormalPostBack.getSubId2())) {
            existing.setSubId2(adsNormalPostBack.getSubId2());
        }
        if (StringUtils.hasText(adsNormalPostBack.getClickTime())) {
            existing.setClickTime(adsNormalPostBack.getClickTime());
        }
        log.info("POSTBACK_UPDATED id={} orderNo={} status={} subId={} subId2={}",
                existing.getId(), existing.getOrderNo(), existing.getStatus(),
                existing.getSubId(), existing.getSubId2());
        return adsNormalPostBackRepository.save(existing);
    }

    // 后台 Post Back 页删除单条记录, 非 admin 只能删自己的 / delete one postback row
    public void deleteById(Long id, Long currentUserId) {
        AdsNormalPostBack postBack = adsNormalPostBackRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ADS_NORMAL_POST_BACK not found: " + id));
        if (currentUserId != null) {
            User current = getCurrentUser(currentUserId);
            if (!isAdmin(current) && !current.getUserPhoneNumber().equals(postBack.getAdsOwner())) {
                throw new IllegalArgumentException("Unauthorized: you can only delete your own post backs");
            }
        }
        adsNormalPostBackRepository.delete(postBack);
        log.info("Deleted ADS_NORMAL_POST_BACK id={} orderNo={}", id, postBack.getOrderNo());
    }

    @Transactional(readOnly = true)
    public AdsNormalPostBack getById(Long id, Long currentUserId) {
        AdsNormalPostBack adsNormalPostBack = adsNormalPostBackRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ADS_NORMAL_POST_BACK not found: " + id));
        ensureReadable(adsNormalPostBack, currentUserId);
        return adsNormalPostBack;
    }

    @Transactional(readOnly = true)
    public Page<AdsNormalPostBack> search(
            String adsOwner,
            String affiliateSite,
            String orderNo,
            String status,
            Long currentUserId,
            Pageable pageable) {
        return search(adsOwner, affiliateSite, orderNo, status, null, null, currentUserId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AdsNormalPostBack> search(
            String adsOwner,
            String affiliateSite,
            String orderNo,
            String status,
            String advertiserShopId,
            String advertiserShopName,
            Long currentUserId,
            Pageable pageable) {
        String scopedAdsOwner = resolveScopedOwner(adsOwner, currentUserId);
        return adsNormalPostBackRepository.findAll(
                buildSpecification(scopedAdsOwner, affiliateSite, orderNo, status, advertiserShopId, advertiserShopName),
                pageable);
    }

    // 按 offer(商家) 汇总订单, 与订单列表同口径筛选(owner/平台/状态) / per-offer order summary, same filters as the list
    @Transactional(readOnly = true)
    public List<Map<String, Object>> offerSummary(
            String adsOwner,
            String affiliateSite,
            String status,
            Long currentUserId) {
        String scopedAdsOwner = resolveScopedOwner(adsOwner, currentUserId);
        List<AdsNormalPostBack> orders = adsNormalPostBackRepository.findAll(
                buildSpecification(scopedAdsOwner, affiliateSite, null, status, null, null));

        Map<String, OfferAggregate> byOffer = new LinkedHashMap<>();
        for (AdsNormalPostBack order : orders) {
            String shopId = trimToNull(order.getAdvertiserShopId());
            String shopName = trimToNull(order.getAdvertiserShopName());
            // 优先按商家ID分组, 无ID的按名称 / group by shop id, fall back to name when id is missing
            String key = shopId != null ? "ID:" + shopId
                    : "NAME:" + (shopName == null ? "" : shopName.toLowerCase(Locale.ROOT));
            byOffer.computeIfAbsent(key, ignored -> new OfferAggregate(shopId)).add(order, shopName);
        }

        return byOffer.values().stream()
                .sorted(Comparator.comparingLong(OfferAggregate::orderCount).reversed()
                        .thenComparing(OfferAggregate::commission, Comparator.reverseOrder()))
                .map(OfferAggregate::toMap)
                .toList();
    }

    static boolean isRefundedStatus(String status) {
        if (status == null) {
            return false;
        }
        String value = status.toLowerCase(Locale.ROOT);
        for (String keyword : REFUNDED_STATUS_KEYWORDS) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    // 与 AdsNormalPostBackRepository 聚合查询的退单关键词一致 / same keywords as the repository aggregate queries
    private static final List<String> REFUNDED_STATUS_KEYWORDS =
            List.of("reject", "refund", "cancel", "void", "decline", "revers");

    private static final class OfferAggregate {
        private final String advertiserShopId;
        private String advertiserShopName;
        private final Set<String> affiliateSites = new TreeSet<>();
        private final Map<String, Long> statusCounts = new TreeMap<>();
        private long orderCount;
        private long refundedOrderCount;
        private long matchedOrderCount;
        private BigDecimal orderAmount = BigDecimal.ZERO;
        private BigDecimal commission = BigDecimal.ZERO;
        private String firstOrderTime;
        private String lastOrderTime;

        private OfferAggregate(String advertiserShopId) {
            this.advertiserShopId = advertiserShopId;
        }

        private void add(AdsNormalPostBack order, String shopName) {
            orderCount++;
            if (shopName != null && advertiserShopName == null) {
                advertiserShopName = shopName;
            }
            if (StringUtils.hasText(order.getAffiliateSite())) {
                affiliateSites.add(order.getAffiliateSite().trim());
            }
            String status = order.getStatus() == null ? "" : order.getStatus().trim().toLowerCase(Locale.ROOT);
            statusCounts.merge(status, 1L, Long::sum);
            if (isRefundedStatus(status)) {
                refundedOrderCount++;
            }
            if ("MATCHED".equals(order.getMatchStatus())) {
                matchedOrderCount++;
            }
            if (order.getOrderAmount() != null) {
                orderAmount = orderAmount.add(order.getOrderAmount());
            }
            if (order.getUserCommissionAmount() != null) {
                commission = commission.add(order.getUserCommissionAmount());
            }
            String orderTime = order.getOrderTime();
            if (StringUtils.hasText(orderTime)) {
                if (firstOrderTime == null || orderTime.compareTo(firstOrderTime) < 0) {
                    firstOrderTime = orderTime;
                }
                if (lastOrderTime == null || orderTime.compareTo(lastOrderTime) > 0) {
                    lastOrderTime = orderTime;
                }
            }
        }

        private long orderCount() {
            return orderCount;
        }

        private BigDecimal commission() {
            return commission;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("advertiserShopId", advertiserShopId);
            row.put("advertiserShopName", advertiserShopName);
            row.put("affiliateSites", String.join(", ", affiliateSites));
            row.put("orderCount", orderCount);
            row.put("orderStatusCounts", statusCounts);
            row.put("refundedOrderCount", refundedOrderCount);
            row.put("matchedOrderCount", matchedOrderCount);
            row.put("orderAmount", orderAmount);
            row.put("commission", commission);
            row.put("firstOrderTime", firstOrderTime);
            row.put("lastOrderTime", lastOrderTime);
            return row;
        }
    }

    private String resolveScopedOwner(String adsOwner, Long currentUserId) {
        String normalizedAdsOwner = normalizeOptional(adsOwner);
        User currentUser = currentUserId == null ? null : getCurrentUser(currentUserId);
        boolean admin = currentUser != null && isAdmin(currentUser);
        String scopedAdsOwner;
        if (currentUser != null) {
            if (admin) {
                // admin: use provided adsOwner if any, otherwise no owner filter
                scopedAdsOwner = normalizedAdsOwner;
            } else {
                // non-admin logged user: always scope to token-derived owner
                scopedAdsOwner = currentUser.getUserPhoneNumber();
            }
        } else {
            // unauthenticated caller: require adsOwner to be provided
            if (!StringUtils.hasText(normalizedAdsOwner)) {
                throw new IllegalArgumentException("adsOwner is required");
            }
            scopedAdsOwner = normalizedAdsOwner;
        }
        return scopedAdsOwner;
    }

    private Specification<AdsNormalPostBack> buildSpecification(
            String scopedAdsOwner,
            String affiliateSite,
            String orderNo,
            String status,
            String advertiserShopId,
            String advertiserShopName) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(scopedAdsOwner)) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("adsOwner")),
                        scopedAdsOwner.toLowerCase()));
            }
            if (StringUtils.hasText(affiliateSite)) {
                // support partial, case-insensitive match for affiliate site
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("affiliateSite")),
                        "%" + affiliateSite.trim().toLowerCase() + "%"));
            }

            if (StringUtils.hasText(orderNo)) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("orderNo")),
                        "%" + orderNo.trim().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(status)) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("status")),
                        status.trim().toLowerCase()));
            }
            // offer 筛选: 有商家ID按ID精确匹配, 否则按名称(不区分大小写) / offer filter: exact shop id, else shop name
            if (StringUtils.hasText(advertiserShopId)) {
                predicates.add(criteriaBuilder.equal(root.get("advertiserShopId"), advertiserShopId.trim()));
            } else if (StringUtils.hasText(advertiserShopName)) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("advertiserShopName")),
                        advertiserShopName.trim().toLowerCase()));
            }

            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void validateAndNormalize(AdsNormalPostBack adsNormalPostBack) {
        if (!StringUtils.hasText(adsNormalPostBack.getAdsOwner())) {
            throw new IllegalArgumentException("adsOwner is required");
        }
        User owner = userRepository.findByUserPhoneNumber(adsNormalPostBack.getAdsOwner().trim())
                .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found by phone number: " + adsNormalPostBack.getAdsOwner()));

        adsNormalPostBack.setAdsOwner(owner.getUserPhoneNumber());
        adsNormalPostBack.setAffiliateSite(trimToNull(adsNormalPostBack.getAffiliateSite()));
        adsNormalPostBack.setAdvertiserShopId(trimToNull(adsNormalPostBack.getAdvertiserShopId()));
        adsNormalPostBack.setAdvertiserShopName(trimToNull(adsNormalPostBack.getAdvertiserShopName()));
        adsNormalPostBack.setSignId(trimToNull(adsNormalPostBack.getSignId()));
        adsNormalPostBack.setOrderNo(trimToNull(adsNormalPostBack.getOrderNo()));
        adsNormalPostBack.setStatus(trimToNull(adsNormalPostBack.getStatus()));
        adsNormalPostBack.setSubId(trimToNull(adsNormalPostBack.getSubId()));
        adsNormalPostBack.setSubId2(trimToNull(adsNormalPostBack.getSubId2()));

        validateLength(adsNormalPostBack.getAdsOwner(), "adsOwner", 64);
        validateLength(adsNormalPostBack.getAffiliateSite(), "affiliateSite", 64);
        validateLength(adsNormalPostBack.getAdvertiserShopId(), "advertiserShopId", 64);
        validateLength(adsNormalPostBack.getAdvertiserShopName(), "advertiserShopName", 512);
        validateLength(adsNormalPostBack.getSignId(), "signId", 64);
        validateLength(adsNormalPostBack.getOrderNo(), "orderNo", 128);
        validateLength(adsNormalPostBack.getStatus(), "status", 64);
        validateLength(adsNormalPostBack.getSubId(), "subId", 128);
        validateLength(adsNormalPostBack.getSubId2(), "subId2", 128);

        adsNormalPostBack.setOrderTime(trimToNull(adsNormalPostBack.getOrderTime()));
        adsNormalPostBack.setClickTime(trimToNull(adsNormalPostBack.getClickTime()));

        validateLength(adsNormalPostBack.getOrderTime(), "orderTime", 32);
        validateLength(adsNormalPostBack.getClickTime(), "clickTime", 32);
        validateAmount(adsNormalPostBack.getOrderAmount(), "orderAmount");
        validateAmount(adsNormalPostBack.getUserCommissionAmount(), "userCommissionAmount");
    }

    private void validateAmount(BigDecimal value, String fieldName) {
        if (value == null) {
            return;
        }
        if (value.scale() > 2) {
            throw new IllegalArgumentException(fieldName + " must have at most 2 decimal places");
        }
    }

    private void validateLength(String value, String fieldName, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must be at most " + maxLength + " characters");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeOptional(String value) {
        return trimToNull(value);
    }

    private void ensureReadable(AdsNormalPostBack adsNormalPostBack, Long currentUserId) {
        if (currentUserId == null) {
            return;
        }
        User currentUser = getCurrentUser(currentUserId);
        if (!isAdmin(currentUser) && !currentUser.getUserPhoneNumber().equals(adsNormalPostBack.getAdsOwner())) {
            throw new IllegalArgumentException("Unauthorized: you can only read your own ads normal post backs");
        }
    }

    private User getCurrentUser(Long currentUserId) {
        return userRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found: " + currentUserId));
    }

    private boolean isAdmin(User user) {
        return user.getUserRole() != null
                && Arrays.stream(user.getUserRole().split(","))
                .map(String::trim)
                .anyMatch(role -> "admin".equalsIgnoreCase(role));
    }
}
