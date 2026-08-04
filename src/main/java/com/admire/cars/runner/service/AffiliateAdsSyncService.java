package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsPlatform;
import com.admire.cars.runner.entity.AffiliateAdsSync;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.AdsPlatformRepository;
import com.admire.cars.runner.repository.AffiliateAdsSyncRepository;
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
public class AffiliateAdsSyncService {

    private final AffiliateAdsSyncRepository affiliateAdsSyncRepository;
    private final AdsPlatformRepository adsPlatformRepository;
    private final UserRepository userRepository;

    public AffiliateAdsSyncService(
            AffiliateAdsSyncRepository affiliateAdsSyncRepository,
            AdsPlatformRepository adsPlatformRepository,
            UserRepository userRepository) {
        this.affiliateAdsSyncRepository = affiliateAdsSyncRepository;
        this.adsPlatformRepository = adsPlatformRepository;
        this.userRepository = userRepository;
    }

    public AffiliateAdsSync create(AffiliateAdsSync affiliateAdsSync, Long currentUserId) {
        if (affiliateAdsSync == null) {
            throw new IllegalArgumentException("AFFILIATE_ADS_SYNC is required");
        }
        User currentUser = getCurrentUser(currentUserId);
        if (!isAdmin(currentUser) || !StringUtils.hasText(affiliateAdsSync.getAdsOwner())) {
            affiliateAdsSync.setAdsOwner(currentUser.getUserPhoneNumber());
        }
        validateAndNormalize(affiliateAdsSync);
        affiliateAdsSync.setCreateDate(LocalDateTime.now());
        return affiliateAdsSyncRepository.save(affiliateAdsSync);
    }

    @Transactional(readOnly = true)
    public AffiliateAdsSync getById(Long id, Long currentUserId) {
        AffiliateAdsSync affiliateAdsSync = affiliateAdsSyncRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AFFILIATE_ADS_SYNC not found: " + id));
        ensureReadable(affiliateAdsSync, currentUserId);
        return affiliateAdsSync;
    }

    @Transactional(readOnly = true)
    public Page<AffiliateAdsSync> search(
            String adsOwner,
            String affiliateNetwork,
            String siteName,
            String status,
            Long currentUserId,
            Pageable pageable) {
        User currentUser = getCurrentUser(currentUserId);
        boolean admin = isAdmin(currentUser);
        String normalizedOwnerFilter = admin ? trimToNull(adsOwner) : currentUser.getUserPhoneNumber();

        Specification<AffiliateAdsSync> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(normalizedOwnerFilter)) {
                predicates.add(criteriaBuilder.equal(root.get("adsOwner"), normalizedOwnerFilter));
            }
            if (StringUtils.hasText(affiliateNetwork)) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("affiliateNetwork")),
                        affiliateNetwork.trim().toLowerCase(Locale.ROOT)));
            }
            if (StringUtils.hasText(siteName)) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("siteName")),
                        "%" + siteName.trim().toLowerCase(Locale.ROOT) + "%"));
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
        return affiliateAdsSyncRepository.findAll(specification, pageable);
    }

    public AffiliateAdsSync update(Long id, AffiliateAdsSync updateData, Long currentUserId) {
        if (updateData == null) {
            throw new IllegalArgumentException("updateData is required");
        }
        User currentUser = getCurrentUser(currentUserId);

        AffiliateAdsSync existing = affiliateAdsSyncRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AFFILIATE_ADS_SYNC not found: " + id));
        ensureWritable(existing, currentUserId);

        if (updateData.getSiteName() != null) {
            existing.setSiteName(updateData.getSiteName());
        }
        if (updateData.getSiteUrl() != null) {
            existing.setSiteUrl(updateData.getSiteUrl());
        }
        if (updateData.getSiteLogoUrl() != null) {
            existing.setSiteLogoUrl(updateData.getSiteLogoUrl());
        }
        if (updateData.getTrackingUrl() != null) {
            existing.setTrackingUrl(updateData.getTrackingUrl());
        }
        if (updateData.getRegion() != null) {
            existing.setRegion(updateData.getRegion());
        }
        if (updateData.getMerchantStatus() != null) {
            existing.setMerchantStatus(updateData.getMerchantStatus());
        }
        if (updateData.getCommissions() != null) {
            existing.setCommissions(updateData.getCommissions());
        }
        if (updateData.getAdvCatagory() != null) {
            existing.setAdvCatagory(updateData.getAdvCatagory());
        }
        if (updateData.getDeeplink() != null) {
            existing.setDeeplink(updateData.getDeeplink());
        }
        if (updateData.getAffiliateNetwork() != null) {
            existing.setAffiliateNetwork(updateData.getAffiliateNetwork());
        }
        if (updateData.getStatus() != null) {
            existing.setStatus(updateData.getStatus());
        }
        if (isAdmin(currentUser) && updateData.getAdsOwner() != null) {
            existing.setAdsOwner(updateData.getAdsOwner());
        }

        validateAndNormalize(existing);
        existing.setUpdateDate(LocalDateTime.now());
        return affiliateAdsSyncRepository.save(existing);
    }

    public void delete(Long id, Long currentUserId) {
        AffiliateAdsSync existing = affiliateAdsSyncRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AFFILIATE_ADS_SYNC not found: " + id));
        ensureWritable(existing, currentUserId);
        affiliateAdsSyncRepository.delete(existing);
    }

    private void validateAndNormalize(AffiliateAdsSync affiliateAdsSync) {
        affiliateAdsSync.setSiteName(trimToNull(affiliateAdsSync.getSiteName()));
        affiliateAdsSync.setSiteUrl(trimToNull(affiliateAdsSync.getSiteUrl()));
        affiliateAdsSync.setSiteLogoUrl(trimToNull(affiliateAdsSync.getSiteLogoUrl()));
        affiliateAdsSync.setTrackingUrl(trimToNull(affiliateAdsSync.getTrackingUrl()));
        affiliateAdsSync.setRegion(trimToNull(affiliateAdsSync.getRegion()));
        affiliateAdsSync.setMerchantStatus(trimToNull(affiliateAdsSync.getMerchantStatus()));
        affiliateAdsSync.setCommissions(trimToNull(affiliateAdsSync.getCommissions()));
        affiliateAdsSync.setAdvCatagory(trimToNull(affiliateAdsSync.getAdvCatagory()));
        affiliateAdsSync.setDeeplink(trimToNull(affiliateAdsSync.getDeeplink()));
        affiliateAdsSync.setAffiliateNetwork(trimToNull(affiliateAdsSync.getAffiliateNetwork()));
        affiliateAdsSync.setStatus(normalizeEnumLike(affiliateAdsSync.getStatus(), "ENABLED"));

        if (!StringUtils.hasText(affiliateAdsSync.getAdsOwner())) {
            throw new IllegalArgumentException("adsOwner is required");
        }
        User owner = userRepository.findByUserPhoneNumber(affiliateAdsSync.getAdsOwner().trim())
                .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found by phone number: " + affiliateAdsSync.getAdsOwner()));
        affiliateAdsSync.setAdsOwner(owner.getUserPhoneNumber());

        if (!StringUtils.hasText(affiliateAdsSync.getSiteName())) {
            throw new IllegalArgumentException("siteName is required");
        }
        if (!StringUtils.hasText(affiliateAdsSync.getAffiliateNetwork())) {
            throw new IllegalArgumentException("affiliateNetwork is required");
        }

        AdsPlatform adsPlatform = adsPlatformRepository.findByPlatformNameIgnoreCase(affiliateAdsSync.getAffiliateNetwork())
                .orElseThrow(() -> new IllegalArgumentException("ADS_PLATFORM not found: " + affiliateAdsSync.getAffiliateNetwork()));
        affiliateAdsSync.setAffiliateNetwork(adsPlatform.getPlatformName());

        validateAllowed(affiliateAdsSync.getStatus(), "status", "ENABLED", "DISABLED");

        validateLength(affiliateAdsSync.getSiteName(), "siteName", 128);
        validateLength(affiliateAdsSync.getSiteUrl(), "siteUrl", 1024);
        validateLength(affiliateAdsSync.getSiteLogoUrl(), "siteLogoUrl", 1024);
        validateLength(affiliateAdsSync.getTrackingUrl(), "trackingUrl", 1024);
        validateLength(affiliateAdsSync.getRegion(), "region", 512);
        validateLength(affiliateAdsSync.getMerchantStatus(), "merchantStatus", 128);
        validateLength(affiliateAdsSync.getCommissions(), "commissions", 512);
        validateLength(affiliateAdsSync.getAdvCatagory(), "advCatagory", 64);
        validateLength(affiliateAdsSync.getDeeplink(), "deeplink", 64);
        validateLength(affiliateAdsSync.getAffiliateNetwork(), "affiliateNetwork", 64);
        validateLength(affiliateAdsSync.getStatus(), "status", 32);
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

    private void ensureReadable(AffiliateAdsSync affiliateAdsSync, Long currentUserId) {
        ensureAccess(affiliateAdsSync, currentUserId, "read");
    }

    private void ensureWritable(AffiliateAdsSync affiliateAdsSync, Long currentUserId) {
        ensureAccess(affiliateAdsSync, currentUserId, "modify");
    }

    private void ensureAccess(AffiliateAdsSync affiliateAdsSync, Long currentUserId, String action) {
        User currentUser = getCurrentUser(currentUserId);
        if (!isAdmin(currentUser) && !currentUser.getUserPhoneNumber().equals(affiliateAdsSync.getAdsOwner())) {
            throw new IllegalArgumentException("Unauthorized: you can only " + action + " your own affiliate sync records");
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
