package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsAccount;
import com.admire.cars.runner.entity.AdsPlatform;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.AdsAccountRepository;
import com.admire.cars.runner.repository.AdsPlatformRepository;
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
public class AdsAccountService {

    private final AdsAccountRepository adsAccountRepository;
    private final AdsPlatformRepository adsPlatformRepository;
    private final UserRepository userRepository;

    public AdsAccountService(
            AdsAccountRepository adsAccountRepository,
            AdsPlatformRepository adsPlatformRepository,
            UserRepository userRepository) {
        this.adsAccountRepository = adsAccountRepository;
        this.adsPlatformRepository = adsPlatformRepository;
        this.userRepository = userRepository;
    }

    public AdsAccount create(AdsAccount adsAccount, Long currentUserId) {
        if (adsAccount == null) {
            throw new IllegalArgumentException("ADS_ACCOUNT is required");
        }
        User currentUser = getCurrentUser(currentUserId);
        adsAccount.setAdsOwner(currentUser.getUserPhoneNumber());
        validateAndNormalize(adsAccount);
        adsAccount.setCreateDate(LocalDateTime.now());
        return adsAccountRepository.save(adsAccount);
    }

    @Transactional(readOnly = true)
    public AdsAccount getById(Long id, Long currentUserId) {
        AdsAccount adsAccount = adsAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ADS_ACCOUNT not found: " + id));
        ensureReadable(adsAccount, currentUserId);
        return adsAccount;
    }

    @Transactional(readOnly = true)
    public Page<AdsAccount> search(
            String adsAccount,
            String mccAccount,
            String agencyPlatform,
            String accountType,
            String status,
            Long currentUserId,
            Pageable pageable) {
        User currentUser = getCurrentUser(currentUserId);
        boolean admin = isAdmin(currentUser);

        Specification<AdsAccount> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (!admin) {
                predicates.add(criteriaBuilder.equal(root.get("adsOwner"), currentUser.getUserPhoneNumber()));
            }
            if (StringUtils.hasText(adsAccount)) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("adsAccount")),
                        "%" + adsAccount.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (StringUtils.hasText(mccAccount)) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("mccAccount")),
                        "%" + mccAccount.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (StringUtils.hasText(agencyPlatform)) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("agencyPlatform")),
                        "%" + agencyPlatform.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (StringUtils.hasText(accountType)) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("accountType")),
                        accountType.trim().toLowerCase(Locale.ROOT)));
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
        return adsAccountRepository.findAll(specification, pageable);
    }

    public AdsAccount update(Long id, AdsAccount updateData, Long currentUserId) {
        if (updateData == null) {
            throw new IllegalArgumentException("updateData is required");
        }

        AdsAccount existing = adsAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ADS_ACCOUNT not found: " + id));
        ensureWritable(existing, currentUserId);

        if (updateData.getAdsAccount() != null) {
            existing.setAdsAccount(updateData.getAdsAccount());
        }
        if (updateData.getMccAccount() != null) {
            existing.setMccAccount(updateData.getMccAccount());
        }
        if (updateData.getAgencyPlatform() != null) {
            existing.setAgencyPlatform(updateData.getAgencyPlatform());
        }
        if (updateData.getAccountType() != null) {
            existing.setAccountType(updateData.getAccountType());
        }
        if (updateData.getStatus() != null) {
            existing.setStatus(updateData.getStatus());
        }

        validateAndNormalize(existing);
        existing.setUpdateDate(LocalDateTime.now());
        return adsAccountRepository.save(existing);
    }

    public void delete(Long id, Long currentUserId) {
        AdsAccount existing = adsAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ADS_ACCOUNT not found: " + id));
        ensureWritable(existing, currentUserId);
        adsAccountRepository.delete(existing);
    }

    private void validateAndNormalize(AdsAccount adsAccount) {
        if (!StringUtils.hasText(adsAccount.getAdsOwner())) {
            throw new IllegalArgumentException("adsOwner is required");
        }
        User owner = userRepository.findByUserPhoneNumber(adsAccount.getAdsOwner().trim())
                .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found by phone number: " + adsAccount.getAdsOwner()));
        adsAccount.setAdsOwner(owner.getUserPhoneNumber());

        adsAccount.setAdsAccount(trimToNull(adsAccount.getAdsAccount()));
        adsAccount.setMccAccount(trimToNull(adsAccount.getMccAccount()));
        adsAccount.setAgencyPlatform(trimToNull(adsAccount.getAgencyPlatform()));
        adsAccount.setAccountType(normalizeEnumLike(adsAccount.getAccountType(), "SELF"));
        adsAccount.setStatus(normalizeEnumLike(adsAccount.getStatus(), "ACTIVE"));

        if (!StringUtils.hasText(adsAccount.getAdsAccount())) {
            throw new IllegalArgumentException("adsAccount is required");
        }

        if (adsAccount.getAgencyPlatform() != null) {
            AdsPlatform platform = adsPlatformRepository.findByPlatformNameIgnoreCase(adsAccount.getAgencyPlatform())
                    .orElseThrow(() -> new IllegalArgumentException("ADS_PLATFORM not found: " + adsAccount.getAgencyPlatform()));
            adsAccount.setAgencyPlatform(platform.getPlatformName());
        }

        validateAllowed(adsAccount.getAccountType(), "accountType", "SELF", "AGENCY");
        validateAllowed(adsAccount.getStatus(), "status", "ACTIVE", "PAUSED", "DEACTIVED");

        validateLength(adsAccount.getAdsAccount(), "adsAccount", 64);
        validateLength(adsAccount.getMccAccount(), "mccAccount", 64);
        validateLength(adsAccount.getAgencyPlatform(), "agencyPlatform", 64);
        validateLength(adsAccount.getAccountType(), "accountType", 32);
        validateLength(adsAccount.getStatus(), "status", 32);
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

    private void ensureReadable(AdsAccount adsAccount, Long currentUserId) {
        ensureAccess(adsAccount, currentUserId, "read");
    }

    private void ensureWritable(AdsAccount adsAccount, Long currentUserId) {
        ensureAccess(adsAccount, currentUserId, "modify");
    }

    private void ensureAccess(AdsAccount adsAccount, Long currentUserId, String action) {
        User currentUser = getCurrentUser(currentUserId);
        if (!isAdmin(currentUser) && !currentUser.getUserPhoneNumber().equals(adsAccount.getAdsOwner())) {
            throw new IllegalArgumentException("Unauthorized: you can only " + action + " your own ads accounts");
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
