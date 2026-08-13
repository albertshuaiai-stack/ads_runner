package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AffiliateTest;
import com.admire.cars.runner.entity.AdsNormalInfo;
import com.admire.cars.runner.entity.IpProxyInfo;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.AffiliateTestRepository;
import com.admire.cars.runner.repository.AdsNormalInfoRepository;
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
public class AffiliateTestService {

    private final AffiliateTestRepository affiliateTestRepository;
    private final UserRepository userRepository;
    private final AdsNormalInfoRepository adsNormalInfoRepository;
    private final IpProxyInfoRepository ipProxyInfoRepository;

    public AffiliateTestService(
            AffiliateTestRepository affiliateTestRepository,
            UserRepository userRepository,
            AdsNormalInfoRepository adsNormalInfoRepository,
            IpProxyInfoRepository ipProxyInfoRepository) {
        this.affiliateTestRepository = affiliateTestRepository;
        this.userRepository = userRepository;
        this.adsNormalInfoRepository = adsNormalInfoRepository;
        this.ipProxyInfoRepository = ipProxyInfoRepository;
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

    /**
     * Convert successful affiliate test to normal ads task
     * Requirements:
     * 1. Query AffiliateTest via ID
     * 2. Check Status is Success
     * 3. Query Enabled IP Proxy info via country/region and status
     * 4. Create AdsNormalInfo from AffiliateTest
     * 5. Delete AffiliateTest record after successful conversion
     *
     * @param testAdId the affiliate test ID to convert
     * @param currentUserId the current user ID
     * @return created AdsNormalInfo
     */
    public AdsNormalInfo convertTestToNormal(Long testAdId, Long currentUserId) {
        // 1. Query AffiliateTest via ID
        AffiliateTest affiliateTest = affiliateTestRepository.findById(testAdId)
                .orElseThrow(() -> new IllegalArgumentException("AFFILIATE_TEST not found: " + testAdId));
        ensureReadable(affiliateTest, currentUserId);

        // 2. Check Status is Success
        if (!StringUtils.hasText(affiliateTest.getStatus()) || !"Success".equalsIgnoreCase(affiliateTest.getStatus().trim())) {
            throw new IllegalArgumentException("AFFILIATE_TEST status must be 'Success', current status: " + affiliateTest.getStatus());
        }

        // 3. Query Enabled IP Proxy info via country (region) and status
        String region = affiliateTest.getRegion();
        if (!StringUtils.hasText(region)) {
            throw new IllegalArgumentException("AFFILIATE_TEST region is required for finding IP proxy");
        }

        List<IpProxyInfo> enabledProxies = ipProxyInfoRepository.findByTargetCountryIgnoreCaseAndStatusIgnoreCase(region, "ENABLED");
        String dynamicProxyInfo = null;
        if (!enabledProxies.isEmpty()) {
            dynamicProxyInfo = enabledProxies.get(0).getProxyInfo();
        }

        // 4. Create AdsNormalInfo from AffiliateTest
        AdsNormalInfo adsNormalInfo = new AdsNormalInfo();
        adsNormalInfo.setCampainName(affiliateTest.getSiteName());
        adsNormalInfo.setCampainCountry(affiliateTest.getRegion());
        adsNormalInfo.setPlatformName(affiliateTest.getAffiliateNetwork());
        adsNormalInfo.setAffiliteUrl(affiliateTest.getTrackingUrl());
        adsNormalInfo.setLandingPageUrl(affiliateTest.getFinalUrl());
        adsNormalInfo.setDynamicProxyInfo(dynamicProxyInfo);
        adsNormalInfo.setStatus("PAUSED");
        adsNormalInfo.setAdsOwner(affiliateTest.getAdsOwner());
        adsNormalInfo.setSuccessCount(0L);
        adsNormalInfo.setFailedCount(0L);
        adsNormalInfo.setCreateDate(LocalDateTime.now());

        AdsNormalInfo savedInfo = adsNormalInfoRepository.save(adsNormalInfo);

        // 5. Delete AffiliateTest record after successful conversion
        affiliateTestRepository.deleteById(testAdId);

        return savedInfo;
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
