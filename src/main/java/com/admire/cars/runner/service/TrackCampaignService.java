package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.TrackCampaign;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.TrackCampaignRepository;
import com.admire.cars.runner.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Value;
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
public class TrackCampaignService {

    private final TrackCampaignRepository campaignRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    // 中转域名根地址, 用于拼出对外追踪链接 / public tracker domain used to build the tracking url
    @Value("${tracker.base-url:}")
    private String baseUrl;

    public TrackCampaignService(
            TrackCampaignRepository campaignRepository,
            UserRepository userRepository,
            UserService userService) {
        this.campaignRepository = campaignRepository;
        this.userRepository = userRepository;
        this.userService = userService;
    }

    /**
     * 供 Google Ads 脚本获取 Final URL / resolve the Final URL for ads scripts.
     * 按 api_key 定位用户, 再按 campaign 名找到运行中的追踪配置.
     */
    @Transactional(readOnly = true)
    public String resolveTrackerUrl(String campaignName, String apiKey) {
        if (!StringUtils.hasText(campaignName)) {
            throw new IllegalArgumentException("campaignName is required");
        }
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalArgumentException("tracker.base-url is not configured");
        }
        User user = userService.getEnabledUserByApiKey(apiKey);
        List<TrackCampaign> campaigns =
                campaignRepository.findByAdsOwnerAndCampaignName(user.getUserPhoneNumber(), campaignName.trim());
        for (TrackCampaign campaign : campaigns) {
            if (TrackCampaign.STATUS_RUNNING.equalsIgnoreCase(campaign.getStatus())) {
                // 去掉结尾斜杠, 避免出现 //r/ 双斜杠 / strip trailing slashes to avoid //r/
                return baseUrl.trim().replaceAll("/+$", "") + "/r/" + campaign.getSlug();
            }
        }
        return null;
    }

    public TrackCampaign create(TrackCampaign campaign, Long currentUserId) {
        if (campaign == null) {
            throw new IllegalArgumentException("campaign is required");
        }
        User currentUser = getCurrentUser(currentUserId);
        campaign.setAdsOwner(currentUser.getUserPhoneNumber());
        validateAndNormalize(campaign);

        campaignRepository.findBySlug(campaign.getSlug()).ifPresent(existing -> {
            throw new IllegalArgumentException("slug already exists: " + campaign.getSlug());
        });

        campaign.setId(null);
        campaign.setCreateDate(LocalDateTime.now());
        return campaignRepository.save(campaign);
    }

    @Transactional(readOnly = true)
    public TrackCampaign getById(Long id, Long currentUserId) {
        TrackCampaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TRACK_CAMPAIGN not found: " + id));
        ensureReadable(campaign, currentUserId);
        return campaign;
    }

    @Transactional(readOnly = true)
    public Page<TrackCampaign> search(String slug,
                                      String campaignName,
                                      String status,
                                      String adsOwner,
                                      Long currentUserId,
                                      Pageable pageable) {
        User currentUser = getCurrentUser(currentUserId);
        boolean admin = isAdmin(currentUser);

        Specification<TrackCampaign> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (admin) {
                if (StringUtils.hasText(adsOwner)) {
                    predicates.add(criteriaBuilder.equal(root.get("adsOwner"), adsOwner.trim()));
                }
            } else {
                predicates.add(criteriaBuilder.equal(root.get("adsOwner"), currentUser.getUserPhoneNumber()));
            }

            if (StringUtils.hasText(slug)) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("slug")),
                        "%" + slug.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (StringUtils.hasText(campaignName)) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("campaignName")),
                        "%" + campaignName.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (StringUtils.hasText(status)) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status.trim()));
            }

            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        return campaignRepository.findAll(specification, pageable);
    }

    public TrackCampaign update(Long id, TrackCampaign updateData, Long currentUserId) {
        if (updateData == null) {
            throw new IllegalArgumentException("updateData is required");
        }
        TrackCampaign existing = campaignRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TRACK_CAMPAIGN not found: " + id));
        ensureWritable(existing, currentUserId);

        if (updateData.getCampaignName() != null) {
            existing.setCampaignName(updateData.getCampaignName());
        }
        if (updateData.getOfferUrlTemplate() != null) {
            existing.setOfferUrlTemplate(updateData.getOfferUrlTemplate());
        }
        if (updateData.getLandingPageUrl() != null) {
            existing.setLandingPageUrl(updateData.getLandingPageUrl());
        }
        if (updateData.getAllowedLandingHosts() != null) {
            existing.setAllowedLandingHosts(updateData.getAllowedLandingHosts());
        }
        if (updateData.getPoolCode() != null) {
            existing.setPoolCode(updateData.getPoolCode());
        }
        if (updateData.getRotateStrategy() != null) {
            existing.setRotateStrategy(updateData.getRotateStrategy());
        }
        if (updateData.getFixedSiteId() != null) {
            existing.setFixedSiteId(updateData.getFixedSiteId());
        }
        if (updateData.getStatus() != null) {
            existing.setStatus(updateData.getStatus());
        }
        if (updateData.getRemarks() != null) {
            existing.setRemarks(updateData.getRemarks());
        }

        validateAndNormalize(existing);
        existing.setUpdateDate(LocalDateTime.now());
        return campaignRepository.save(existing);
    }

    public void delete(Long id, Long currentUserId) {
        TrackCampaign existing = campaignRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TRACK_CAMPAIGN not found: " + id));
        ensureWritable(existing, currentUserId);
        campaignRepository.delete(existing);
    }

    private void validateAndNormalize(TrackCampaign campaign) {
        if (!StringUtils.hasText(campaign.getAdsOwner())) {
            throw new IllegalArgumentException("adsOwner is required");
        }
        userRepository.findByUserPhoneNumber(campaign.getAdsOwner().trim())
                .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found by phone number: " + campaign.getAdsOwner()));

        // slug 只允许安全字符, 避免跳转链接异常 / slug must be url safe
        String slug = trimToNull(campaign.getSlug());
        if (slug == null) {
            throw new IllegalArgumentException("slug is required");
        }
        if (!slug.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("slug must contain only letters, digits, underscore or dash");
        }
        campaign.setSlug(slug);

        String template = trimToNull(campaign.getOfferUrlTemplate());
        if (template == null) {
            throw new IllegalArgumentException("offerUrlTemplate is required");
        }
        campaign.setOfferUrlTemplate(template);

        if (!StringUtils.hasText(campaign.getStatus())) {
            campaign.setStatus(TrackCampaign.STATUS_RUNNING);
        }
        if (!TrackCampaign.STATUS_RUNNING.equals(campaign.getStatus())
                && !TrackCampaign.STATUS_PAUSED.equals(campaign.getStatus())) {
            throw new IllegalArgumentException("status must be RUNNING or PAUSED");
        }
        if (!StringUtils.hasText(campaign.getRotateStrategy())) {
            campaign.setRotateStrategy(TrackCampaign.ROTATE_WEIGHTED_RANDOM);
        }
        if (!TrackCampaign.ROTATE_WEIGHTED_RANDOM.equals(campaign.getRotateStrategy())
                && !TrackCampaign.ROTATE_FIXED.equals(campaign.getRotateStrategy())) {
            throw new IllegalArgumentException("rotateStrategy must be WEIGHTED_RANDOM or FIXED");
        }
        if (TrackCampaign.ROTATE_FIXED.equals(campaign.getRotateStrategy())
                && !StringUtils.hasText(campaign.getFixedSiteId())) {
            throw new IllegalArgumentException("fixedSiteId is required when rotateStrategy is FIXED");
        }
        if (TrackCampaign.ROTATE_WEIGHTED_RANDOM.equals(campaign.getRotateStrategy())
                && !StringUtils.hasText(campaign.getPoolCode())) {
            throw new IllegalArgumentException("poolCode is required when rotateStrategy is WEIGHTED_RANDOM");
        }

        validateLength(campaign.getCampaignName(), "campaignName", 128);
        validateLength(campaign.getLandingPageUrl(), "landingPageUrl", 1024);
        validateLength(campaign.getAllowedLandingHosts(), "allowedLandingHosts", 512);
        validateLength(campaign.getRemarks(), "remarks", 128);
    }

    private void ensureReadable(TrackCampaign campaign, Long currentUserId) {
        ensureAccess(campaign, currentUserId, "read");
    }

    private void ensureWritable(TrackCampaign campaign, Long currentUserId) {
        ensureAccess(campaign, currentUserId, "modify");
    }

    private void ensureAccess(TrackCampaign campaign, Long currentUserId, String action) {
        User currentUser = getCurrentUser(currentUserId);
        if (!isAdmin(currentUser) && !currentUser.getUserPhoneNumber().equals(campaign.getAdsOwner())) {
            throw new IllegalArgumentException("Unauthorized: you can only " + action + " your own track campaigns");
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void validateLength(String value, String fieldName, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must be at most " + maxLength + " characters");
        }
    }
}
