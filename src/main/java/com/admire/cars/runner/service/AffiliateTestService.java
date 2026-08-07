package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AffiliateTest;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.AffiliateTestRepository;
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
import java.util.Locale;

@Service
@Transactional
public class AffiliateTestService {

    private final AffiliateTestRepository affiliateTestRepository;
    private final UserRepository userRepository;

    public AffiliateTestService(
            AffiliateTestRepository affiliateTestRepository,
            UserRepository userRepository) {
        this.affiliateTestRepository = affiliateTestRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public AffiliateTest getById(Long id, Long currentUserId) {
        AffiliateTest result = affiliateTestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AFFILIATE_TEST not found: " + id));
        ensureReadable(result, currentUserId);
        return result;
    }

    @Transactional(readOnly = true)
    public Page<AffiliateTest> search(
            String adsOwner,
            String affiliateNetwork,
            String region,
            String status,
            Long currentUserId,
            Pageable pageable) {
        User currentUser = getCurrentUser(currentUserId);
        boolean admin = isAdmin(currentUser);
        String normalizedOwnerFilter = admin ? trimToNull(adsOwner) : currentUser.getUserPhoneNumber();

        Specification<AffiliateTest> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(normalizedOwnerFilter)) {
                predicates.add(criteriaBuilder.equal(root.get("adsOwner"), normalizedOwnerFilter));
            }
            if (StringUtils.hasText(affiliateNetwork)) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("affiliateNetwork")),
                        affiliateNetwork.trim().toLowerCase(Locale.ROOT)));
            }
            if (StringUtils.hasText(region)) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("region")),
                        region.trim().toLowerCase(Locale.ROOT)));
            }
            if (StringUtils.hasText(status)) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("status")),
                        status.trim().toLowerCase(Locale.ROOT)));
            }

            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        return affiliateTestRepository.findAll(specification, pageable);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void ensureReadable(AffiliateTest result, Long currentUserId) {
        ensureAccess(result, currentUserId, "read");
    }

    private void ensureAccess(AffiliateTest result, Long currentUserId, String action) {
        User currentUser = getCurrentUser(currentUserId);
        if (!isAdmin(currentUser) && !currentUser.getUserPhoneNumber().equals(result.getAdsOwner())) {
            throw new IllegalArgumentException("Unauthorized: you can only " + action + " your own test records");
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
