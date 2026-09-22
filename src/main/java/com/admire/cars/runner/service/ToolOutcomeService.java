package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.ToolOutcome;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.ToolOutcomeRepository;
import com.admire.cars.runner.repository.AdsAccountRepository;
import com.admire.cars.runner.repository.ToolIpRepository;
import com.admire.cars.runner.repository.ToolCloudPhoneRepository;
import com.admire.cars.runner.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    private final AdsAccountRepository adsAccountRepository;
    private final com.admire.cars.runner.repository.ToolIpRepository toolIpRepository;
    private final com.admire.cars.runner.repository.ToolCloudPhoneRepository toolCloudPhoneRepository;

    public ToolOutcomeService(ToolOutcomeRepository toolOutcomeRepository, AdsAccountRepository adsAccountRepository, com.admire.cars.runner.repository.ToolIpRepository toolIpRepository, com.admire.cars.runner.repository.ToolCloudPhoneRepository toolCloudPhoneRepository, UserRepository userRepository) {
        this.toolOutcomeRepository = toolOutcomeRepository;
        this.userRepository = userRepository;
        this.adsAccountRepository = adsAccountRepository;
        this.toolIpRepository = toolIpRepository;
        this.toolCloudPhoneRepository = toolCloudPhoneRepository;
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
                com.admire.cars.runner.entity.OutcomeType ot = com.admire.cars.runner.entity.OutcomeType.fromString(outcomeType);
                if (ot != null) {
                    predicates.add(criteriaBuilder.equal(root.get("outcomeType"), ot));
                }
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

    @Transactional(readOnly = true)
    public List<ToolOutcome> findForReport(
            LocalDate payDateBegin,
            LocalDate payDateEnd,
            Long currentUserId) {
        User currentUser = getCurrentUser(currentUserId);
        boolean admin = isAdmin(currentUser);
        Specification<ToolOutcome> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (!admin) {
                predicates.add(criteriaBuilder.equal(root.get("adsOwner"), currentUser.getUserPhoneNumber()));
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
        return toolOutcomeRepository.findAll(
                specification,
                Sort.by(Sort.Direction.ASC, "payDate").and(Sort.by(Sort.Direction.ASC, "id")));
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
        if (updateData.getAdsAccount() != null) {
            existing.setAdsAccount(updateData.getAdsAccount());
        }
        if (updateData.getIp() != null) {
            existing.setIp(updateData.getIp());
        }
        if (updateData.getPhoneNumber() != null) {
            existing.setPhoneNumber(updateData.getPhoneNumber());
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

        // outcomeType is now an enum on entity; assume DTO converted string to enum already
        toolOutcome.setCurrency(normalizeEnumLike(toolOutcome.getCurrency(), null));
        toolOutcome.setRemarks(trimToNull(toolOutcome.getRemarks()));

        toolOutcome.setAdsAccount(trimToNull(toolOutcome.getAdsAccount()));
        toolOutcome.setPhoneNumber(trimToNull(toolOutcome.getPhoneNumber()));
        toolOutcome.setIp(trimToNull(toolOutcome.getIp()));

        if (toolOutcome.getOutcomeAmount() != null && toolOutcome.getOutcomeAmount().signum() < 0) {
            throw new IllegalArgumentException("outcomeAmount must be greater than or equal to 0");
        }

        // outcomeType is an enum, no need to validate allowed values here. If provided, it must be one of OutcomeType.
        validateAllowed(toolOutcome.getCurrency(), "currency", "CNY", "USD");

        // when MEDIA_BY, adsAccount is required and must exist in ADS_ACCOUNT
        if (toolOutcome.getOutcomeType() == com.admire.cars.runner.entity.OutcomeType.MEDIA_BY) {
            if (!StringUtils.hasText(toolOutcome.getAdsAccount())) {
                throw new IllegalArgumentException("adsAccount is required when outcomeType is MEDIA_BY");
            }
            if (!adsAccountRepository.existsByAdsAccountIgnoreCase(toolOutcome.getAdsAccount().trim())) {
                throw new IllegalArgumentException("ADS_ACCOUNT not found by adsAccount: " + toolOutcome.getAdsAccount());
            }
            toolOutcome.setAdsAccount(toolOutcome.getAdsAccount().trim());
        }

        // when STATIC_IP, ip is required and must exist in TOOL_IP
        if (toolOutcome.getOutcomeType() == com.admire.cars.runner.entity.OutcomeType.STATIC_IP) {
            if (!StringUtils.hasText(toolOutcome.getIp())) {
                throw new IllegalArgumentException("ip is required when outcomeType is STATIC_IP");
            }
            if (!toolIpRepository.existsByIpIgnoreCase(toolOutcome.getIp().trim())) {
                throw new IllegalArgumentException("TOOL_IP not found by ip: " + toolOutcome.getIp());
            }
            toolOutcome.setIp(toolOutcome.getIp().trim());
        }

        // when CLOUD_PHONE, phoneNumber is required and must exist in TOOL_CLOUD_PHONE
        if (toolOutcome.getOutcomeType() == com.admire.cars.runner.entity.OutcomeType.CLOUD_PHONE) {
            if (!StringUtils.hasText(toolOutcome.getPhoneNumber())) {
                throw new IllegalArgumentException("phoneNumber is required when outcomeType is CLOUD_PHONE");
            }
            if (!toolCloudPhoneRepository.existsByPhoneNumberIgnoreCase(toolOutcome.getPhoneNumber().trim())) {
                throw new IllegalArgumentException("TOOL_CLOUD_PHONE not found by phoneNumber: " + toolOutcome.getPhoneNumber());
            }
            toolOutcome.setPhoneNumber(toolOutcome.getPhoneNumber().trim());
        }

        validateLength(toolOutcome.getOutcomeType() == null ? null : toolOutcome.getOutcomeType().getNormalized(), "outcomeType", 64);
        validateLength(toolOutcome.getCurrency(), "currency", 32);
        validateLength(toolOutcome.getRemarks(), "remarks", 128);
        validateLength(toolOutcome.getAdsAccount(), "adsAccount", 64);
        validateLength(toolOutcome.getIp(), "ip", 64);
        validateLength(toolOutcome.getPhoneNumber(), "phoneNumber", 64);
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

    @Transactional(readOnly = true)
    public List<ToolOutcome> findAllForUser(Long currentUserId) {
        User currentUser = getCurrentUser(currentUserId);
        if (isAdmin(currentUser)) {
            return toolOutcomeRepository.findAll();
        }
        Specification<ToolOutcome> spec = (root, query, cb) ->
                cb.equal(root.get("adsOwner"), currentUser.getUserPhoneNumber());
        return toolOutcomeRepository.findAll(spec);
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
