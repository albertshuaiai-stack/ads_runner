package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.TrackClick;
import com.admire.cars.runner.entity.TrackConversion;
import com.admire.cars.runner.entity.TrackHealthCheck;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.TrackClickRepository;
import com.admire.cars.runner.repository.TrackConversionRepository;
import com.admire.cars.runner.repository.TrackHealthCheckRepository;
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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

// 点击与转化的查询, 按用户隔离 / click and conversion queries, scoped per user
@Service
@Transactional(readOnly = true)
public class TrackQueryService {

    private final TrackClickRepository clickRepository;
    private final TrackConversionRepository conversionRepository;
    private final TrackHealthCheckRepository healthCheckRepository;
    private final UserRepository userRepository;

    public TrackQueryService(
            TrackClickRepository clickRepository,
            TrackConversionRepository conversionRepository,
            TrackHealthCheckRepository healthCheckRepository,
            UserRepository userRepository) {
        this.clickRepository = clickRepository;
        this.conversionRepository = conversionRepository;
        this.healthCheckRepository = healthCheckRepository;
        this.userRepository = userRepository;
    }

    public Page<TrackClick> searchClicks(String slug,
                                         String clickId,
                                         String campaignName,
                                         LocalDate startDate,
                                         LocalDate endDate,
                                         String adsOwner,
                                         Long currentUserId,
                                         Pageable pageable) {
        User currentUser = getCurrentUser(currentUserId);
        boolean admin = isAdmin(currentUser);
        LocalDateTime start = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime end = endDate == null ? null : endDate.atTime(LocalTime.MAX);

        Specification<TrackClick> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(ownerPredicate(admin, adsOwner, currentUser, root.get("adsOwner"), criteriaBuilder));

            if (StringUtils.hasText(slug)) {
                predicates.add(criteriaBuilder.equal(root.get("slug"), slug.trim()));
            }
            if (StringUtils.hasText(clickId)) {
                predicates.add(criteriaBuilder.equal(root.get("clickId"), clickId.trim()));
            }
            if (StringUtils.hasText(campaignName)) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("campaignName")),
                        "%" + campaignName.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (start != null && end != null) {
                predicates.add(criteriaBuilder.between(root.get("clickTime"), start, end));
            } else if (start != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("clickTime"), start));
            } else if (end != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("clickTime"), end));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        return clickRepository.findAll(specification, pageable);
    }

    public Page<TrackConversion> searchConversions(String orderNo,
                                                   String clickId,
                                                   String attributed,
                                                   LocalDate startDate,
                                                   LocalDate endDate,
                                                   String adsOwner,
                                                   Long currentUserId,
                                                   Pageable pageable) {
        User currentUser = getCurrentUser(currentUserId);
        boolean admin = isAdmin(currentUser);
        LocalDateTime start = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime end = endDate == null ? null : endDate.atTime(LocalTime.MAX);

        Specification<TrackConversion> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(ownerPredicate(admin, adsOwner, currentUser, root.get("adsOwner"), criteriaBuilder));

            if (StringUtils.hasText(orderNo)) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("orderNo")),
                        "%" + orderNo.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (StringUtils.hasText(clickId)) {
                predicates.add(criteriaBuilder.equal(root.get("clickId"), clickId.trim()));
            }
            if (StringUtils.hasText(attributed)) {
                predicates.add(criteriaBuilder.equal(root.get("attributed"), attributed.trim()));
            }
            if (start != null && end != null) {
                predicates.add(criteriaBuilder.between(root.get("createDate"), start, end));
            } else if (start != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createDate"), start));
            } else if (end != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createDate"), end));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        return conversionRepository.findAll(specification, pageable);
    }

    public Page<TrackHealthCheck> searchHealthChecks(String slug,
                                                     String adsOwner,
                                                     Long currentUserId,
                                                     Pageable pageable) {
        User currentUser = getCurrentUser(currentUserId);
        boolean admin = isAdmin(currentUser);

        Specification<TrackHealthCheck> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(ownerPredicate(admin, adsOwner, currentUser, root.get("adsOwner"), criteriaBuilder));

            if (StringUtils.hasText(slug)) {
                predicates.add(criteriaBuilder.equal(root.get("slug"), slug.trim()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        return healthCheckRepository.findAll(specification, pageable);
    }

    private Predicate ownerPredicate(boolean admin,
                                     String adsOwner,
                                     User currentUser,
                                     jakarta.persistence.criteria.Path<Object> ownerPath,
                                     jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder) {
        if (admin) {
            if (StringUtils.hasText(adsOwner)) {
                return criteriaBuilder.equal(ownerPath, adsOwner.trim());
            }
            return criteriaBuilder.conjunction();
        }
        return criteriaBuilder.equal(ownerPath, currentUser.getUserPhoneNumber());
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
