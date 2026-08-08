package com.admire.cars.runner.service;

import com.admire.cars.runner.constant.Constant;
import com.admire.cars.runner.entity.AdsTaskLog;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.AdsTaskLogRepository;
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

@Service
@Transactional
public class AdsTaskLogService {

    private final AdsTaskLogRepository adsTaskLogRepository;
    private final UserRepository userRepository;

    public AdsTaskLogService(
            AdsTaskLogRepository adsTaskLogRepository,
            UserRepository userRepository) {
        this.adsTaskLogRepository = adsTaskLogRepository;
        this.userRepository = userRepository;
    }

    public AdsTaskLog create(AdsTaskLog adsTaskLog, Long currentUserId) {
        if (adsTaskLog == null) {
            throw new IllegalArgumentException("ADS_TASK_LOG is required");
        }
        User currentUser = getCurrentUser(currentUserId);
        adsTaskLog.setAdsOwner(currentUser.getUserPhoneNumber());
        validateAndNormalize(adsTaskLog);
        return adsTaskLogRepository.save(adsTaskLog);
    }

    @Transactional(readOnly = true)
    public AdsTaskLog getById(Long id, Long currentUserId) {
        AdsTaskLog redirectLog = adsTaskLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ADS_TASK_LOG not found: " + id));
        ensureReadable(redirectLog, currentUserId);
        return redirectLog;
    }

    @Transactional(readOnly = true)
    public Page<AdsTaskLog> queryLogs(String adsOwner, String adsType, String adsName, Long currentUserId, Pageable pageable) {
        User currentUser = getCurrentUser(currentUserId);
        boolean admin = isAdmin(currentUser);
        String normalizedAdsOwner = normalizeOptional(adsOwner);
        String normalizedAdsType = normalizeQueryAdsType(adsType);
        String normalizedAdsName = normalizeOptional(adsName);
        Specification<AdsTaskLog> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!admin) {
                predicates.add(criteriaBuilder.equal(root.get("adsOwner"), currentUser.getUserPhoneNumber()));
            } else if (StringUtils.hasText(normalizedAdsOwner)) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("adsOwner")),
                        normalizedAdsOwner.toLowerCase()));
            }
            if (StringUtils.hasText(normalizedAdsType)) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("adsType")),
                        normalizedAdsType.toLowerCase()));
            }
            if (StringUtils.hasText(normalizedAdsName)) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("adsName")),
                        normalizedAdsName.toLowerCase()));
            }
            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return adsTaskLogRepository.findAll(specification, pageable);
    }

    public AdsTaskLog update(Long id, AdsTaskLog updateData, Long currentUserId) {
        if (updateData == null) {
            throw new IllegalArgumentException("updateData is required");
        }
        AdsTaskLog existing = adsTaskLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ADS_TASK_LOG not found: " + id));
        ensureWritable(existing, currentUserId);

        if (updateData.getAdsName() != null) {
            existing.setAdsName(updateData.getAdsName());
        }
        if (updateData.getAdsType() != null) {
            existing.setAdsType(updateData.getAdsType());
        }
        if (updateData.getPlatformName() != null) {
            existing.setPlatformName(updateData.getPlatformName());
        }
        if (updateData.getDevice() != null) {
            existing.setDevice(updateData.getDevice());
        }
        if (updateData.getUserAgent() != null) {
            existing.setUserAgent(updateData.getUserAgent());
        }
        if (updateData.getIp() != null) {
            existing.setIp(updateData.getIp());
        }
        if (updateData.getCountryCode() != null) {
            existing.setCountryCode(updateData.getCountryCode());
        }
        if (updateData.getSequence() != null) {
            existing.setSequence(updateData.getSequence());
        }
        if (updateData.getRequestUrl() != null) {
            existing.setRequestUrl(updateData.getRequestUrl());
        }
        if (updateData.getResponseUrl() != null) {
            existing.setResponseUrl(updateData.getResponseUrl());
        }
        if (updateData.getStatusCode() != null) {
            existing.setStatusCode(updateData.getStatusCode());
        }
        if (updateData.getDurationMillis() != null) {
            existing.setDurationMillis(updateData.getDurationMillis());
        }
        if (updateData.getLocation() != null) {
            existing.setLocation(updateData.getLocation());
        }
        if (updateData.getSuccess() != null) {
            existing.setSuccess(updateData.getSuccess());
        }
        if (updateData.getErrMsg() != null) {
            existing.setErrMsg(updateData.getErrMsg());
        }

        validateAndNormalize(existing);
        return adsTaskLogRepository.save(existing);
    }

    public void delete(Long id, Long currentUserId) {
        AdsTaskLog existing = adsTaskLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ADS_TASK_LOG not found: " + id));
        ensureWritable(existing, currentUserId);
        adsTaskLogRepository.delete(existing);
    }

    private void validateAndNormalize(AdsTaskLog redirectLog) {
        if (!StringUtils.hasText(redirectLog.getAdsName())) {
            throw new IllegalArgumentException("adsName is required");
        }
        if (!StringUtils.hasText(redirectLog.getAdsType())) {
            throw new IllegalArgumentException("adsType is required");
        }
        if (!StringUtils.hasText(redirectLog.getAdsOwner())) {
            throw new IllegalArgumentException("adsOwner is required");
        }
        User owner = userRepository.findByUserPhoneNumber(redirectLog.getAdsOwner().trim())
                .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found by phone number: " + redirectLog.getAdsOwner()));

        redirectLog.setAdsOwner(owner.getUserPhoneNumber());
        redirectLog.setAdsName(redirectLog.getAdsName().trim());
        redirectLog.setAdsType(normalizeAdsType(redirectLog.getAdsType()));
        redirectLog.setPlatformName(trimToNull(redirectLog.getPlatformName()));

        redirectLog.setDevice(trimToNull(redirectLog.getDevice()));
        redirectLog.setUserAgent(trimToNull(redirectLog.getUserAgent()));
        redirectLog.setIp(trimToNull(redirectLog.getIp()));
        redirectLog.setCountryCode(trimToNull(redirectLog.getCountryCode()));
        redirectLog.setRequestUrl(trimToNull(redirectLog.getRequestUrl()));
        redirectLog.setResponseUrl(trimToNull(redirectLog.getResponseUrl()));
        redirectLog.setStatusCode(trimToNull(redirectLog.getStatusCode()));
        redirectLog.setDurationMillis(trimToNull(redirectLog.getDurationMillis()));
        redirectLog.setLocation(trimToNull(redirectLog.getLocation()));
        redirectLog.setErrMsg(trimToNull(redirectLog.getErrMsg()));

        validateLength(redirectLog.getAdsName(), "adsName", 128);
        validateLength(redirectLog.getAdsType(), "adsType", 32);
        validateLength(redirectLog.getPlatformName(), "platformName", 64);
        validateLength(redirectLog.getDevice(), "device", 64);
        validateLength(redirectLog.getUserAgent(), "userAgent", 512);
        validateLength(redirectLog.getIp(), "ip", 64);
        validateLength(redirectLog.getCountryCode(), "countryCode", 16);
        validateLength(redirectLog.getRequestUrl(), "requestUrl", 1024);
        validateLength(redirectLog.getResponseUrl(), "responseUrl", 1024);
        validateLength(redirectLog.getStatusCode(), "statusCode", 16);
        validateLength(redirectLog.getDurationMillis(), "durationMillis", 16);
        validateLength(redirectLog.getLocation(), "location", 1024);
        validateLength(redirectLog.getErrMsg(), "errMsg", 256);
    }

    private String normalizeAdsType(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException("adsType is required");
        }
        if ("normal".equalsIgnoreCase(normalized)) {
            return Constant.ADS_TYPE_NORMAL;
        }
        if ("matrix".equalsIgnoreCase(normalized)) {
            return Constant.ADS_TYPE_MATRIX;
        }
        throw new IllegalArgumentException("adsType must be Normal or Matrix");
    }

    private String normalizeQueryAdsType(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        if ("normal".equalsIgnoreCase(normalized)) {
            return Constant.ADS_TYPE_NORMAL;
        }
        if ("matrix".equalsIgnoreCase(normalized)) {
            return Constant.ADS_TYPE_MATRIX;
        }
        throw new IllegalArgumentException("adsType must be Normal or Matrix");
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

    private String normalizeOptional(String value) {
        return trimToNull(value);
    }

    private void ensureReadable(AdsTaskLog redirectLog, Long currentUserId) {
        ensureAccess(redirectLog, currentUserId, "read");
    }

    private void ensureWritable(AdsTaskLog redirectLog, Long currentUserId) {
        ensureAccess(redirectLog, currentUserId, "modify");
    }

    private void ensureAccess(AdsTaskLog redirectLog, Long currentUserId, String action) {
        User currentUser = getCurrentUser(currentUserId);
        if (!isAdmin(currentUser) && !currentUser.getUserPhoneNumber().equals(redirectLog.getAdsOwner())) {
            throw new IllegalArgumentException("Unauthorized: you can only " + action + " your own ads task logs");
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
