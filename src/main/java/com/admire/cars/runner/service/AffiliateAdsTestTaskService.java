package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AffiliateAdsSyncConfig;
import com.admire.cars.runner.entity.AffiliateAdsTestTask;
import com.admire.cars.runner.entity.IpProxyInfo;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.AffiliateAdsSyncConfigRepository;
import com.admire.cars.runner.repository.AffiliateAdsTestTaskRepository;
import com.admire.cars.runner.repository.IpProxyInfoRepository;
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
public class AffiliateAdsTestTaskService {

    private final AffiliateAdsTestTaskRepository affiliateAdsTestTaskRepository;
    private final AffiliateAdsSyncConfigRepository affiliateAdsSyncConfigRepository;
    private final IpProxyInfoRepository ipProxyInfoRepository;
    private final UserRepository userRepository;

    public AffiliateAdsTestTaskService(
            AffiliateAdsTestTaskRepository affiliateAdsTestTaskRepository,
            AffiliateAdsSyncConfigRepository affiliateAdsSyncConfigRepository,
            IpProxyInfoRepository ipProxyInfoRepository,
            UserRepository userRepository) {
        this.affiliateAdsTestTaskRepository = affiliateAdsTestTaskRepository;
        this.affiliateAdsSyncConfigRepository = affiliateAdsSyncConfigRepository;
        this.ipProxyInfoRepository = ipProxyInfoRepository;
        this.userRepository = userRepository;
    }

    public AffiliateAdsTestTask create(AffiliateAdsTestTask task, Long currentUserId) {
        if (task == null) {
            throw new IllegalArgumentException("AFFILIATE_ADS_TEST_TASK is required");
        }
        User currentUser = getCurrentUser(currentUserId);
        if (!isAdmin(currentUser) || !StringUtils.hasText(task.getAdsOwner())) {
            task.setAdsOwner(currentUser.getUserPhoneNumber());
        }
        validateAndNormalize(task, currentUser);
        task.setCreateDate(LocalDateTime.now());
        return affiliateAdsTestTaskRepository.save(task);
    }

    @Transactional(readOnly = true)
    public AffiliateAdsTestTask getById(Long id, Long currentUserId) {
        AffiliateAdsTestTask task = affiliateAdsTestTaskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AFFILIATE_ADS_TEST_TASK not found: " + id));
        ensureReadable(task, currentUserId);
        return task;
    }

    @Transactional(readOnly = true)
    public Page<AffiliateAdsTestTask> search(
            String adsOwner,
            Long affiliateAdsSyncConfigId,
            Long currentUserId,
            Pageable pageable) {
        User currentUser = getCurrentUser(currentUserId);
        boolean admin = isAdmin(currentUser);
        String normalizedOwnerFilter = admin ? trimToNull(adsOwner) : currentUser.getUserPhoneNumber();

        Specification<AffiliateAdsTestTask> specification = (root, query, criteriaBuilder) -> {
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

        return affiliateAdsTestTaskRepository.findAll(specification, pageable);
    }

    public AffiliateAdsTestTask update(Long id, AffiliateAdsTestTask updateData, Long currentUserId) {
        if (updateData == null) {
            throw new IllegalArgumentException("updateData is required");
        }
        User currentUser = getCurrentUser(currentUserId);

        AffiliateAdsTestTask existing = affiliateAdsTestTaskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AFFILIATE_ADS_TEST_TASK not found: " + id));
        ensureWritable(existing, currentUserId);

        if (updateData.getAffiliateAdsSyncConfigId() != null) {
            existing.setAffiliateAdsSyncConfigId(updateData.getAffiliateAdsSyncConfigId());
        }
        if (updateData.getRegion() != null) {
            existing.setRegion(updateData.getRegion());
        }
        if (updateData.getIpProxyInfoId() != null) {
            existing.setIpProxyInfoId(updateData.getIpProxyInfoId());
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
        if (updateData.getPreStartDate() != null) {
            existing.setPreStartDate(updateData.getPreStartDate());
        }
        if (updateData.getPreEndDate() != null) {
            existing.setPreEndDate(updateData.getPreEndDate());
        }
        if (updateData.getPreDuration() != null) {
            existing.setPreDuration(updateData.getPreDuration());
        }
        if (updateData.getStatus() != null) {
            existing.setStatus(updateData.getStatus());
        }
        if (isAdmin(currentUser) && updateData.getAdsOwner() != null) {
            existing.setAdsOwner(updateData.getAdsOwner());
        }

        validateAndNormalize(existing, currentUser);
        existing.setUpdateDate(LocalDateTime.now());
        return affiliateAdsTestTaskRepository.save(existing);
    }

    public void delete(Long id, Long currentUserId) {
        AffiliateAdsTestTask existing = affiliateAdsTestTaskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AFFILIATE_ADS_TEST_TASK not found: " + id));
        ensureWritable(existing, currentUserId);
        affiliateAdsTestTaskRepository.delete(existing);
    }

    public AffiliateAdsTestTask markInProgress(Long id, Long currentUserId) {
        AffiliateAdsTestTask existing = affiliateAdsTestTaskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AFFILIATE_ADS_TEST_TASK not found: " + id));
        ensureWritable(existing, currentUserId);
        if ("IN_PROGRESS".equalsIgnoreCase(existing.getStatus())) {
            throw new IllegalArgumentException("AFFILIATE_ADS_TEST_TASK is already IN_PROGRESS");
        }
        existing.setStatus("IN_PROGRESS");
        existing.setTotalCount(0L);
        existing.setSuccessCount(0L);
        existing.setFailedCount(0L);
        existing.setPreStartDate(LocalDateTime.now());
        existing.setPreEndDate(null);
        existing.setPreDuration(null);
        existing.setUpdateDate(LocalDateTime.now());
        return affiliateAdsTestTaskRepository.save(existing);
    }

    private void validateAndNormalize(AffiliateAdsTestTask task, User currentUser) {
        task.setRegion(trimToNull(task.getRegion()));
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
        if (task.getPreDuration() != null && task.getPreDuration() < 0) {
            throw new IllegalArgumentException("preDuration must be non-negative");
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

        if (task.getIpProxyInfoId() != null) {
            IpProxyInfo proxyInfo = ipProxyInfoRepository.findById(task.getIpProxyInfoId())
                    .orElseThrow(() -> new IllegalArgumentException("IP_PROXY_INFO not found: " + task.getIpProxyInfoId()));
            if (!task.getAdsOwner().equals(proxyInfo.getAdsOwner())) {
                throw new IllegalArgumentException("ipProxyInfoId must belong to the same adsOwner");
            }
        }

        validateAllowed(task.getStatus(), "status", "WAITING", "IN_PROGRESS", "COMPLETED", "FAILED");
        validateLength(task.getRegion(), "region", 64);
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

    private void ensureReadable(AffiliateAdsTestTask task, Long currentUserId) {
        ensureAccess(task, currentUserId, "read");
    }

    private void ensureWritable(AffiliateAdsTestTask task, Long currentUserId) {
        ensureAccess(task, currentUserId, "modify");
    }

    private void ensureAccess(AffiliateAdsTestTask task, Long currentUserId, String action) {
        User currentUser = getCurrentUser(currentUserId);
        if (!isAdmin(currentUser) && !currentUser.getUserPhoneNumber().equals(task.getAdsOwner())) {
            throw new IllegalArgumentException("Unauthorized: you can only " + action + " your own test tasks");
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
