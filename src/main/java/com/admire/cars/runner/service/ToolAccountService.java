package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsPlatform;
import com.admire.cars.runner.entity.ToolAccount;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.AdsPlatformRepository;
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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class ToolAccountService {

    private final ToolAccountRepository toolAccountRepository;
    private final ToolEmailRepository toolEmailRepository;
    private final AdsPlatformRepository adsPlatformRepository;
    private final UserRepository userRepository;

    public ToolAccountService(
            ToolAccountRepository toolAccountRepository,
            ToolEmailRepository toolEmailRepository,
            AdsPlatformRepository adsPlatformRepository,
            UserRepository userRepository) {
        this.toolAccountRepository = toolAccountRepository;
        this.toolEmailRepository = toolEmailRepository;
        this.adsPlatformRepository = adsPlatformRepository;
        this.userRepository = userRepository;
    }

    public ToolAccount create(ToolAccount toolAccount, Long currentUserId) {
        if (toolAccount == null) {
            throw new IllegalArgumentException("TOOL_ACCOUNT is required");
        }
        User currentUser = getCurrentUser(currentUserId);
        toolAccount.setAdsOwner(currentUser.getUserPhoneNumber());
        validateAndNormalize(toolAccount);
        toolAccount.setCreateDate(LocalDateTime.now());
        return toolAccountRepository.save(toolAccount);
    }

    @Transactional(readOnly = true)
    public ToolAccount getById(Long id, Long currentUserId) {
        ToolAccount toolAccount = toolAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TOOL_ACCOUNT not found: " + id));
        ensureReadable(toolAccount, currentUserId);
        return toolAccount;
    }

    @Transactional(readOnly = true)
    public Page<ToolAccount> search(String userName, String status, Long currentUserId, Pageable pageable) {
        User currentUser = getCurrentUser(currentUserId);
        boolean admin = isAdmin(currentUser);
        Specification<ToolAccount> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (!admin) {
                predicates.add(criteriaBuilder.equal(root.get("adsOwner"), currentUser.getUserPhoneNumber()));
            }
            if (StringUtils.hasText(userName)) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("userName")),
                        "%" + userName.trim().toLowerCase(Locale.ROOT) + "%"));
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
        return toolAccountRepository.findAll(specification, pageable);
    }

    public ToolAccount update(Long id, ToolAccount updateData, Long currentUserId) {
        if (updateData == null) {
            throw new IllegalArgumentException("updateData is required");
        }
        ToolAccount existing = toolAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TOOL_ACCOUNT not found: " + id));
        ensureWritable(existing, currentUserId);

        if (updateData.getEmailAddress() != null) {
            existing.setEmailAddress(updateData.getEmailAddress());
        }
        if (updateData.getUserName() != null) {
            existing.setUserName(updateData.getUserName());
        }
        if (updateData.getPlatformName() != null) {
            existing.setPlatformName(updateData.getPlatformName());
        }
        if (updateData.getPaymentStatus() != null) {
            existing.setPaymentStatus(updateData.getPaymentStatus());
        }
        if (updateData.getStatus() != null) {
            existing.setStatus(updateData.getStatus());
        }
        if (updateData.getRegisterDate() != null) {
            existing.setRegisterDate(updateData.getRegisterDate());
        }
        if (updateData.getBalance() != null) {
            existing.setBalance(updateData.getBalance());
        }
        if (updateData.getCurrency() != null) {
            existing.setCurrency(updateData.getCurrency());
        }
        if (updateData.getRemarks() != null) {
            existing.setRemarks(updateData.getRemarks());
        }

        validateAndNormalize(existing);
        existing.setUpdateDate(LocalDateTime.now());
        return toolAccountRepository.save(existing);
    }

    public void delete(Long id, Long currentUserId) {
        ToolAccount existing = toolAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TOOL_ACCOUNT not found: " + id));
        ensureWritable(existing, currentUserId);
        toolAccountRepository.delete(existing);
    }

    private void validateAndNormalize(ToolAccount toolAccount) {
        if (!StringUtils.hasText(toolAccount.getAdsOwner())) {
            throw new IllegalArgumentException("adsOwner is required");
        }
        User owner = userRepository.findByUserPhoneNumber(toolAccount.getAdsOwner().trim())
                .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found by phone number: " + toolAccount.getAdsOwner()));
        toolAccount.setAdsOwner(owner.getUserPhoneNumber());

        toolAccount.setEmailAddress(trimToNull(toolAccount.getEmailAddress()));
        toolAccount.setUserName(trimToNull(toolAccount.getUserName()));
        toolAccount.setPlatformName(trimToNull(toolAccount.getPlatformName()));
        toolAccount.setPaymentStatus(normalizeEnumLike(toolAccount.getPaymentStatus(), "TODO"));
        toolAccount.setStatus(normalizeEnumLike(toolAccount.getStatus(), "RUNNING"));
        toolAccount.setCurrency(normalizeEnumLike(toolAccount.getCurrency(), null));
        toolAccount.setRemarks(trimToNull(toolAccount.getRemarks()));

        if (toolAccount.getEmailAddress() != null) {
            toolEmailRepository.findByEmailAddress(toolAccount.getEmailAddress())
                    .orElseThrow(() -> new IllegalArgumentException("TOOL_EMAL not found by emailAddress: " + toolAccount.getEmailAddress()));
        }
        if (toolAccount.getUserName() != null) {
            toolEmailRepository.findByUserName(toolAccount.getUserName())
                    .orElseThrow(() -> new IllegalArgumentException("TOOL_EMAL not found by userName: " + toolAccount.getUserName()));
        }
        if (toolAccount.getPlatformName() != null) {
            AdsPlatform platform = adsPlatformRepository.findByPlatformNameIgnoreCase(toolAccount.getPlatformName())
                    .orElseThrow(() -> new IllegalArgumentException("ADS_PLATFORM not found: " + toolAccount.getPlatformName()));
            toolAccount.setPlatformName(platform.getPlatformName());
        }

        validateAllowed(toolAccount.getPaymentStatus(), "paymentStatus", "TODO", "ADDED", "OTHER");
        validateAllowed(toolAccount.getStatus(), "status", "RUNNING", "PAUSED", "LOCKED", "OTHER");
        validateAllowed(toolAccount.getCurrency(), "currency", "CNY", "USD");

        validateLength(toolAccount.getEmailAddress(), "emailAddress", 64);
        validateLength(toolAccount.getUserName(), "userName", 16);
        validateLength(toolAccount.getPlatformName(), "platformName", 32);
        validateLength(toolAccount.getPaymentStatus(), "paymentStatus", 32);
        validateLength(toolAccount.getStatus(), "status", 32);
        validateLength(toolAccount.getCurrency(), "currency", 32);
        validateLength(toolAccount.getRemarks(), "remarks", 64);
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

    private void ensureReadable(ToolAccount toolAccount, Long currentUserId) {
        ensureAccess(toolAccount, currentUserId, "read");
    }

    private void ensureWritable(ToolAccount toolAccount, Long currentUserId) {
        ensureAccess(toolAccount, currentUserId, "modify");
    }

    private void ensureAccess(ToolAccount toolAccount, Long currentUserId, String action) {
        User currentUser = getCurrentUser(currentUserId);
        if (!isAdmin(currentUser) && !currentUser.getUserPhoneNumber().equals(toolAccount.getAdsOwner())) {
            throw new IllegalArgumentException("Unauthorized: you can only " + action + " your own tool accounts");
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
