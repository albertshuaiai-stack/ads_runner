package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.ShiftLinkLog;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.ShiftLinkLogRepository;
import com.admire.cars.runner.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@Transactional
public class ShiftLinkLogService {

    private final ShiftLinkLogRepository shiftLinkLogRepository;
    private final UserRepository userRepository;

    public ShiftLinkLogService(ShiftLinkLogRepository shiftLinkLogRepository, UserRepository userRepository) {
        this.shiftLinkLogRepository = shiftLinkLogRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<ShiftLinkLog> queryLogs(
            String adsType,
            String platformName,
            String adsName,
            Long currentUserId,
            Pageable pageable) {
        User currentUser = getCurrentUser(currentUserId);
        String normalizedAdsType = normalizeAdsType(adsType);
        String normalizedPlatformName = normalizeOptional(platformName);
        String normalizedAdsName = normalizeOptional(adsName);
        boolean admin = isAdmin(currentUser);

        Specification<ShiftLinkLog> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!admin) {
                predicates.add(criteriaBuilder.equal(root.get("adsOwner"), currentUser.getUserPhoneNumber()));
            }
            if (normalizedAdsType != null) {
                predicates.add(criteriaBuilder.equal(root.get("adsType"), normalizedAdsType));
            }
            if (normalizedPlatformName != null) {
                predicates.add(criteriaBuilder.equal(root.get("platformName"), normalizedPlatformName));
            }
            if (normalizedAdsName != null) {
                predicates.add(criteriaBuilder.equal(root.get("adsName"), normalizedAdsName));
            }
            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        return shiftLinkLogRepository.findAll(specification, pageable);
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

    private String normalizeAdsType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        if ("NORMAL".equals(normalized)) {
            return "Normal";
        }
        if ("MATRIX".equals(normalized)) {
            return "Matrix";
        }
        throw new IllegalArgumentException("adsType must be Normal or Matrix");
    }

    private String normalizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

}
