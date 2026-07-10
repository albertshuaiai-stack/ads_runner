package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsNormalInfo;
import com.admire.cars.runner.entity.AdsPlatform;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.event.AdsAutoTaskAction;
import com.admire.cars.runner.event.AdsAutoTaskRegistrationEvent;
import com.admire.cars.runner.repository.AdsNormalInfoRepository;
import com.admire.cars.runner.repository.AdsPlatformRepository;
import com.admire.cars.runner.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class AdsNormalInfoService {

    private final AdsNormalInfoRepository adsNormalInfoRepository;
    private final AdsPlatformRepository adsPlatformRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AdsNormalInfoService(
            AdsNormalInfoRepository adsNormalInfoRepository,
            AdsPlatformRepository adsPlatformRepository,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher) {
        this.adsNormalInfoRepository = adsNormalInfoRepository;
        this.adsPlatformRepository = adsPlatformRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    public AdsNormalInfo create(AdsNormalInfo adsNormalInfo, Long currentUserId) {
        adsNormalInfo.setAdsOwner(resolveAdsOwner(currentUserId));
        validateAndNormalize(adsNormalInfo);
        adsNormalInfo.setCreateDate(LocalDateTime.now());
        AdsNormalInfo saved = adsNormalInfoRepository.save(adsNormalInfo);
        publishAutoTaskRegistration(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public AdsNormalInfo getById(Long id) {
        return adsNormalInfoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ADS_NORMAL_INFO not found: " + id));
    }

    public Page<AdsNormalInfo> search(String campainName, String platformName, String status, String adsOwner, Long currentUserId, Pageable pageable) {
        User currentUser = getCurrentUser(currentUserId);
        boolean admin = isAdmin(currentUser);
        String scopedAdsOwner = admin ? adsOwner : currentUser.getUserPhoneNumber();
        Specification<AdsNormalInfo> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(campainName)) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("campainName")),
                        "%" + campainName.toLowerCase() + "%"));
            }
            if (StringUtils.hasText(platformName)) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("platformName")),
                        "%" + platformName.toLowerCase() + "%"));
            }
            if (StringUtils.hasText(status)) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("status")),
                        status.toLowerCase()));
            }
            if (StringUtils.hasText(scopedAdsOwner)) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("adsOwner")),
                        "%" + scopedAdsOwner.toLowerCase() + "%"));
            }

            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        return adsNormalInfoRepository.findAll(specification, pageable);
    }

    public AdsNormalInfo update(Long id, AdsNormalInfo updateData, Long currentUserId) {
        AdsNormalInfo existing = getById(id);

        if (updateData.getCampainName() != null) {
            existing.setCampainName(updateData.getCampainName());
        }
        if (updateData.getCampainCountry() != null) {
            existing.setCampainCountry(updateData.getCampainCountry());
        }
        if (updateData.getPlatformName() != null) {
            existing.setPlatformName(updateData.getPlatformName());
        }
        if (updateData.getAffiliteUrl() != null) {
            existing.setAffiliteUrl(updateData.getAffiliteUrl());
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
        existing.setAdsOwner(resolveAdsOwner(currentUserId));

        validateAndNormalize(existing);
        existing.setUpdateDate(LocalDateTime.now());
        AdsNormalInfo saved = adsNormalInfoRepository.save(existing);
        publishAutoTaskRegistration(saved);
        return saved;
    }

    public void delete(Long id) {
        AdsNormalInfo existing = getById(id);
        publishAutoTaskDelete(existing);
        adsNormalInfoRepository.delete(existing);
    }

    private void validateAndNormalize(AdsNormalInfo adsNormalInfo) {
        if (adsNormalInfo == null) {
            throw new IllegalArgumentException("ADS_NORMAL_INFO is required");
        }
        if (!StringUtils.hasText(adsNormalInfo.getCampainName())) {
            throw new IllegalArgumentException("campainName is required");
        }
        if (!StringUtils.hasText(adsNormalInfo.getCampainCountry())) {
            throw new IllegalArgumentException("campainCountry is required");
        }
        if (!StringUtils.hasText(adsNormalInfo.getPlatformName())) {
            throw new IllegalArgumentException("platformName is required");
        }
        if (!StringUtils.hasText(adsNormalInfo.getAdsOwner())) {
            throw new IllegalArgumentException("adsOwner is required");
        }

        AdsPlatform platform = adsPlatformRepository.findByPlatformNameIgnoreCase(adsNormalInfo.getPlatformName().trim())
                .orElseThrow(() -> new IllegalArgumentException("ADS_PLATFORM not found: " + adsNormalInfo.getPlatformName()));
        User owner = userRepository.findByUserPhoneNumber(adsNormalInfo.getAdsOwner().trim())
                .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found by phone number: " + adsNormalInfo.getAdsOwner()));

        adsNormalInfo.setPlatformName(platform.getPlatformName());
        adsNormalInfo.setAdsOwner(owner.getUserPhoneNumber());
        adsNormalInfo.setCampainName(adsNormalInfo.getCampainName().trim());
        adsNormalInfo.setCampainCountry(adsNormalInfo.getCampainCountry().trim());
        adsNormalInfo.setStatus(normalizeStatus(adsNormalInfo.getStatus()));

        if (adsNormalInfo.getCampainName().length() > 32) {
            throw new IllegalArgumentException("campainName must be at most 32 characters");
        }
        if (adsNormalInfo.getCampainCountry().length() > 8) {
            throw new IllegalArgumentException("campainCountry must be at most 8 characters");
        }
        if (adsNormalInfo.getPlatformName().length() > 32) {
            throw new IllegalArgumentException("platformName must be at most 32 characters");
        }
        if (adsNormalInfo.getAffiliteUrl() != null && adsNormalInfo.getAffiliteUrl().length() > 128) {
            throw new IllegalArgumentException("affiliteUrl must be at most 128 characters");
        }
        if (adsNormalInfo.getLandingPageUrl() != null && adsNormalInfo.getLandingPageUrl().length() > 128) {
            throw new IllegalArgumentException("landingPageUrl must be at most 128 characters");
        }
        if (adsNormalInfo.getDynamicProxyInfo() != null && adsNormalInfo.getDynamicProxyInfo().length() > 256) {
            throw new IllegalArgumentException("dynamicProxyInfo must be at most 256 characters");
        }
        if (adsNormalInfo.getDynamicProxyInfoBackup() != null && adsNormalInfo.getDynamicProxyInfoBackup().length() > 256) {
            throw new IllegalArgumentException("dynamicProxyInfoBackup must be at most 256 characters");
        }
        if (adsNormalInfo.getStatus().length() > 16) {
            throw new IllegalArgumentException("status must be at most 16 characters");
        }
    }

    private String normalizeStatus(String status) {
        String normalized = StringUtils.hasText(status) ? status.trim().toUpperCase() : "RUNNING";
        if (!"PAUSED".equals(normalized) && !"RUNNING".equals(normalized)) {
            throw new IllegalArgumentException("status must be PAUSED or RUNNING");
        }
        return normalized;
    }

    private void publishAutoTaskRegistration(AdsNormalInfo saved) {
        Long intervalTime = saved.getIntervalTime();
        if (intervalTime == null) {
            return;
        }
        if (intervalTime <= 0) {
            throw new IllegalArgumentException("intervalTime must be greater than 0");
        }

        eventPublisher.publishEvent(new AdsAutoTaskRegistrationEvent(
                AdsAutoTaskAction.UPSERT,
                saved.getId(),
                saved.getAdsOwner(),
                "Normal",
                intervalTime,
                saved.getStatus()));
    }

    private void publishAutoTaskDelete(AdsNormalInfo existing) {
        eventPublisher.publishEvent(new AdsAutoTaskRegistrationEvent(
                AdsAutoTaskAction.DELETE,
                existing.getId(),
                existing.getAdsOwner(),
                "Normal",
                existing.getIntervalTime(),
                existing.getStatus()));
    }

    private String resolveAdsOwner(Long currentUserId) {
        return getCurrentUser(currentUserId).getUserPhoneNumber();
    }

    private User getCurrentUser(Long currentUserId) {
        return userRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found by id: " + currentUserId));
    }

    private boolean isAdmin(User user) {
        return user.getUserRole() != null
                && java.util.Arrays.stream(user.getUserRole().split(","))
                .map(String::trim)
                .anyMatch(role -> "admin".equalsIgnoreCase(role));
    }
}
