package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.ToolOutcome;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.ToolOutcomeRepository;
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
public class ToolOutcomeService {

    private final ToolOutcomeRepository toolOutcomeRepository;
    private final UserRepository userRepository;

    public ToolOutcomeService(ToolOutcomeRepository toolOutcomeRepository, UserRepository userRepository) {
        this.toolOutcomeRepository = toolOutcomeRepository;
        this.userRepository = userRepository;
    }

    public ToolOutcome create(ToolOutcome toolOutcome, Long currentUserId) {
        if (toolOutcome == null) {
            throw new IllegalArgumentException("TOOL_OUTCOME is required");
        }
        User currentUser = getCurrentUser(currentUserId);
        toolOutcome.setAdsOwner(currentUser.getUserPhoneNumber());
        validateAndNormalize(toolOutcome);
        toolOutcome.setCreateDate(LocalDateTime.now());
        return toolOutcomeRepository.save(toolOutcome);
    }

    @Transactional(readOnly = true)
    public ToolOutcome getById(Long id, Long currentUserId) {
        ToolOutcome toolOutcome = toolOutcomeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TOOL_OUTCOME not found: " + id));
        ensureReadable(toolOutcome, currentUserId);
        return toolOutcome;
    }

    @Transactional(readOnly = true)
    public Page<ToolOutcome> search(
            String outcomeType,
            LocalDate payDateBegin,
            LocalDate payDateEnd,
            Long currentUserId,
            Pageable pageable) {
        User currentUser = getCurrentUser(currentUserId);
        boolean admin = isAdmin(currentUser);
        Specification<ToolOutcome> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (!admin) {
                predicates.add(criteriaBuilder.equal(root.get("adsOwner"), currentUser.getUserPhoneNumber()));
            }
            if (StringUtils.hasText(outcomeType)) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("outcomeType")),
                        normalizeOutcomeType(outcomeType).toLowerCase(Locale.ROOT)));
            }
            if (payDateBegin != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("payDate"), payDateBegin));
            }
            if (payDateEnd != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("payDate"), payDateEnd));
            }

            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return toolOutcomeRepository.findAll(specification, pageable);
    }

    public ToolOutcome update(Long id, ToolOutcome updateData, Long currentUserId) {
        if (updateData == null) {
            throw new IllegalArgumentException("updateData is required");
        }
        ToolOutcome existing = toolOutcomeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TOOL_OUTCOME not found: " + id));
        ensureWritable(existing, currentUserId);

        if (updateData.getOutcomeType() != null) {
            existing.setOutcomeType(updateData.getOutcomeType());
        }
        if (updateData.getOutcomeAmount() != null) {
            existing.setOutcomeAmount(updateData.getOutcomeAmount());
        }
        if (updateData.getCurrency() != null) {
            existing.setCurrency(updateData.getCurrency());
        }
        if (updateData.getPayDate() != null) {
            existing.setPayDate(updateData.getPayDate());
        }
        if (updateData.getRemarks() != null) {
            existing.setRemarks(updateData.getRemarks());
        }

        validateAndNormalize(existing);
        existing.setUpdateDate(LocalDateTime.now());
        return toolOutcomeRepository.save(existing);
    }

    public void delete(Long id, Long currentUserId) {
        ToolOutcome existing = toolOutcomeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TOOL_OUTCOME not found: " + id));
        ensureWritable(existing, currentUserId);
        toolOutcomeRepository.delete(existing);
    }

    private void validateAndNormalize(ToolOutcome toolOutcome) {
        if (!StringUtils.hasText(toolOutcome.getAdsOwner())) {
            throw new IllegalArgumentException("adsOwner is required");
        }
        User owner = userRepository.findByUserPhoneNumber(toolOutcome.getAdsOwner().trim())
                .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found by phone number: " + toolOutcome.getAdsOwner()));
        toolOutcome.setAdsOwner(owner.getUserPhoneNumber());

        toolOutcome.setOutcomeType(normalizeOutcomeType(toolOutcome.getOutcomeType()));
        toolOutcome.setCurrency(normalizeEnumLike(toolOutcome.getCurrency(), null));
        toolOutcome.setRemarks(trimToNull(toolOutcome.getRemarks()));

        if (toolOutcome.getOutcomeAmount() != null && toolOutcome.getOutcomeAmount().signum() < 0) {
            throw new IllegalArgumentException("outcomeAmount must be greater than or equal to 0");
        }

        validateAllowed(toolOutcome.getOutcomeType(),
                "outcomeType",
                "MEDIABY",
                "IP PROXY",
                "VPN",
                "ADSPOWER BROWSER",
                "SEMRUSH",
                "OTHERS");
        validateAllowed(toolOutcome.getCurrency(), "currency", "CNY", "USD");

        validateLength(toolOutcome.getOutcomeType(), "outcomeType", 64);
        validateLength(toolOutcome.getCurrency(), "currency", 32);
        validateLength(toolOutcome.getRemarks(), "remarks", 128);
    }

    private String normalizeOutcomeType(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "MEDIABY", "MEDIA BY" -> "MEDIABY";
            case "IP PROXY", "IP_PROXY" -> "IP PROXY";
            case "VPN" -> "VPN";
            case "ADSPOWER BROWSER", "ADSPOWER_BROWSER" -> "ADSPOWER BROWSER";
            case "SEMRUSH" -> "SEMRUSH";
            case "OTHERS", "OTHER" -> "OTHERS";
            default -> upper;
        };
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

    private void ensureReadable(ToolOutcome toolOutcome, Long currentUserId) {
        ensureAccess(toolOutcome, currentUserId, "read");
    }

    private void ensureWritable(ToolOutcome toolOutcome, Long currentUserId) {
        ensureAccess(toolOutcome, currentUserId, "modify");
    }

    private void ensureAccess(ToolOutcome toolOutcome, Long currentUserId, String action) {
        User currentUser = getCurrentUser(currentUserId);
        if (!isAdmin(currentUser) && !currentUser.getUserPhoneNumber().equals(toolOutcome.getAdsOwner())) {
            throw new IllegalArgumentException("Unauthorized: you can only " + action + " your own tool outcomes");
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
