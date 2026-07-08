package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsMatrixAffiliateInfo;
import com.admire.cars.runner.entity.AdsMatrixInfo;
import com.admire.cars.runner.entity.AdsPlatform;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.AdsMatrixInfoRepository;
import com.admire.cars.runner.repository.AdsPlatformRepository;
import com.admire.cars.runner.repository.UserRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
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
public class AdsMatrixInfoService {

    private final AdsMatrixInfoRepository adsMatrixInfoRepository;
    private final AdsPlatformRepository adsPlatformRepository;
    private final UserRepository userRepository;

    public AdsMatrixInfoService(
            AdsMatrixInfoRepository adsMatrixInfoRepository,
            AdsPlatformRepository adsPlatformRepository,
            UserRepository userRepository) {
        this.adsMatrixInfoRepository = adsMatrixInfoRepository;
        this.adsPlatformRepository = adsPlatformRepository;
        this.userRepository = userRepository;
    }

    public AdsMatrixInfo create(AdsMatrixInfo adsMatrixInfo) {
        validateAndNormalize(adsMatrixInfo);
        adsMatrixInfo.setCreateDate(LocalDateTime.now());
        attachChildren(adsMatrixInfo);
        return adsMatrixInfoRepository.save(adsMatrixInfo);
    }

    @Transactional(readOnly = true)
    public AdsMatrixInfo getById(Long id) {
        return adsMatrixInfoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ADS_MATRIX_INFO not found: " + id));
    }

    public Page<AdsMatrixInfo> search(String campainName, String platformName, String status, String adsOwner, Pageable pageable) {
        Specification<AdsMatrixInfo> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(campainName)) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("campainName")),
                        "%" + campainName.toLowerCase() + "%"));
            }
            if (StringUtils.hasText(status)) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("status")),
                        status.toLowerCase()));
            }
            if (StringUtils.hasText(adsOwner)) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("adsOwner")),
                        "%" + adsOwner.toLowerCase() + "%"));
            }
            if (StringUtils.hasText(platformName)) {
                query.distinct(true);
                Join<AdsMatrixInfo, AdsMatrixAffiliateInfo> affiliateJoin = root.join("affiliateInfos", JoinType.LEFT);
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(affiliateJoin.get("platformName")),
                        "%" + platformName.toLowerCase() + "%"));
            }

            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        return adsMatrixInfoRepository.findAll(specification, pageable);
    }

    public AdsMatrixInfo update(Long id, AdsMatrixInfo updateData) {
        AdsMatrixInfo existing = getById(id);

        if (updateData.getCampainName() != null) {
            existing.setCampainName(updateData.getCampainName());
        }
        if (updateData.getCampainCountry() != null) {
            existing.setCampainCountry(updateData.getCampainCountry());
        }
        if (updateData.getLandingPageUrl() != null) {
            existing.setLandingPageUrl(updateData.getLandingPageUrl());
        }
        if (updateData.getDynamicProxyInfo() != null) {
            existing.setDynamicProxyInfo(updateData.getDynamicProxyInfo());
        }
        if (updateData.getDynamicProxyInfoBackup() != null) {
            existing.setDynamicProxyInfoBackup(updateData.getDynamicProxyInfoBackup());
        }
        if (updateData.getIntervalTime() != null) {
            existing.setIntervalTime(updateData.getIntervalTime());
        }
        if (updateData.getStatus() != null) {
            existing.setStatus(updateData.getStatus());
        }
        if (updateData.getAdsOwner() != null) {
            existing.setAdsOwner(updateData.getAdsOwner());
        }

        validateAndNormalize(existing);
        existing.setUpdateDate(LocalDateTime.now());
        if (updateData.getAffiliateInfos() != null) {
            validateAffiliateList(updateData.getAffiliateInfos());
            existing.getAffiliateInfos().clear();
            attachChildren(existing, updateData.getAffiliateInfos());
        }
        return adsMatrixInfoRepository.save(existing);
    }

    public void delete(Long id) {
        AdsMatrixInfo existing = getById(id);
        adsMatrixInfoRepository.delete(existing);
    }

    private void validateAndNormalize(AdsMatrixInfo adsMatrixInfo) {
        if (adsMatrixInfo == null) {
            throw new IllegalArgumentException("ADS_MATRIX_INFO is required");
        }
        if (!StringUtils.hasText(adsMatrixInfo.getCampainName())) {
            throw new IllegalArgumentException("campainName is required");
        }
        if (!StringUtils.hasText(adsMatrixInfo.getCampainCountry())) {
            throw new IllegalArgumentException("campainCountry is required");
        }
        if (!StringUtils.hasText(adsMatrixInfo.getAdsOwner())) {
            throw new IllegalArgumentException("adsOwner is required");
        }

        Optional<User> owner = userRepository.findByUserPhoneNumber(adsMatrixInfo.getAdsOwner().trim());
        if (owner.isEmpty()) {
            throw new IllegalArgumentException("ADS_USER not found by phone number: " + adsMatrixInfo.getAdsOwner());
        }

        adsMatrixInfo.setAdsOwner(owner.get().getUserPhoneNumber());
        adsMatrixInfo.setCampainName(adsMatrixInfo.getCampainName().trim());
        adsMatrixInfo.setCampainCountry(adsMatrixInfo.getCampainCountry().trim());
        adsMatrixInfo.setStatus(normalizeStatus(adsMatrixInfo.getStatus()));

        if (adsMatrixInfo.getCampainName().length() > 32) {
            throw new IllegalArgumentException("campainName must be at most 32 characters");
        }
        if (adsMatrixInfo.getCampainCountry().length() > 8) {
            throw new IllegalArgumentException("campainCountry must be at most 8 characters");
        }
        if (adsMatrixInfo.getLandingPageUrl() != null && adsMatrixInfo.getLandingPageUrl().length() > 128) {
            throw new IllegalArgumentException("landingPageUrl must be at most 128 characters");
        }
        if (adsMatrixInfo.getDynamicProxyInfo() != null && adsMatrixInfo.getDynamicProxyInfo().length() > 256) {
            throw new IllegalArgumentException("dynamicProxyInfo must be at most 256 characters");
        }
        if (adsMatrixInfo.getDynamicProxyInfoBackup() != null && adsMatrixInfo.getDynamicProxyInfoBackup().length() > 256) {
            throw new IllegalArgumentException("dynamicProxyInfoBackup must be at most 256 characters");
        }
        if (adsMatrixInfo.getStatus().length() > 16) {
            throw new IllegalArgumentException("status must be at most 16 characters");
        }

        validateAffiliateList(adsMatrixInfo.getAffiliateInfos());
        for (AdsMatrixAffiliateInfo affiliateInfo : adsMatrixInfo.getAffiliateInfos()) {
            adsPlatformRepository.findByPlatformNameIgnoreCase(affiliateInfo.getPlatformName().trim())
                    .orElseThrow(() -> new IllegalArgumentException("ADS_PLATFORM not found: " + affiliateInfo.getPlatformName()));
            affiliateInfo.setPlatformName(affiliateInfo.getPlatformName().trim());
            affiliateInfo.setAffiliteUrl(affiliateInfo.getAffiliteUrl() == null ? null : affiliateInfo.getAffiliteUrl().trim());
            affiliateInfo.setRemarks(affiliateInfo.getRemarks() == null ? null : affiliateInfo.getRemarks().trim());
            if (affiliateInfo.getPlatformName().length() > 32) {
                throw new IllegalArgumentException("affiliate platformName must be at most 32 characters");
            }
            if (affiliateInfo.getAffiliteUrl() != null && affiliateInfo.getAffiliteUrl().length() > 128) {
                throw new IllegalArgumentException("affiliteUrl must be at most 128 characters");
            }
            if (affiliateInfo.getRemarks() != null && affiliateInfo.getRemarks().length() > 64) {
                throw new IllegalArgumentException("remarks must be at most 64 characters");
            }
        }
    }

    private String normalizeStatus(String status) {
        String normalized = StringUtils.hasText(status) ? status.trim().toUpperCase() : "RUNNING";
        if (!"PAUSED".equals(normalized) && !"RUNNING".equals(normalized)) {
            throw new IllegalArgumentException("status must be PAUSED or RUNNING");
        }
        return normalized;
    }

    private void attachChildren(AdsMatrixInfo adsMatrixInfo) {
        attachChildren(adsMatrixInfo, adsMatrixInfo.getAffiliateInfos());
    }

    private void attachChildren(AdsMatrixInfo adsMatrixInfo, List<AdsMatrixAffiliateInfo> affiliates) {
        List<AdsMatrixAffiliateInfo> copy = affiliates == null ? null : new ArrayList<>(affiliates);
        adsMatrixInfo.getAffiliateInfos().clear();
        if (copy == null) {
            return;
        }
        for (AdsMatrixAffiliateInfo affiliateInfo : copy) {
            affiliateInfo.setId(null);
            affiliateInfo.setMatrixInfo(adsMatrixInfo);
            if (affiliateInfo.getDisplayNumber() == null) {
                affiliateInfo.setDisplayNumber(0L);
            }
            adsMatrixInfo.getAffiliateInfos().add(affiliateInfo);
        }
    }

    private void validateAffiliateList(List<AdsMatrixAffiliateInfo> affiliates) {
        if (affiliates == null) {
            return;
        }
        for (AdsMatrixAffiliateInfo affiliateInfo : affiliates) {
            validateAffiliate(affiliateInfo);
        }
    }

    private void validateAffiliate(AdsMatrixAffiliateInfo affiliateInfo) {
        if (affiliateInfo == null) {
            throw new IllegalArgumentException("affiliateInfos cannot contain null entries");
        }
        if (!StringUtils.hasText(affiliateInfo.getPlatformName())) {
            throw new IllegalArgumentException("affiliate platformName is required");
        }
    }
}
