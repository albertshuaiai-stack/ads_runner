package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.RanSiteIdPool;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.RanSiteIdPoolRepository;
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
public class TrackPoolService {

    private final RanSiteIdPoolRepository poolRepository;
    private final UserRepository userRepository;

    public TrackPoolService(RanSiteIdPoolRepository poolRepository, UserRepository userRepository) {
        this.poolRepository = poolRepository;
        this.userRepository = userRepository;
    }

    public RanSiteIdPool create(RanSiteIdPool pool, Long currentUserId) {
        if (pool == null) {
            throw new IllegalArgumentException("pool entry is required");
        }
        User currentUser = getCurrentUser(currentUserId);
        pool.setAdsOwner(currentUser.getUserPhoneNumber());
        validateAndNormalize(pool);

        poolRepository.findByPoolCodeAndSiteId(pool.getPoolCode(), pool.getSiteId()).ifPresent(existing -> {
            throw new IllegalArgumentException("siteId already exists in pool: " + pool.getSiteId());
        });

        pool.setId(null);
        pool.setCreateDate(LocalDateTime.now());
        return poolRepository.save(pool);
    }

    @Transactional(readOnly = true)
    public RanSiteIdPool getById(Long id, Long currentUserId) {
        RanSiteIdPool pool = poolRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("RAN_SITE_ID_POOL not found: " + id));
        ensureReadable(pool, currentUserId);
        return pool;
    }

    @Transactional(readOnly = true)
    public Page<RanSiteIdPool> search(String poolCode,
                                      String siteId,
                                      String status,
                                      String adsOwner,
                                      Long currentUserId,
                                      Pageable pageable) {
        User currentUser = getCurrentUser(currentUserId);
        boolean admin = isAdmin(currentUser);

        Specification<RanSiteIdPool> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (admin) {
                if (StringUtils.hasText(adsOwner)) {
                    predicates.add(criteriaBuilder.equal(root.get("adsOwner"), adsOwner.trim()));
                }
            } else {
                predicates.add(criteriaBuilder.equal(root.get("adsOwner"), currentUser.getUserPhoneNumber()));
            }

            if (StringUtils.hasText(poolCode)) {
                predicates.add(criteriaBuilder.equal(root.get("poolCode"), poolCode.trim()));
            }
            if (StringUtils.hasText(siteId)) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("siteId")),
                        "%" + siteId.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (StringUtils.hasText(status)) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status.trim()));
            }

            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        return poolRepository.findAll(specification, pageable);
    }

    public RanSiteIdPool update(Long id, RanSiteIdPool updateData, Long currentUserId) {
        if (updateData == null) {
            throw new IllegalArgumentException("updateData is required");
        }
        RanSiteIdPool existing = poolRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("RAN_SITE_ID_POOL not found: " + id));
        ensureWritable(existing, currentUserId);

        if (updateData.getPlatform() != null) {
            existing.setPlatform(updateData.getPlatform());
        }
        if (updateData.getAdvertiser() != null) {
            existing.setAdvertiser(updateData.getAdvertiser());
        }
        if (updateData.getWeight() != null) {
            existing.setWeight(updateData.getWeight());
        }
        if (updateData.getDailyCap() != null) {
            existing.setDailyCap(updateData.getDailyCap());
        }
        if (updateData.getStatus() != null) {
            existing.setStatus(updateData.getStatus());
        }
        if (updateData.getRemarks() != null) {
            existing.setRemarks(updateData.getRemarks());
        }

        validateAndNormalize(existing);
        existing.setUpdateDate(LocalDateTime.now());
        return poolRepository.save(existing);
    }

    public void delete(Long id, Long currentUserId) {
        RanSiteIdPool existing = poolRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("RAN_SITE_ID_POOL not found: " + id));
        ensureWritable(existing, currentUserId);
        poolRepository.delete(existing);
    }

    // 重置当日用量, 配额按天控制 / reset daily usage, quota is per day
    public int resetDailyUsage(String poolCode, Long currentUserId) {
        User currentUser = getCurrentUser(currentUserId);
        List<RanSiteIdPool> items;
        if (StringUtils.hasText(poolCode)) {
            items = poolRepository.findByPoolCode(poolCode.trim());
        } else {
            items = poolRepository.findByAdsOwner(currentUser.getUserPhoneNumber());
        }
        int count = 0;
        LocalDate today = LocalDate.now();
        for (RanSiteIdPool item : items) {
            if (item.getLastUsedAt() != null && item.getLastUsedAt().toLocalDate().isAfter(today)) {
                continue;
            }
            item.setUsedToday(0L);
            item.setUpdateDate(LocalDateTime.now());
            poolRepository.save(item);
            count++;
        }
        return count;
    }

    private void validateAndNormalize(RanSiteIdPool pool) {
        if (!StringUtils.hasText(pool.getAdsOwner())) {
            throw new IllegalArgumentException("adsOwner is required");
        }
        userRepository.findByUserPhoneNumber(pool.getAdsOwner().trim())
                .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found by phone number: " + pool.getAdsOwner()));

        if (!StringUtils.hasText(pool.getPoolCode())) {
            throw new IllegalArgumentException("poolCode is required");
        }
        pool.setPoolCode(pool.getPoolCode().trim());

        if (!StringUtils.hasText(pool.getSiteId())) {
            throw new IllegalArgumentException("siteId is required");
        }
        pool.setSiteId(pool.getSiteId().trim());

        if (pool.getWeight() == null || pool.getWeight() <= 0) {
            pool.setWeight(1);
        }
        if (pool.getUsedToday() == null) {
            pool.setUsedToday(0L);
        }
        if (pool.getTotalUsed() == null) {
            pool.setTotalUsed(0L);
        }
        if (!StringUtils.hasText(pool.getStatus())) {
            pool.setStatus(RanSiteIdPool.STATUS_ACTIVE);
        }
        if (!RanSiteIdPool.STATUS_ACTIVE.equals(pool.getStatus())
                && !RanSiteIdPool.STATUS_PAUSED.equals(pool.getStatus())
                && !RanSiteIdPool.STATUS_BANNED.equals(pool.getStatus())) {
            throw new IllegalArgumentException("status must be ACTIVE, PAUSED or BANNED");
        }

        validateLength(pool.getPlatform(), "platform", 64);
        validateLength(pool.getAdvertiser(), "advertiser", 128);
        validateLength(pool.getRemarks(), "remarks", 128);
    }

    private void ensureReadable(RanSiteIdPool pool, Long currentUserId) {
        ensureAccess(pool, currentUserId, "read");
    }

    private void ensureWritable(RanSiteIdPool pool, Long currentUserId) {
        ensureAccess(pool, currentUserId, "modify");
    }

    private void ensureAccess(RanSiteIdPool pool, Long currentUserId, String action) {
        User currentUser = getCurrentUser(currentUserId);
        if (!isAdmin(currentUser) && !currentUser.getUserPhoneNumber().equals(pool.getAdsOwner())) {
            throw new IllegalArgumentException("Unauthorized: you can only " + action + " your own pool entries");
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

    private void validateLength(String value, String fieldName, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must be at most " + maxLength + " characters");
        }
    }
}
