package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.ToolPaypal;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.ToolPaypalRepository;
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
public class ToolPaypalService {

    private final ToolPaypalRepository toolPaypalRepository;
    private final UserRepository userRepository;

    public ToolPaypalService(ToolPaypalRepository toolPaypalRepository, UserRepository userRepository) {
        this.toolPaypalRepository = toolPaypalRepository;
        this.userRepository = userRepository;
    }

    public ToolPaypal create(ToolPaypal toolPaypal, Long currentUserId) {
        if (toolPaypal == null) {
            throw new IllegalArgumentException("TOOL_PAYPAL is required");
        }
        User currentUser = getCurrentUser(currentUserId);
        toolPaypal.setAdsOwner(currentUser.getUserPhoneNumber());
        validateAndNormalize(toolPaypal);
        toolPaypal.setCreateDate(LocalDateTime.now());
        return toolPaypalRepository.save(toolPaypal);
    }

    @Transactional(readOnly = true)
    public ToolPaypal getById(Long id, Long currentUserId) {
        ToolPaypal toolPaypal = toolPaypalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TOOL_PAYPAL not found: " + id));
        ensureReadable(toolPaypal, currentUserId);
        return toolPaypal;
    }

    @Transactional(readOnly = true)
    public Page<ToolPaypal> search(String paypalEmail, String primaryEmail, Long currentUserId, Pageable pageable) {
        User currentUser = getCurrentUser(currentUserId);
        boolean admin = isAdmin(currentUser);
        Specification<ToolPaypal> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (!admin) {
                predicates.add(criteriaBuilder.equal(root.get("adsOwner"), currentUser.getUserPhoneNumber()));
            }
            if (StringUtils.hasText(paypalEmail)) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("paypalEmail")),
                        "%" + paypalEmail.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (StringUtils.hasText(primaryEmail)) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("primaryEmail")),
                        "%" + primaryEmail.trim().toLowerCase(Locale.ROOT) + "%"));
            }

            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return toolPaypalRepository.findAll(specification, pageable);
    }

    public ToolPaypal update(Long id, ToolPaypal updateData, Long currentUserId) {
        if (updateData == null) {
            throw new IllegalArgumentException("updateData is required");
        }
        ToolPaypal existing = toolPaypalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TOOL_PAYPAL not found: " + id));
        ensureWritable(existing, currentUserId);

        if (updateData.getPaypalEmail() != null) {
            existing.setPaypalEmail(updateData.getPaypalEmail());
        }
        if (updateData.getPrimaryEmail() != null) {
            existing.setPrimaryEmail(updateData.getPrimaryEmail());
        }
        if (updateData.getPaypalId() != null) {
            existing.setPaypalId(updateData.getPaypalId());
        }

        validateAndNormalize(existing);
        existing.setUpdateDate(LocalDateTime.now());
        return toolPaypalRepository.save(existing);
    }

    public void delete(Long id, Long currentUserId) {
        ToolPaypal existing = toolPaypalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TOOL_PAYPAL not found: " + id));
        ensureWritable(existing, currentUserId);
        toolPaypalRepository.delete(existing);
    }

    private void validateAndNormalize(ToolPaypal toolPaypal) {
        if (!StringUtils.hasText(toolPaypal.getAdsOwner())) {
            throw new IllegalArgumentException("adsOwner is required");
        }
        User owner = userRepository.findByUserPhoneNumber(toolPaypal.getAdsOwner().trim())
                .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found by phone number: " + toolPaypal.getAdsOwner()));
        toolPaypal.setAdsOwner(owner.getUserPhoneNumber());

        toolPaypal.setPaypalEmail(trimToNull(toolPaypal.getPaypalEmail()));
        toolPaypal.setPrimaryEmail(trimToNull(toolPaypal.getPrimaryEmail()));
        toolPaypal.setPaypalId(trimToNull(toolPaypal.getPaypalId()));

        if (!StringUtils.hasText(toolPaypal.getPaypalEmail())) {
            throw new IllegalArgumentException("paypalEmail is required");
        }

        validateLength(toolPaypal.getPaypalEmail(), "paypalEmail", 128);
        validateLength(toolPaypal.getPrimaryEmail(), "primaryEmail", 128);
        validateLength(toolPaypal.getPaypalId(), "paypalId", 64);
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

    private void ensureReadable(ToolPaypal toolPaypal, Long currentUserId) {
        ensureAccess(toolPaypal, currentUserId, "read");
    }

    private void ensureWritable(ToolPaypal toolPaypal, Long currentUserId) {
        ensureAccess(toolPaypal, currentUserId, "modify");
    }

    private void ensureAccess(ToolPaypal toolPaypal, Long currentUserId, String action) {
        User currentUser = getCurrentUser(currentUserId);
        if (!isAdmin(currentUser) && !currentUser.getUserPhoneNumber().equals(toolPaypal.getAdsOwner())) {
            throw new IllegalArgumentException("Unauthorized: you can only " + action + " your own tool paypals");
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
