package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AffiliateAdsSyncConfig;
import com.admire.cars.runner.entity.AffiliateAdsSyncTask;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.AffiliateAdsSyncConfigRepository;
import com.admire.cars.runner.repository.AffiliateAdsSyncTaskRepository;
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
public class AffiliateAdsSyncTaskService {

    private final AffiliateAdsSyncTaskRepository affiliateAdsSyncTaskRepository;
    private final AffiliateAdsSyncConfigRepository affiliateAdsSyncConfigRepository;
    private final UserRepository userRepository;

    public AffiliateAdsSyncTaskService(
            AffiliateAdsSyncTaskRepository affiliateAdsSyncTaskRepository,
            AffiliateAdsSyncConfigRepository affiliateAdsSyncConfigRepository,
            UserRepository userRepository) {
        this.affiliateAdsSyncTaskRepository = affiliateAdsSyncTaskRepository;
        this.affiliateAdsSyncConfigRepository = affiliateAdsSyncConfigRepository;
        this.userRepository = userRepository;
    }

    public AffiliateAdsSyncTask create(AffiliateAdsSyncTask task, Long currentUserId) {
        if (task == null) {
            throw new IllegalArgumentException("AFFILIATE_ADS_SYNC_TASK is required");
        }
        User currentUser = getCurrentUser(currentUserId);
        if (!isAdmin(currentUser) || !StringUtils.hasText(task.getAdsOwner())) {
            task.setAdsOwner(currentUser.getUserPhoneNumber());
        }
        validateAndNormalize(task, currentUser);
        task.setCreateDate(LocalDateTime.now());
        return affiliateAdsSyncTaskRepository.save(task);
    }

    @Transactional(readOnly = true)
    public AffiliateAdsSyncTask getById(Long id, Long currentUserId) {
        AffiliateAdsSyncTask task = affiliateAdsSyncTaskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AFFILIATE_ADS_SYNC_TASK not found: " + id));
        ensureReadable(task, currentUserId);
        return task;
    }

    @Transactional(readOnly = true)
    public Page<AffiliateAdsSyncTask> search(
            String adsOwner,
            Long affiliateAdsSyncConfigId,
            Long currentUserId,
            Pageable pageable) {
        User currentUser = getCurrentUser(currentUserId);
        boolean admin = isAdmin(currentUser);
        String normalizedOwnerFilter = admin ? trimToNull(adsOwner) : currentUser.getUserPhoneNumber();

        Specification<AffiliateAdsSyncTask> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(normalizedOwnerFilter)) {
                predicates.add(criteriaBuilder.equal(root.get("adsOwner"), normalizedOwnerFilter));
            }
            if (affiliateAdsSyncConfigId != null) {
                predicates.add(criteriaBuilder.equal(root.get("affiliateAdsSyncConfigId"), affiliateAdsSyncConfigId));
            }

            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        return affiliateAdsSyncTaskRepository.findAll(specification, pageable);
    }

    public AffiliateAdsSyncTask update(Long id, AffiliateAdsSyncTask updateData, Long currentUserId) {
        if (updateData == null) {
            throw new IllegalArgumentException("updateData is required");
        }
        User currentUser = getCurrentUser(currentUserId);

        AffiliateAdsSyncTask existing = affiliateAdsSyncTaskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AFFILIATE_ADS_SYNC_TASK not found: " + id));
        ensureWritable(existing, currentUserId);

        if (updateData.getAffiliateAdsSyncConfigId() != null) {
            existing.setAffiliateAdsSyncConfigId(updateData.getAffiliateAdsSyncConfigId());
        }
        if (updateData.getRegion() != null) {
            existing.setRegion(updateData.getRegion());
        }
        if (updateData.getSyncType() != null) {
            existing.setSyncType(updateData.getSyncType());
        }
        if (updateData.getCron() != null) {
            existing.setCron(updateData.getCron());
        }
        if (updateData.getTotalCount() != null) {
            existing.setTotalCount(updateData.getTotalCount());
        }
        if (updateData.getSuccessCount() != null) {
            existing.setSuccessCount(updateData.getSuccessCount());
        }
        if (updateData.getFailedCount() != null) {
            existing.setFailedCount(updateData.getFailedCount());
        }
        if (updateData.getStatus() != null) {
            existing.setStatus(updateData.getStatus());
        }
        if (isAdmin(currentUser) && updateData.getAdsOwner() != null) {
            existing.setAdsOwner(updateData.getAdsOwner());
        }

        validateAndNormalize(existing, currentUser);
        existing.setUpdateDate(LocalDateTime.now());
        return affiliateAdsSyncTaskRepository.save(existing);
    }

    public void delete(Long id, Long currentUserId) {
        AffiliateAdsSyncTask existing = affiliateAdsSyncTaskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AFFILIATE_ADS_SYNC_TASK not found: " + id));
        ensureWritable(existing, currentUserId);
        affiliateAdsSyncTaskRepository.delete(existing);
    }

    public AffiliateAdsSyncTask markInProgress(Long id, Long currentUserId) {
        AffiliateAdsSyncTask existing = affiliateAdsSyncTaskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AFFILIATE_ADS_SYNC_TASK not found: " + id));
        ensureWritable(existing, currentUserId);
        if ("IN_PROGRESS".equalsIgnoreCase(existing.getStatus())) {
            throw new IllegalArgumentException("AFFILIATE_ADS_SYNC_TASK is already IN_PROGRESS");
        }
        existing.setStatus("IN_PROGRESS");
        existing.setTotalCount(0L);
        existing.setSuccessCount(0L);
        existing.setFailedCount(0L);
        existing.setPreStartDate(LocalDateTime.now());
        existing.setPreEndDate(null);
        existing.setPreDuration(null);
        existing.setUpdateDate(LocalDateTime.now());
        return affiliateAdsSyncTaskRepository.save(existing);
    }

    private void validateAndNormalize(AffiliateAdsSyncTask task, User currentUser) {
        task.setRegion(trimToNull(task.getRegion()));
        task.setSyncType(normalizeEnumLike(task.getSyncType(), "MANUAL"));
        task.setCron(trimToNull(task.getCron()));
        task.setStatus(normalizeEnumLike(task.getStatus(), "WAITING"));
        if (task.getTotalCount() == null) {
            task.setTotalCount(0L);
        }
        if (task.getSuccessCount() == null) {
            task.setSuccessCount(0L);
        }
        if (task.getFailedCount() == null) {
            task.setFailedCount(0L);
        }

        if (task.getAffiliateAdsSyncConfigId() == null) {
            throw new IllegalArgumentException("affiliateAdsSyncConfigId is required");
        }
        AffiliateAdsSyncConfig config = affiliateAdsSyncConfigRepository.findById(task.getAffiliateAdsSyncConfigId())
                .orElseThrow(() -> new IllegalArgumentException("AFFILIATE_ADS_SYNC_CONFIG not found: " + task.getAffiliateAdsSyncConfigId()));

        if (!StringUtils.hasText(task.getAdsOwner())) {
            throw new IllegalArgumentException("adsOwner is required");
        }
        User owner = userRepository.findByUserPhoneNumber(task.getAdsOwner().trim())
                .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found by phone number: " + task.getAdsOwner()));
        task.setAdsOwner(owner.getUserPhoneNumber());

        if (!isAdmin(currentUser) && !currentUser.getUserPhoneNumber().equals(task.getAdsOwner())) {
            throw new IllegalArgumentException("Unauthorized: adsOwner must match current user");
        }
        if (!config.getAdsOwner().equals(task.getAdsOwner())) {
            throw new IllegalArgumentException("adsOwner must match AFFILIATE_ADS_SYNC_CONFIG owner");
        }

        if (!StringUtils.hasText(task.getRegion())) {
            throw new IllegalArgumentException("region is required");
        }

        validateAllowed(task.getSyncType(), "syncType", "SCHEDULER", "MANUAL");
        validateAllowed(task.getStatus(), "status", "WAITING", "IN_PROGRESS", "COMPLETED", "FAILED");

        if ("SCHEDULER".equals(task.getSyncType()) && !StringUtils.hasText(task.getCron())) {
            throw new IllegalArgumentException("cron is required when syncType is SCHEDULER");
        }

        if (task.getTotalCount() < 0 || task.getSuccessCount() < 0 || task.getFailedCount() < 0) {
            throw new IllegalArgumentException("count fields must be non-negative");
        }

        validateLength(task.getRegion(), "region", 64);
        validateLength(task.getSyncType(), "syncType", 64);
        validateLength(task.getCron(), "cron", 128);
        validateLength(task.getStatus(), "status", 64);
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

    private void ensureReadable(AffiliateAdsSyncTask task, Long currentUserId) {
        ensureAccess(task, currentUserId, "read");
    }

    private void ensureWritable(AffiliateAdsSyncTask task, Long currentUserId) {
        ensureAccess(task, currentUserId, "modify");
    }

    private void ensureAccess(AffiliateAdsSyncTask task, Long currentUserId, String action) {
        User currentUser = getCurrentUser(currentUserId);
        if (!isAdmin(currentUser) && !currentUser.getUserPhoneNumber().equals(task.getAdsOwner())) {
            throw new IllegalArgumentException("Unauthorized: you can only " + action + " your own sync tasks");
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
