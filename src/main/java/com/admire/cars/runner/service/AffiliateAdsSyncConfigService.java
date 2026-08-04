package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsPlatform;
import com.admire.cars.runner.entity.AffiliateAdsSyncConfig;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.AdsPlatformRepository;
import com.admire.cars.runner.repository.AffiliateAdsSyncConfigRepository;
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
public class AffiliateAdsSyncConfigService {

    private final AffiliateAdsSyncConfigRepository affiliateAdsSyncConfigRepository;
    private final AdsPlatformRepository adsPlatformRepository;
    private final UserRepository userRepository;

    public AffiliateAdsSyncConfigService(
            AffiliateAdsSyncConfigRepository affiliateAdsSyncConfigRepository,
            AdsPlatformRepository adsPlatformRepository,
            UserRepository userRepository) {
        this.affiliateAdsSyncConfigRepository = affiliateAdsSyncConfigRepository;
        this.adsPlatformRepository = adsPlatformRepository;
        this.userRepository = userRepository;
    }

    public AffiliateAdsSyncConfig create(AffiliateAdsSyncConfig config, Long currentUserId) {
        if (config == null) {
            throw new IllegalArgumentException("AFFILIATE_ADS_SYNC_CONFIG is required");
        }
        User currentUser = getCurrentUser(currentUserId);
        if (!isAdmin(currentUser) || !StringUtils.hasText(config.getAdsOwner())) {
            config.setAdsOwner(currentUser.getUserPhoneNumber());
        }
        validateAndNormalize(config);
        config.setCreateDate(LocalDateTime.now());
        return affiliateAdsSyncConfigRepository.save(config);
    }

    @Transactional(readOnly = true)
    public AffiliateAdsSyncConfig getById(Long id, Long currentUserId) {
        AffiliateAdsSyncConfig config = affiliateAdsSyncConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AFFILIATE_ADS_SYNC_CONFIG not found: " + id));
        ensureReadable(config, currentUserId);
        return config;
    }

    @Transactional(readOnly = true)
    public Page<AffiliateAdsSyncConfig> search(
            String adsOwner,
            String affiliateNetwork,
            Long currentUserId,
            Pageable pageable) {
        User currentUser = getCurrentUser(currentUserId);
        boolean admin = isAdmin(currentUser);
        String normalizedOwnerFilter = admin ? trimToNull(adsOwner) : currentUser.getUserPhoneNumber();

        Specification<AffiliateAdsSyncConfig> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(normalizedOwnerFilter)) {
                predicates.add(criteriaBuilder.equal(root.get("adsOwner"), normalizedOwnerFilter));
            }
            if (StringUtils.hasText(affiliateNetwork)) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("affiliateNetwork")),
                        affiliateNetwork.trim().toLowerCase(Locale.ROOT)));
            }

            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        return affiliateAdsSyncConfigRepository.findAll(specification, pageable);
    }

    public AffiliateAdsSyncConfig update(Long id, AffiliateAdsSyncConfig updateData, Long currentUserId) {
        if (updateData == null) {
            throw new IllegalArgumentException("updateData is required");
        }
        User currentUser = getCurrentUser(currentUserId);

        AffiliateAdsSyncConfig existing = affiliateAdsSyncConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AFFILIATE_ADS_SYNC_CONFIG not found: " + id));
        ensureWritable(existing, currentUserId);

        if (updateData.getAffiliateNetwork() != null) {
            existing.setAffiliateNetwork(updateData.getAffiliateNetwork());
        }
        if (updateData.getSyncName() != null) {
            existing.setSyncName(updateData.getSyncName());
        }
        if (updateData.getUrl() != null) {
            existing.setUrl(updateData.getUrl());
        }
        if (updateData.getMethod() != null) {
            existing.setMethod(updateData.getMethod());
        }
        if (updateData.getRequestHeaders() != null) {
            existing.setRequestHeaders(updateData.getRequestHeaders());
        }
        if (updateData.getRequestPayload() != null) {
            existing.setRequestPayload(updateData.getRequestPayload());
        }
        if (updateData.getResponsePayload() != null) {
            existing.setResponsePayload(updateData.getResponsePayload());
        }
        if (isAdmin(currentUser) && updateData.getAdsOwner() != null) {
            existing.setAdsOwner(updateData.getAdsOwner());
        }

        validateAndNormalize(existing);
        existing.setUpdateDate(LocalDateTime.now());
        return affiliateAdsSyncConfigRepository.save(existing);
    }

    public void delete(Long id, Long currentUserId) {
        AffiliateAdsSyncConfig existing = affiliateAdsSyncConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AFFILIATE_ADS_SYNC_CONFIG not found: " + id));
        ensureWritable(existing, currentUserId);
        affiliateAdsSyncConfigRepository.delete(existing);
    }

    private void validateAndNormalize(AffiliateAdsSyncConfig config) {
        config.setAffiliateNetwork(trimToNull(config.getAffiliateNetwork()));
        config.setSyncName(trimToNull(config.getSyncName()));
        config.setUrl(trimToNull(config.getUrl()));
        config.setMethod(normalizeEnumLike(config.getMethod(), "GET"));
        config.setRequestHeaders(trimToNull(config.getRequestHeaders()));
        config.setRequestPayload(trimToNull(config.getRequestPayload()));
        config.setResponsePayload(trimToNull(config.getResponsePayload()));

        if (!StringUtils.hasText(config.getAdsOwner())) {
            throw new IllegalArgumentException("adsOwner is required");
        }
        User owner = userRepository.findByUserPhoneNumber(config.getAdsOwner().trim())
                .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found by phone number: " + config.getAdsOwner()));
        config.setAdsOwner(owner.getUserPhoneNumber());

        if (!StringUtils.hasText(config.getAffiliateNetwork())) {
            throw new IllegalArgumentException("affiliateNetwork is required");
        }
        if (!StringUtils.hasText(config.getSyncName())) {
            throw new IllegalArgumentException("syncName is required");
        }
        if (!StringUtils.hasText(config.getUrl())) {
            throw new IllegalArgumentException("url is required");
        }

        AdsPlatform adsPlatform = adsPlatformRepository.findByPlatformNameIgnoreCase(config.getAffiliateNetwork())
                .orElseThrow(() -> new IllegalArgumentException("ADS_PLATFORM not found: " + config.getAffiliateNetwork()));
        config.setAffiliateNetwork(adsPlatform.getPlatformName());

        validateAllowed(config.getMethod(), "method", "POST", "GET", "PATCH", "DELETE");

        validateLength(config.getAffiliateNetwork(), "affiliateNetwork", 64);
        validateLength(config.getSyncName(), "syncName", 64);
        validateLength(config.getUrl(), "url", 1024);
        validateLength(config.getMethod(), "method", 64);
        validateLength(config.getRequestHeaders(), "requestHeaders", 1024);
        validateLength(config.getRequestPayload(), "requestPayload", 1024);
        validateLength(config.getResponsePayload(), "responsePayload", 1024);
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

    private void ensureReadable(AffiliateAdsSyncConfig config, Long currentUserId) {
        ensureAccess(config, currentUserId, "read");
    }

    private void ensureWritable(AffiliateAdsSyncConfig config, Long currentUserId) {
        ensureAccess(config, currentUserId, "modify");
    }

    private void ensureAccess(AffiliateAdsSyncConfig config, Long currentUserId, String action) {
        User currentUser = getCurrentUser(currentUserId);
        if (!isAdmin(currentUser) && !currentUser.getUserPhoneNumber().equals(config.getAdsOwner())) {
            throw new IllegalArgumentException("Unauthorized: you can only " + action + " your own sync config records");
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
