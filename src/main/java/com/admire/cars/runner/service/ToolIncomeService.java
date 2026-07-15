package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsPlatform;
import com.admire.cars.runner.entity.ToolIncome;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.AdsPlatformRepository;
import com.admire.cars.runner.repository.ToolEmailRepository;
import com.admire.cars.runner.repository.ToolIncomeRepository;
import com.admire.cars.runner.repository.ToolPaypalRepository;
import com.admire.cars.runner.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class ToolIncomeService {

    private final ToolIncomeRepository toolIncomeRepository;
    private final AdsPlatformRepository adsPlatformRepository;
    private final ToolEmailRepository toolEmailRepository;
    private final ToolPaypalRepository toolPaypalRepository;
    private final UserRepository userRepository;

    public ToolIncomeService(
            ToolIncomeRepository toolIncomeRepository,
            AdsPlatformRepository adsPlatformRepository,
            ToolEmailRepository toolEmailRepository,
            ToolPaypalRepository toolPaypalRepository,
            UserRepository userRepository) {
        this.toolIncomeRepository = toolIncomeRepository;
        this.adsPlatformRepository = adsPlatformRepository;
        this.toolEmailRepository = toolEmailRepository;
        this.toolPaypalRepository = toolPaypalRepository;
        this.userRepository = userRepository;
    }

    public ToolIncome create(ToolIncome toolIncome, Long currentUserId) {
        if (toolIncome == null) {
            throw new IllegalArgumentException("TOOL_INCOME is required");
        }
        User currentUser = getCurrentUser(currentUserId);
        toolIncome.setAdsOwner(currentUser.getUserPhoneNumber());
        validateAndNormalize(toolIncome);
        toolIncome.setCreateDate(LocalDateTime.now());
        return toolIncomeRepository.save(toolIncome);
    }

    @Transactional(readOnly = true)
    public ToolIncome getById(Long id, Long currentUserId) {
        ToolIncome toolIncome = toolIncomeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TOOL_INCOME not found: " + id));
        ensureReadable(toolIncome, currentUserId);
        return toolIncome;
    }

    @Transactional(readOnly = true)
    public Page<ToolIncome> search(
            String platformName,
            String userName,
            String paypalAccount,
            LocalDate payoutDateBegin,
            LocalDate payoutDateEnd,
            Long currentUserId,
            Pageable pageable) {
        User currentUser = getCurrentUser(currentUserId);
        boolean admin = isAdmin(currentUser);
        Specification<ToolIncome> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (!admin) {
                predicates.add(criteriaBuilder.equal(root.get("adsOwner"), currentUser.getUserPhoneNumber()));
            }
            if (StringUtils.hasText(platformName)) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("platformName")),
                        "%" + platformName.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (StringUtils.hasText(userName)) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("userName")),
                        "%" + userName.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (StringUtils.hasText(paypalAccount)) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("paypalAccount")),
                        "%" + paypalAccount.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (payoutDateBegin != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("payoutDate"), payoutDateBegin));
            }
            if (payoutDateEnd != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("payoutDate"), payoutDateEnd));
            }

            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return toolIncomeRepository.findAll(specification, pageable);
    }

    public ToolIncome update(Long id, ToolIncome updateData, Long currentUserId) {
        if (updateData == null) {
            throw new IllegalArgumentException("updateData is required");
        }
        ToolIncome existing = toolIncomeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TOOL_INCOME not found: " + id));
        ensureWritable(existing, currentUserId);

        if (updateData.getPlatformName() != null) {
            existing.setPlatformName(updateData.getPlatformName());
        }
        if (updateData.getUserName() != null) {
            existing.setUserName(updateData.getUserName());
        }
        if (updateData.getIncomeAmount() != null) {
            existing.setIncomeAmount(updateData.getIncomeAmount());
        }
        if (updateData.getCurrency() != null) {
            existing.setCurrency(updateData.getCurrency());
        }
        if (updateData.getPaymentMethod() != null) {
            existing.setPaymentMethod(updateData.getPaymentMethod());
        }
        if (updateData.getPaypalAccount() != null) {
            existing.setPaypalAccount(updateData.getPaypalAccount());
        }
        if (updateData.getPayoutDate() != null) {
            existing.setPayoutDate(updateData.getPayoutDate());
        }
        if (updateData.getRemarks() != null) {
            existing.setRemarks(updateData.getRemarks());
        }

        validateAndNormalize(existing);
        existing.setUpdateDate(LocalDateTime.now());
        return toolIncomeRepository.save(existing);
    }

    public void delete(Long id, Long currentUserId) {
        ToolIncome existing = toolIncomeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TOOL_INCOME not found: " + id));
        ensureWritable(existing, currentUserId);
        toolIncomeRepository.delete(existing);
    }

    private void validateAndNormalize(ToolIncome toolIncome) {
        if (!StringUtils.hasText(toolIncome.getAdsOwner())) {
            throw new IllegalArgumentException("adsOwner is required");
        }
        User owner = userRepository.findByUserPhoneNumber(toolIncome.getAdsOwner().trim())
                .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found by phone number: " + toolIncome.getAdsOwner()));
        toolIncome.setAdsOwner(owner.getUserPhoneNumber());

        toolIncome.setPlatformName(trimToNull(toolIncome.getPlatformName()));
        toolIncome.setUserName(trimToNull(toolIncome.getUserName()));
        toolIncome.setCurrency(normalizeEnumLike(toolIncome.getCurrency(), null));
        toolIncome.setPaymentMethod(trimToNull(toolIncome.getPaymentMethod()));
        toolIncome.setPaypalAccount(trimToNull(toolIncome.getPaypalAccount()));
        toolIncome.setRemarks(trimToNull(toolIncome.getRemarks()));

        if (toolIncome.getIncomeAmount() != null && toolIncome.getIncomeAmount().signum() < 0) {
            throw new IllegalArgumentException("incomeAmount must be greater than or equal to 0");
        }

        if (toolIncome.getPlatformName() != null) {
            AdsPlatform platform = adsPlatformRepository.findByPlatformNameIgnoreCase(toolIncome.getPlatformName())
                    .orElseThrow(() -> new IllegalArgumentException("ADS_PLATFORM not found: " + toolIncome.getPlatformName()));
            toolIncome.setPlatformName(platform.getPlatformName());
        }
        if (toolIncome.getUserName() != null) {
            toolEmailRepository.findByUserName(toolIncome.getUserName())
                    .orElseThrow(() -> new IllegalArgumentException("TOOL_EMAL not found by userName: " + toolIncome.getUserName()));
        }
        if (toolIncome.getPaypalAccount() != null) {
            toolPaypalRepository.findByPaypalEmail(toolIncome.getPaypalAccount())
                    .orElseThrow(() -> new IllegalArgumentException("TOOL_PAYPAL not found by paypalEmail: " + toolIncome.getPaypalAccount()));
        }

        validateAllowed(toolIncome.getCurrency(), "currency", "CNY", "USD");

        validateLength(toolIncome.getPlatformName(), "platformName", 32);
        validateLength(toolIncome.getUserName(), "userName", 16);
        validateLength(toolIncome.getCurrency(), "currency", 32);
        validateLength(toolIncome.getPaymentMethod(), "paymentMethod", 64);
        validateLength(toolIncome.getPaypalAccount(), "paypalAccount", 128);
        validateLength(toolIncome.getRemarks(), "remarks", 128);
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

    private void ensureReadable(ToolIncome toolIncome, Long currentUserId) {
        ensureAccess(toolIncome, currentUserId, "read");
    }

    private void ensureWritable(ToolIncome toolIncome, Long currentUserId) {
        ensureAccess(toolIncome, currentUserId, "modify");
    }

    private void ensureAccess(ToolIncome toolIncome, Long currentUserId, String action) {
        User currentUser = getCurrentUser(currentUserId);
        if (!isAdmin(currentUser) && !currentUser.getUserPhoneNumber().equals(toolIncome.getAdsOwner())) {
            throw new IllegalArgumentException("Unauthorized: you can only " + action + " your own tool incomes");
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
