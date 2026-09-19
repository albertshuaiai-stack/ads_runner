package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsRunningAudit;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.AdsPlatformRepository;
import com.admire.cars.runner.repository.AdsRunningAuditRepository;
import com.admire.cars.runner.repository.ToolEmailRepository;
import com.admire.cars.runner.repository.UserRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class AdsRunningAuditService {

    private final AdsRunningAuditRepository adsRunningAuditRepository;
    private final AdsPlatformRepository adsPlatformRepository;
    private final ToolEmailRepository toolEmailRepository;
    private final UserRepository userRepository;

    public AdsRunningAuditService(AdsRunningAuditRepository adsRunningAuditRepository,
                                  AdsPlatformRepository adsPlatformRepository,
                                  ToolEmailRepository toolEmailRepository,
                                  UserRepository userRepository) {
        this.adsRunningAuditRepository = adsRunningAuditRepository;
        this.adsPlatformRepository = adsPlatformRepository;
        this.toolEmailRepository = toolEmailRepository;
        this.userRepository = userRepository;
    }

    public AdsRunningAudit create(AdsRunningAudit audit, Long currentUserId) {
        if (audit == null) {
            throw new IllegalArgumentException("ADS_RUNNING_AUDIT is required");
        }
        audit.setAdsOwner(resolveAdsOwner(currentUserId));

        if (StringUtils.hasText(audit.getBrand()) && audit.getBrand().length() > 128) {
            throw new IllegalArgumentException("brand must be at most 128 characters");
        }
        if (StringUtils.hasText(audit.getPlatform())) {
            adsPlatformRepository.findByPlatformNameIgnoreCase(audit.getPlatform().trim())
                    .orElseThrow(() -> new IllegalArgumentException("ADS_PLATFORM not found: " + audit.getPlatform()));
            audit.setPlatform(audit.getPlatform().trim());
        }
        if (StringUtils.hasText(audit.getEmail())) {
            if (!toolEmailRepository.findByUserName(audit.getEmail().trim()).isPresent()) {
                throw new IllegalArgumentException("TOOL_EMAL not found by userName: " + audit.getEmail());
            }
            audit.setEmail(audit.getEmail().trim());
        }

        return adsRunningAuditRepository.save(audit);
    }

    @Transactional(readOnly = true)
    public List<AdsRunningAudit> query(String adsOwner, String createDateBegin, String createDateEnd, Long currentUserId) {
        User currentUser = getCurrentUser(currentUserId);
        boolean admin = isAdmin(currentUser);

        LocalDateTime begin = parseDateBegin(createDateBegin);
        LocalDateTime end = parseDateEnd(createDateEnd);

        Specification<AdsRunningAudit> specification = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            if (begin != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createDate"), begin));
            }
            if (end != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createDate"), end));
            }

            if (admin) {
                if (StringUtils.hasText(adsOwner)) {
                    predicates.add(cb.equal(cb.lower(root.get("adsOwner")), adsOwner.toLowerCase()));
                }
            } else {
                predicates.add(cb.equal(cb.lower(root.get("adsOwner")), currentUser.getUserPhoneNumber().toLowerCase()));
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        return adsRunningAuditRepository.findAll(specification);
    }

    private LocalDateTime parseDateBegin(String input) {
        if (input == null || input.isBlank()) return null;
        try {
            LocalDateTime dt = LocalDateTime.parse(input);
            return dt.toLocalDate().atStartOfDay();
        } catch (DateTimeParseException ex) {
            LocalDate ld = LocalDate.parse(input);
            return ld.atStartOfDay();
        }
    }

    private LocalDateTime parseDateEnd(String input) {
        if (input == null || input.isBlank()) return null;
        try {
            LocalDateTime dt = LocalDateTime.parse(input);
            return dt.toLocalDate().atTime(23, 59, 59);
        } catch (DateTimeParseException ex) {
            LocalDate ld = LocalDate.parse(input);
            return ld.atTime(23, 59, 59);
        }
    }

    private User getCurrentUser(Long currentUserId) {
        return userRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found by id: " + currentUserId));
    }

    private String resolveAdsOwner(Long currentUserId) {
        return getCurrentUser(currentUserId).getUserPhoneNumber();
    }

    private boolean isAdmin(User user) {
        return user.getUserRole() != null
                && java.util.Arrays.stream(user.getUserRole().split(","))
                .map(String::trim)
                .anyMatch(role -> "admin".equalsIgnoreCase(role));
    }
}
