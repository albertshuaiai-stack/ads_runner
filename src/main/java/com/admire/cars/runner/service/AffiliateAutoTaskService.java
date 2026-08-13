package com.admire.cars.runner.service;

import com.admire.cars.runner.constant.Constant;
import com.admire.cars.runner.constant.StatusConstant;
import com.admire.cars.runner.dto.IpVerificationDto;
import com.admire.cars.runner.entity.*;
import com.admire.cars.runner.repository.*;
import com.admire.cars.runner.service.autotask.BonusArriveAutoSyncService;
import com.admire.cars.runner.service.autotask.BonusArriveAutoTestService;
import com.admire.cars.runner.service.proxy.IpProxyService;
import com.admire.cars.runner.service.proxy.UserAgentService;
import com.admire.cars.runner.util.AdsHttpClientTool;
import com.admire.cars.runner.util.AdsHttpRequestDto;
import com.admire.cars.runner.util.AdsHttpResponseDto;
import jakarta.persistence.criteria.Predicate;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
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
public class AffiliateAutoTaskService {


    private static final Logger log = LoggerFactory.getLogger(AffiliateAutoTaskService.class);

    @Autowired
    private BonusArriveAutoSyncService bonusArriveAutoSyncService;

    @Autowired
    private BonusArriveAutoTestService bonusArriveAutoTestService;

    @Autowired
    private AdsHttpClientTool adsHttpClientTool;

    @Autowired
    private AffiliateAutoTaskRepository affiliateAutoTaskRepository;

    @Autowired
    private AffiliateAdsRepository affiliateAdsRepository;

    @Autowired
    private IpProxyInfoRepository ipProxyInfoRepository;

    @Autowired
    private IpProxyService ipProxyService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AffiliateTestRepository affiliateTestRepository;

    @Autowired
    private UserAgentService userAgentService;


    public AffiliateAutoTask create(AffiliateAutoTask task, Long currentUserId) {
        if (task == null) {
            throw new IllegalArgumentException("AFFILIATE_AUTO_TASK is required");
        }
        User currentUser = getCurrentUser(currentUserId);
        if (!isAdmin(currentUser) || !StringUtils.hasText(task.getAdsOwner())) {
            task.setAdsOwner(currentUser.getUserPhoneNumber());
        }
        validateAndNormalize(task, currentUser);
        task.setCreateDate(LocalDateTime.now());
        return affiliateAutoTaskRepository.save(task);
    }

    @Transactional(readOnly = true)
    public AffiliateAutoTask getById(Long id, Long currentUserId) {
        AffiliateAutoTask task = affiliateAutoTaskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AFFILIATE_AUTO_TASK not found: " + id));
        ensureReadable(task, currentUserId);
        return task;
    }

    @Transactional(readOnly = true)
    public Page<AffiliateAutoTask> search(
            String adsOwner,
            String affiliateNetwork,
            String region,
            String status,
            Long currentUserId,
            Pageable pageable) {
        User currentUser = getCurrentUser(currentUserId);
        boolean admin = isAdmin(currentUser);
        String normalizedOwnerFilter = admin ? trimToNull(adsOwner) : currentUser.getUserPhoneNumber();

        Specification<AffiliateAutoTask> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(normalizedOwnerFilter)) {
                predicates.add(criteriaBuilder.equal(root.get("adsOwner"), normalizedOwnerFilter));
            }
            if (StringUtils.hasText(affiliateNetwork)) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.upper(root.get("affiliateNetwork")),
                        "%" + affiliateNetwork.toUpperCase(Locale.ROOT) + "%"));
            }
            if (StringUtils.hasText(region)) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.upper(root.get("region")),
                        "%" + region.toUpperCase(Locale.ROOT) + "%"));
            }
            if (StringUtils.hasText(status)) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status.toUpperCase(Locale.ROOT)));
            }

            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        return affiliateAutoTaskRepository.findAll(specification, pageable);
    }

    public AffiliateAutoTask update(Long id, AffiliateAutoTask updateData, Long currentUserId) {
        if (updateData == null) {
            throw new IllegalArgumentException("updateData is required");
        }
        User currentUser = getCurrentUser(currentUserId);

        AffiliateAutoTask existing = affiliateAutoTaskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AFFILIATE_AUTO_TASK not found: " + id));
        ensureWritable(existing, currentUserId);

        if (updateData.getAffiliateNetwork() != null) {
            existing.setAffiliateNetwork(updateData.getAffiliateNetwork());
        }
        if (updateData.getAutoTaskType() != null) {
            existing.setAutoTaskType(updateData.getAutoTaskType());
        }
        if (updateData.getRegion() != null) {
            existing.setRegion(updateData.getRegion());
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
        if (updateData.getStartDate() != null) {
            existing.setStartDate(updateData.getStartDate());
        }
        if (updateData.getEndDate() != null) {
            existing.setEndDate(updateData.getEndDate());
        }
        if (updateData.getDuration() != null) {
            existing.setDuration(updateData.getDuration());
        }
        if (updateData.getStatus() != null) {
            existing.setStatus(updateData.getStatus());
        }
        if (isAdmin(currentUser) && updateData.getAdsOwner() != null) {
            existing.setAdsOwner(updateData.getAdsOwner());
        }

        validateAndNormalize(existing, currentUser);
        existing.setUpdateDate(LocalDateTime.now());
        return affiliateAutoTaskRepository.save(existing);
    }

    public void delete(Long id, Long currentUserId) {
        AffiliateAutoTask existing = affiliateAutoTaskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AFFILIATE_AUTO_TASK not found: " + id));
        ensureWritable(existing, currentUserId);
        affiliateAutoTaskRepository.delete(existing);
    }

    private void validateAndNormalize(AffiliateAutoTask task, User currentUser) {
        task.setAffiliateNetwork(normalizeEnumLike(task.getAffiliateNetwork(), null));
        task.setAutoTaskType(normalizeEnumLike(task.getAutoTaskType(), null));
        task.setRegion(normalizeEnumLike(task.getRegion(), null));
        task.setStatus(normalizeEnumLike(task.getStatus(), "NOT_RUN"));

        if (task.getTotalCount() == null) {
            task.setTotalCount(0L);
        }
        if (task.getSuccessCount() == null) {
            task.setSuccessCount(0L);
        }
        if (task.getFailedCount() == null) {
            task.setFailedCount(0L);
        }

        if (!StringUtils.hasText(task.getAffiliateNetwork())) {
            throw new IllegalArgumentException("affiliateNetwork is required");
        }
        if (!StringUtils.hasText(task.getAutoTaskType())) {
            throw new IllegalArgumentException("autoTaskType is required");
        }
        if (!StringUtils.hasText(task.getRegion())) {
            throw new IllegalArgumentException("region is required");
        }
        if (!StringUtils.hasText(task.getAdsOwner())) {
            throw new IllegalArgumentException("adsOwner is required");
        }

        User owner = userRepository.findByUserPhoneNumber(task.getAdsOwner().trim())
                .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found by phone number: " + task.getAdsOwner()));
        task.setAdsOwner(owner.getUserPhoneNumber());

        if (!isAdmin(currentUser) && !currentUser.getUserPhoneNumber().equals(task.getAdsOwner())) {
            throw new IllegalArgumentException("Unauthorized: adsOwner must match current user");
        }

        validateAllowed(task.getAutoTaskType(), "autoTaskType", "SYNC", "TEST");
        validateAllowed(task.getStatus(), "status", "NOT_RUN", "IN_PROGRESS", "SUCCESS", "FAILED");

        if (task.getTotalCount() < 0 || task.getSuccessCount() < 0 || task.getFailedCount() < 0) {
            throw new IllegalArgumentException("count fields must be non-negative");
        }

        validateLength(task.getAffiliateNetwork(), "affiliateNetwork", 64);
        validateLength(task.getAutoTaskType(), "autoTaskType", 64);
        validateLength(task.getRegion(), "region", 64);
        validateLength(task.getStatus(), "status", 64);
        validateLength(task.getAdsOwner(), "adsOwner", 32);
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

    private void ensureReadable(AffiliateAutoTask task, Long currentUserId) {
        ensureAccess(task, currentUserId, "read");
    }

    private void ensureWritable(AffiliateAutoTask task, Long currentUserId) {
        ensureAccess(task, currentUserId, "modify");
    }

    private void ensureAccess(AffiliateAutoTask task, Long currentUserId, String action) {
        User currentUser = getCurrentUser(currentUserId);
        if (!isAdmin(currentUser) && !currentUser.getUserPhoneNumber().equals(task.getAdsOwner())) {
            throw new IllegalArgumentException("Unauthorized: you can only " + action + " your own auto tasks");
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

    public AffiliateAutoTask markInProgress(Long id, Long currentUserId) {
        AffiliateAutoTask existing = affiliateAutoTaskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AFFILIATE_AUTO_TASK not found: " + id));
        ensureWritable(existing, currentUserId);
        if ("IN_PROGRESS".equalsIgnoreCase(existing.getStatus())) {
            throw new IllegalArgumentException("AFFILIATE_AUTO_TASK is already IN_PROGRESS");
        }
        existing.setStatus(StatusConstant.IN_PROGRESS);
        existing.setTotalCount(0L);
        existing.setSuccessCount(0L);
        existing.setFailedCount(0L);
        existing.setStartDate(LocalDateTime.now());
        existing.setEndDate(null);
        existing.setDuration(null);
        existing.setUpdateDate(LocalDateTime.now());
        return affiliateAutoTaskRepository.save(existing);
    }

    public AffiliateAds markAffiliateAdsInProgress(Long id) {
        AffiliateAds existing = affiliateAdsRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("AFFILIATE_AUTO_TASK not found: " + id));
        if ("IN_PROGRESS".equalsIgnoreCase(existing.getStatus())) {
            throw new IllegalArgumentException("AFFILIATE_ADS is already IN_PROGRESS");
        }
        existing.setStatus(StatusConstant.IN_PROGRESS);
        existing.setUpdateDate(LocalDateTime.now());
        return affiliateAdsRepository.save(existing);
    }

    /**
     * Sync ads from affiliate networks asynchronously
     * @param taskId
     */
    @Async("adsAsyncExecutor")
    @Transactional
    public void syncAdsAsync(Long taskId) {
        bonusArriveAutoSyncService.syncAdsNow(taskId);
    }

    /**
     * Test all ads according taskid
     * @param taskId
     */
    @Async("adsAsyncExecutor")
    @Transactional
    public void testAdsAsync(Long taskId) {
        bonusArriveAutoTestService.testAdsAsync(taskId);
    }

    /**
     * Test ad
     * @param affiliateAdsId
     */
    @Async("adsAsyncExecutor")
    @Transactional
    public void testAd(Long affiliateAdsId) {
        AffiliateAds affiliateAds = affiliateAdsRepository.findById(affiliateAdsId)
                .orElseThrow(() -> new IllegalArgumentException("AFFILIATE_ADS not found: " + affiliateAdsId));
        final List<String> proxyFailures = new ArrayList<>();

        AffiliateTest result = new AffiliateTest();
        result.setAffiliateNetwork(affiliateAds.getAffiliateNetwork());
        result.setRegion(affiliateAds.getRegion());
        result.setSiteName(affiliateAds.getSiteName());
        result.setSiteUrl(affiliateAds.getSiteUrl());
        result.setTrackingUrl(affiliateAds.getTrackingUrl());
        result.setAdsOwner(affiliateAds.getAdsOwner());
        String region = affiliateAds.getRegion() == null ? null : affiliateAds.getRegion().trim();

        List<IpProxyInfo> proxies = ipProxyInfoRepository
                .findByAdsOwnerAndStatusIgnoreCaseAndTargetCountryIgnoreCaseAndProxyTypeAndProxyProtocolOrderByIdDesc(
                affiliateAds.getAdsOwner(),
                StatusConstant.ENABLED,
                region,
                Constant.PROXY_TYPE_DYNAMIC,
                Constant.PROXY_PROTOCOL_SOCKETS5);
        if (proxies.isEmpty()) {
            log.warn("No ENABLED IP_PROXY_INFO found for adsOwner: {} with targetCountry: {}", affiliateAds.getAdsOwner(), region);
            log.error("No ENABLED IP_PROXY_INFO found for adsOwner: {}", affiliateAds.getAdsOwner());
            result.setStatus(StatusConstant.FAILED);
            affiliateTestRepository.save(result);
            return;
        }
        OkHttpClient okHttpClient = null;
        IpProxyInfo ipProxyInfo = null;
        IpVerificationDto ipVerification = null;
        for (IpProxyInfo proxy : proxies) {
            okHttpClient = ipProxyService.buildOkHttpClient(proxy.getProxyInfo());
            ipVerification = ipProxyService.ipVerification4OkHttpClient(okHttpClient, region);
            ipProxyInfo = proxy;
            if (ipVerification.isMatched()) {
                break;
            } else {
                proxyFailures.add("proxyId=" + ipProxyInfo.getId()
                        + " region mismatch expected=" + region
                        + " actual=" + ipVerification.getCountryCode());
            }
        }
        String userAgent = userAgentService.getUserAgent();
        if (null != ipVerification && ipVerification.isMatched()) {
            log.info("AFFILIATE_TEST_TASK Proxy verification passed. Ad ID={} proxyId={} region={}, ipVerification:{}",
                    affiliateAdsId, ipProxyInfo.getId(), region, ipVerification);
            final long startTime = System.currentTimeMillis();
            AdsHttpRequestDto adsHttpRequestDto = new AdsHttpRequestDto(
                    affiliateAds.getTrackingUrl(),affiliateAds.getSiteUrl(),Constant.DEVICE_TYPE_DESK,userAgent);
            AdsHttpResponseDto adsHttpResponseDto = adsHttpClientTool.applyAffiliateAd(okHttpClient, adsHttpRequestDto);
            final long durationMillis = System.currentTimeMillis() - startTime;
            log.info("AFFILIATE_ADS Test. Ad ID={} proxyId={} region={}, ipVerification:{} duration={}ms",
                    affiliateAdsId, ipProxyInfo.getId(), region, ipVerification, durationMillis);
            result.setFinalUrl(adsHttpResponseDto.getUrl());
            result.setStatus(adsHttpResponseDto.getStatus());

            affiliateAds.setStatus(adsHttpResponseDto.getStatus());
        } else {
            String failureMessage = String.join("; ", proxyFailures);
            log.error("AFFILIATE_ADS Test Proxy verification failed. Ad ID={} region={} failures={}",
                    affiliateAdsId, region, failureMessage);
            result.setStatus(StatusConstant.FAILED);
            affiliateAds.setStatus(StatusConstant.FAILED);
        }
        affiliateAdsRepository.save(affiliateAds);
        affiliateTestRepository.save(result);
    }


}
