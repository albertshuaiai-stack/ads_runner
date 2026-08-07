package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.IpProxyInfo;
import com.admire.cars.runner.entity.User;
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
public class IpProxyInfoService {

    private final IpProxyInfoRepository ipProxyInfoRepository;
    private final UserRepository userRepository;

    public IpProxyInfoService(IpProxyInfoRepository ipProxyInfoRepository, UserRepository userRepository) {
        this.ipProxyInfoRepository = ipProxyInfoRepository;
        this.userRepository = userRepository;
    }

    public IpProxyInfo create(IpProxyInfo ipProxyInfo, Long currentUserId) {
        if (ipProxyInfo == null) {
            throw new IllegalArgumentException("IP_PROXY_INFO is required");
        }
        User currentUser = getCurrentUser(currentUserId);
        ipProxyInfo.setAdsOwner(currentUser.getUserPhoneNumber());
        validateAndNormalize(ipProxyInfo);
        ipProxyInfo.setCreateDate(LocalDateTime.now());
        return ipProxyInfoRepository.save(ipProxyInfo);
    }

    @Transactional(readOnly = true)
    public IpProxyInfo getById(Long id, Long currentUserId) {
        IpProxyInfo ipProxyInfo = ipProxyInfoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("IP_PROXY_INFO not found: " + id));
        ensureReadable(ipProxyInfo, currentUserId);
        return ipProxyInfo;
    }

    @Transactional(readOnly = true)
    public Page<IpProxyInfo> search(
            String proxyType,
            String proxyProtocol,
            String status,
            Long currentUserId,
            Pageable pageable) {
        User currentUser = getCurrentUser(currentUserId);
        boolean admin = isAdmin(currentUser);

        Specification<IpProxyInfo> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (!admin) {
                predicates.add(criteriaBuilder.equal(root.get("adsOwner"), currentUser.getUserPhoneNumber()));
            }
            if (StringUtils.hasText(proxyType)) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("proxyType")),
                        proxyType.trim().toLowerCase(Locale.ROOT)));
            }
            if (StringUtils.hasText(proxyProtocol)) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("proxyProtocol")),
                        proxyProtocol.trim().toLowerCase(Locale.ROOT)));
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
        return ipProxyInfoRepository.findAll(specification, pageable);
    }

    public IpProxyInfo update(Long id, IpProxyInfo updateData, Long currentUserId) {
        if (updateData == null) {
            throw new IllegalArgumentException("updateData is required");
        }
        IpProxyInfo existing = ipProxyInfoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("IP_PROXY_INFO not found: " + id));
        ensureWritable(existing, currentUserId);

        if (updateData.getProxyType() != null) {
            existing.setProxyType(updateData.getProxyType());
        }
        if (updateData.getProxyProtocol() != null) {
            existing.setProxyProtocol(updateData.getProxyProtocol());
        }
        if (updateData.getProxyInfo() != null) {
            existing.setProxyInfo(updateData.getProxyInfo());
        }
        if (updateData.getStatus() != null) {
            existing.setStatus(updateData.getStatus());
        }

        validateAndNormalize(existing);
        existing.setUpdateDate(LocalDateTime.now());
        return ipProxyInfoRepository.save(existing);
    }

    public void delete(Long id, Long currentUserId) {
        IpProxyInfo existing = ipProxyInfoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("IP_PROXY_INFO not found: " + id));
        ensureWritable(existing, currentUserId);
        ipProxyInfoRepository.delete(existing);
    }

    private void validateAndNormalize(IpProxyInfo ipProxyInfo) {
        if (!StringUtils.hasText(ipProxyInfo.getAdsOwner())) {
            throw new IllegalArgumentException("adsOwner is required");
        }
        User owner = userRepository.findByUserPhoneNumber(ipProxyInfo.getAdsOwner().trim())
                .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found by phone number: " + ipProxyInfo.getAdsOwner()));
        ipProxyInfo.setAdsOwner(owner.getUserPhoneNumber());

        ipProxyInfo.setProxyType(normalizeEnumLike(ipProxyInfo.getProxyType(), "DYNAMIC"));
        ipProxyInfo.setProxyProtocol(normalizeEnumLike(ipProxyInfo.getProxyProtocol(), "HTTPS"));
        ipProxyInfo.setStatus(normalizeEnumLike(ipProxyInfo.getStatus(), "ENABLED"));
        ipProxyInfo.setProxyInfo(trimToNull(ipProxyInfo.getProxyInfo()));

        if (!StringUtils.hasText(ipProxyInfo.getProxyInfo())) {
            throw new IllegalArgumentException("proxyInfo is required");
        }

        validateAllowed(ipProxyInfo.getProxyType(), "proxyType", "DYNAMIC", "STATIC");
        validateAllowed(ipProxyInfo.getProxyProtocol(), "proxyProtocol", "HTTPS", "SOCKETS5");
        validateAllowed(ipProxyInfo.getStatus(), "status", "ENABLED", "DISABLED");

        validateLength(ipProxyInfo.getProxyType(), "proxyType", 32);
        validateLength(ipProxyInfo.getProxyProtocol(), "proxyProtocol", 32);
        validateLength(ipProxyInfo.getProxyInfo(), "proxyInfo", 512);
        validateLength(ipProxyInfo.getStatus(), "status", 32);
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

    private void ensureReadable(IpProxyInfo ipProxyInfo, Long currentUserId) {
        ensureAccess(ipProxyInfo, currentUserId, "read");
    }

    private void ensureWritable(IpProxyInfo ipProxyInfo, Long currentUserId) {
        ensureAccess(ipProxyInfo, currentUserId, "modify");
    }

    private void ensureAccess(IpProxyInfo ipProxyInfo, Long currentUserId, String action) {
        User currentUser = getCurrentUser(currentUserId);
        if (!isAdmin(currentUser) && !currentUser.getUserPhoneNumber().equals(ipProxyInfo.getAdsOwner())) {
            throw new IllegalArgumentException("Unauthorized: you can only " + action + " your own proxy info");
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
