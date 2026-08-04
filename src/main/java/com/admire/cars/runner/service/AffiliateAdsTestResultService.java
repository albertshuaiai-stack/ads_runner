package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsPlatform;
import com.admire.cars.runner.entity.AffiliateAdsTestResult;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.AdsPlatformRepository;
import com.admire.cars.runner.repository.AffiliateAdsTestResultRepository;
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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class AffiliateAdsTestResultService {

    private final AffiliateAdsTestResultRepository affiliateAdsTestResultRepository;
    private final AdsPlatformRepository adsPlatformRepository;
    private final UserRepository userRepository;

    public AffiliateAdsTestResultService(
            AffiliateAdsTestResultRepository affiliateAdsTestResultRepository,
            AdsPlatformRepository adsPlatformRepository,
            UserRepository userRepository) {
        this.affiliateAdsTestResultRepository = affiliateAdsTestResultRepository;
        this.adsPlatformRepository = adsPlatformRepository;
        this.userRepository = userRepository;
    }

    public AffiliateAdsTestResult create(AffiliateAdsTestResult result, Long currentUserId) {
        if (result == null) {
            throw new IllegalArgumentException("AFFILIATE_ADS_TEST_RESULT is required");
        }
        User currentUser = getCurrentUser(currentUserId);
        if (!isAdmin(currentUser) || !StringUtils.hasText(result.getAdsOwner())) {
            result.setAdsOwner(currentUser.getUserPhoneNumber());
        }
        validateAndNormalize(result, currentUser);
        result.setCreateDate(LocalDateTime.now());
        return affiliateAdsTestResultRepository.save(result);
    }

    @Transactional(readOnly = true)
    public AffiliateAdsTestResult getById(Long id, Long currentUserId) {
        AffiliateAdsTestResult result = affiliateAdsTestResultRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AFFILIATE_ADS_TEST_RESULT not found: " + id));
        ensureReadable(result, currentUserId);
        return result;
    }

    @Transactional(readOnly = true)
    public Page<AffiliateAdsTestResult> search(
            String adsOwner,
            String affiliateNetwork,
            String region,
            String status,
            Long currentUserId,
            Pageable pageable) {
        User currentUser = getCurrentUser(currentUserId);
        boolean admin = isAdmin(currentUser);
        String normalizedOwnerFilter = admin ? trimToNull(adsOwner) : currentUser.getUserPhoneNumber();

        Specification<AffiliateAdsTestResult> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(normalizedOwnerFilter)) {
                predicates.add(criteriaBuilder.equal(root.get("adsOwner"), normalizedOwnerFilter));
            }
            if (StringUtils.hasText(affiliateNetwork)) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("affiliateNetwork")),
                        affiliateNetwork.trim().toLowerCase(Locale.ROOT)));
            }
            if (StringUtils.hasText(region)) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("region")),
                        region.trim().toLowerCase(Locale.ROOT)));
            }
            if (StringUtils.hasText(status)) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("status")),
                        status.trim().toLowerCase(Locale.ROOT)));
            }

            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        return affiliateAdsTestResultRepository.findAll(specification, pageable);
    }

    public AffiliateAdsTestResult update(Long id, AffiliateAdsTestResult updateData, Long currentUserId) {
        if (updateData == null) {
            throw new IllegalArgumentException("updateData is required");
        }
        User currentUser = getCurrentUser(currentUserId);

        AffiliateAdsTestResult existing = affiliateAdsTestResultRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AFFILIATE_ADS_TEST_RESULT not found: " + id));
        ensureWritable(existing, currentUserId);

        if (updateData.getAffiliateNetwork() != null) {
            existing.setAffiliateNetwork(updateData.getAffiliateNetwork());
        }
        if (updateData.getRegion() != null) {
            existing.setRegion(updateData.getRegion());
        }
        if (updateData.getSiteName() != null) {
            existing.setSiteName(updateData.getSiteName());
        }
        if (updateData.getSiteUrl() != null) {
            existing.setSiteUrl(updateData.getSiteUrl());
        }
        if (updateData.getTrackingUrl() != null) {
            existing.setTrackingUrl(updateData.getTrackingUrl());
        }
        if (updateData.getFinalUrl() != null) {
            existing.setFinalUrl(updateData.getFinalUrl());
        }
        if (updateData.getStatus() != null) {
            existing.setStatus(updateData.getStatus());
        }
        if (isAdmin(currentUser) && updateData.getAdsOwner() != null) {
            existing.setAdsOwner(updateData.getAdsOwner());
        }

        validateAndNormalize(existing, currentUser);
        existing.setUpdateDate(LocalDateTime.now());
        return affiliateAdsTestResultRepository.save(existing);
    }

    public void delete(Long id, Long currentUserId) {
        AffiliateAdsTestResult existing = affiliateAdsTestResultRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AFFILIATE_ADS_TEST_RESULT not found: " + id));
        ensureWritable(existing, currentUserId);
        affiliateAdsTestResultRepository.delete(existing);
    }

    private void validateAndNormalize(AffiliateAdsTestResult result, User currentUser) {
        result.setAffiliateNetwork(trimToNull(result.getAffiliateNetwork()));
        result.setRegion(trimToNull(result.getRegion()));
        result.setSiteName(trimToNull(result.getSiteName()));
        result.setSiteUrl(trimToNull(result.getSiteUrl()));
        result.setTrackingUrl(trimToNull(result.getTrackingUrl()));
        result.setFinalUrl(trimToNull(result.getFinalUrl()));
        result.setStatus(normalizeEnumLike(result.getStatus(), null));

        if (!StringUtils.hasText(result.getAdsOwner())) {
            throw new IllegalArgumentException("adsOwner is required");
        }
        User owner = userRepository.findByUserPhoneNumber(result.getAdsOwner().trim())
                .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found by phone number: " + result.getAdsOwner()));
        result.setAdsOwner(owner.getUserPhoneNumber());

        if (!isAdmin(currentUser) && !currentUser.getUserPhoneNumber().equals(result.getAdsOwner())) {
            throw new IllegalArgumentException("Unauthorized: adsOwner must match current user");
        }

        if (!StringUtils.hasText(result.getAffiliateNetwork())) {
            throw new IllegalArgumentException("affiliateNetwork is required");
        }
        AdsPlatform platform = adsPlatformRepository.findByPlatformNameIgnoreCase(result.getAffiliateNetwork())
                .orElseThrow(() -> new IllegalArgumentException("ADS_PLATFORM not found: " + result.getAffiliateNetwork()));
        result.setAffiliateNetwork(platform.getPlatformName());

        if (!StringUtils.hasText(result.getRegion())) {
            throw new IllegalArgumentException("region is required");
        }
        if (!StringUtils.hasText(result.getSiteName())) {
            throw new IllegalArgumentException("siteName is required");
        }
        if (!StringUtils.hasText(result.getStatus())) {
            throw new IllegalArgumentException("status is required");
        }

        validateAllowed(result.getStatus(), "status", "SUCCESS", "FAILED");
        validateLength(result.getAffiliateNetwork(), "affiliateNetwork", 64);
        validateLength(result.getRegion(), "region", 64);
        validateLength(result.getSiteName(), "siteName", 128);
        validateLength(result.getSiteUrl(), "siteUrl", 1024);
        validateLength(result.getTrackingUrl(), "trackingUrl", 1024);
        validateLength(result.getFinalUrl(), "finalUrl", 2044);
        validateLength(result.getStatus(), "status", 64);
    }

    private String normalizeEnumLike(String value, String defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private void validateAllowed(String value, String fieldName, String... allowedValues) {
        if (value == null) {
            return;
        }
        boolean allowed = Arrays.stream(allowedValues).anyMatch(value::equals);
        if (!allowed) {
            throw new IllegalArgumentException(fieldName + " must be one of: " + String.join(", ", allowedValues));
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

    private void ensureReadable(AffiliateAdsTestResult result, Long currentUserId) {
        ensureAccess(result, currentUserId, "read");
    }

    private void ensureWritable(AffiliateAdsTestResult result, Long currentUserId) {
        ensureAccess(result, currentUserId, "modify");
    }

    private void ensureAccess(AffiliateAdsTestResult result, Long currentUserId, String action) {
        User currentUser = getCurrentUser(currentUserId);
        if (!isAdmin(currentUser) && !currentUser.getUserPhoneNumber().equals(result.getAdsOwner())) {
            throw new IllegalArgumentException("Unauthorized: you can only " + action + " your own test results");
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
