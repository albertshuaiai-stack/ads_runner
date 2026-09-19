package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.ToolBrandsReview;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.ToolBrandsReviewRepository;
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
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ToolBrandsReviewService {

    private final ToolBrandsReviewRepository repository;
    private final UserRepository userRepository;

    public ToolBrandsReviewService(ToolBrandsReviewRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    public ToolBrandsReview create(ToolBrandsReview review, Long currentUserId) {
        if (review == null) {
            throw new IllegalArgumentException("TOOL_BRANDS_REVIEW is required");
        }
        User currentUser = getCurrentUser(currentUserId);
        review.setAdsOwner(currentUser.getUserPhoneNumber());
        validateAndNormalize(review);
        review.setCreateDate(LocalDateTime.now());
        return repository.save(review);
    }

    @Transactional(readOnly = true)
    public ToolBrandsReview getById(Long id, Long currentUserId) {
        ToolBrandsReview e = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("TOOL_BRANDS_REVIEW not found: " + id));
        ensureReadable(e, currentUserId);
        return e;
    }

    @Transactional(readOnly = true)
    public Page<ToolBrandsReview> search(String brand, Long score, String adsOwner, Long currentUserId, Pageable pageable) {
        String normalizedBrand = trimToNull(brand);
        User currentUser = currentUserId == null ? null : getCurrentUser(currentUserId);
        boolean admin = currentUser != null && isAdmin(currentUser);
        String scopedAdsOwner;
        if (currentUser != null) {
            if (admin) {
                scopedAdsOwner = trimToNull(adsOwner);
            } else {
                scopedAdsOwner = currentUser.getUserPhoneNumber();
            }
        } else {
            scopedAdsOwner = trimToNull(adsOwner);
        }

        Specification<ToolBrandsReview> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(normalizedBrand)) {
                predicates.add(cb.like(cb.lower(root.get("brand")), "%" + normalizedBrand.toLowerCase() + "%"));
            }
            if (score != null) {
                predicates.add(cb.equal(root.get("score"), score));
            }

            if (StringUtils.hasText(scopedAdsOwner)) {
                predicates.add(cb.equal(cb.lower(root.get("adsOwner")), scopedAdsOwner.toLowerCase()));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
        return repository.findAll(specification, pageable);
    }

    public ToolBrandsReview update(Long id, ToolBrandsReview updateData, Long currentUserId) {
        if (updateData == null) {
            throw new IllegalArgumentException("updateData is required");
        }
        ToolBrandsReview existing = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("TOOL_BRANDS_REVIEW not found: " + id));
        ensureWritable(existing, currentUserId);

        if (updateData.getBrand() != null) existing.setBrand(updateData.getBrand());
        if (updateData.getScore() != null) existing.setScore(updateData.getScore());
        if (updateData.getRemarks() != null) existing.setRemarks(updateData.getRemarks());

        validateAndNormalize(existing);
        existing.setUpdateDate(LocalDateTime.now());
        return repository.save(existing);
    }

    public void delete(Long id, Long currentUserId) {
        ToolBrandsReview existing = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("TOOL_BRANDS_REVIEW not found: " + id));
        ensureWritable(existing, currentUserId);
        repository.delete(existing);
    }

    private void validateAndNormalize(ToolBrandsReview review) {
        if (!StringUtils.hasText(review.getAdsOwner())) {
            throw new IllegalArgumentException("adsOwner is required");
        }
        User owner = userRepository.findByUserPhoneNumber(review.getAdsOwner().trim())
                .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found by phone number: " + review.getAdsOwner()));
        review.setAdsOwner(owner.getUserPhoneNumber());

        review.setBrand(trimToNull(review.getBrand()));
        review.setRemarks(trimToNull(review.getRemarks()));

        if (review.getScore() == null) {
            throw new IllegalArgumentException("score is required");
        }
        if (review.getScore() < 1 || review.getScore() > 5) {
            throw new IllegalArgumentException("score must be between 1 and 5");
        }

        validateLength(review.getBrand(), "brand", 128);
        validateLength(review.getRemarks(), "remarks", 1024);
    }

    private void validateLength(String value, String fieldName, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must be at most " + maxLength + " characters");
        }
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private void ensureReadable(ToolBrandsReview review, Long currentUserId) {
        ensureAccess(review, currentUserId, "read");
    }

    private void ensureWritable(ToolBrandsReview review, Long currentUserId) {
        ensureAccess(review, currentUserId, "modify");
    }

    private void ensureAccess(ToolBrandsReview review, Long currentUserId, String action) {
        User currentUser = getCurrentUser(currentUserId);
        if (!isAdmin(currentUser) && !currentUser.getUserPhoneNumber().equals(review.getAdsOwner())) {
            throw new IllegalArgumentException("Unauthorized: you can only " + action + " your own brand reviews");
        }
    }

    private User getCurrentUser(Long currentUserId) {
        return userRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found: " + currentUserId));
    }

    public java.util.List<String> getAllBrands(Long currentUserId) {
        User currentUser = getCurrentUser(currentUserId);
        if (isAdmin(currentUser)) {
            return repository.findDistinctBrandsOrderByBrandAsc();
        } else {
            return repository.findDistinctBrandsByAdsOwnerOrderByBrandAsc(currentUser.getUserPhoneNumber());
        }
    }

    private boolean isAdmin(User user) {
        return user.getUserRole() != null && user.getUserRole().toLowerCase().contains("admin");
    }
}
