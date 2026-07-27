package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsNormalInfo;
import com.admire.cars.runner.entity.NormalTaskRedirectLog;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.AdsNormalInfoRepository;
import com.admire.cars.runner.repository.NormalTaskRedirectLogRepository;
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

@Service
@Transactional
public class NormalTaskRedirectLogService {

    private final NormalTaskRedirectLogRepository normalTaskRedirectLogRepository;
    private final AdsNormalInfoRepository adsNormalInfoRepository;
    private final UserRepository userRepository;

    public NormalTaskRedirectLogService(
            NormalTaskRedirectLogRepository normalTaskRedirectLogRepository,
            AdsNormalInfoRepository adsNormalInfoRepository,
            UserRepository userRepository) {
        this.normalTaskRedirectLogRepository = normalTaskRedirectLogRepository;
        this.adsNormalInfoRepository = adsNormalInfoRepository;
        this.userRepository = userRepository;
    }

    public NormalTaskRedirectLog create(NormalTaskRedirectLog normalTaskRedirectLog, Long currentUserId) {
        if (normalTaskRedirectLog == null) {
            throw new IllegalArgumentException("NORMAL_TASK_REDIRECT_LOG is required");
        }
        User currentUser = getCurrentUser(currentUserId);
        normalTaskRedirectLog.setAdsOwner(currentUser.getUserPhoneNumber());
        validateAndNormalize(normalTaskRedirectLog);
        normalTaskRedirectLog.setCreateDate(LocalDateTime.now());
        return normalTaskRedirectLogRepository.save(normalTaskRedirectLog);
    }

    @Transactional(readOnly = true)
    public NormalTaskRedirectLog getById(Long id, Long currentUserId) {
        NormalTaskRedirectLog redirectLog = normalTaskRedirectLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("NORMAL_TASK_REDIRECT_LOG not found: " + id));
        ensureReadable(redirectLog, currentUserId);
        return redirectLog;
    }

    @Transactional(readOnly = true)
    public Page<NormalTaskRedirectLog> getAll(Long currentUserId, Pageable pageable) {
        User currentUser = getCurrentUser(currentUserId);
        boolean admin = isAdmin(currentUser);
        Specification<NormalTaskRedirectLog> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!admin) {
                predicates.add(criteriaBuilder.equal(root.get("adsOwner"), currentUser.getUserPhoneNumber()));
            }
            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return normalTaskRedirectLogRepository.findAll(specification, pageable);
    }

    public NormalTaskRedirectLog update(Long id, NormalTaskRedirectLog updateData, Long currentUserId) {
        if (updateData == null) {
            throw new IllegalArgumentException("updateData is required");
        }
        NormalTaskRedirectLog existing = normalTaskRedirectLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("NORMAL_TASK_REDIRECT_LOG not found: " + id));
        ensureWritable(existing, currentUserId);

        if (updateData.getNormalInfoId() != null) {
            existing.setNormalInfoId(updateData.getNormalInfoId());
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
        return normalTaskRedirectLogRepository.save(existing);
    }

    public void delete(Long id, Long currentUserId) {
        NormalTaskRedirectLog existing = normalTaskRedirectLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("NORMAL_TASK_REDIRECT_LOG not found: " + id));
        ensureWritable(existing, currentUserId);
        normalTaskRedirectLogRepository.delete(existing);
    }

    private void validateAndNormalize(NormalTaskRedirectLog redirectLog) {
        if (redirectLog.getNormalInfoId() == null) {
            throw new IllegalArgumentException("normalInfoId is required");
        }
        AdsNormalInfo normalInfo = adsNormalInfoRepository.findById(redirectLog.getNormalInfoId())
                .orElseThrow(() -> new IllegalArgumentException("ADS_NORMAL_INFO not found: " + redirectLog.getNormalInfoId()));
        if (!StringUtils.hasText(redirectLog.getAdsOwner())) {
            throw new IllegalArgumentException("adsOwner is required");
        }
        User owner = userRepository.findByUserPhoneNumber(redirectLog.getAdsOwner().trim())
                .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found by phone number: " + redirectLog.getAdsOwner()));

        redirectLog.setNormalInfoId(normalInfo.getId());
        redirectLog.setAdsOwner(owner.getUserPhoneNumber());

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

    private void ensureReadable(NormalTaskRedirectLog redirectLog, Long currentUserId) {
        ensureAccess(redirectLog, currentUserId, "read");
    }

    private void ensureWritable(NormalTaskRedirectLog redirectLog, Long currentUserId) {
        ensureAccess(redirectLog, currentUserId, "modify");
    }

    private void ensureAccess(NormalTaskRedirectLog redirectLog, Long currentUserId, String action) {
        User currentUser = getCurrentUser(currentUserId);
        if (!isAdmin(currentUser) && !currentUser.getUserPhoneNumber().equals(redirectLog.getAdsOwner())) {
            throw new IllegalArgumentException("Unauthorized: you can only " + action + " your own normal task redirect logs");
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
