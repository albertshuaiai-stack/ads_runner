package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsNormalPostBack;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.AdsNormalPostBackRepository;
import com.admire.cars.runner.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
        adsNormalPostBack.setAffiliateSite("BonuesArrive");
        validateAndNormalize(adsNormalPostBack);
        return adsNormalPostBackRepository.save(adsNormalPostBack);
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
        String normalizedAdsOwner = normalizeOptional(adsOwner);
        User currentUser = currentUserId == null ? null : getCurrentUser(currentUserId);
        boolean admin = currentUser != null && isAdmin(currentUser);
        String scopedAdsOwner = currentUser == null
                ? normalizedAdsOwner
                : (admin ? normalizedAdsOwner : currentUser.getUserPhoneNumber());

        if (currentUser == null && !StringUtils.hasText(scopedAdsOwner)) {
            throw new IllegalArgumentException("adsOwner is required");
        }

        Specification<AdsNormalPostBack> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(scopedAdsOwner)) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("adsOwner")),
                        scopedAdsOwner.toLowerCase()));
            }
            if (StringUtils.hasText(affiliateSite)) {
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

            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        return adsNormalPostBackRepository.findAll(specification, pageable);
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
