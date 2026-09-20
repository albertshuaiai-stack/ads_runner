package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.ToolIp;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.ToolIpRepository;
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
import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class ToolIpService {

    private final ToolIpRepository toolIpRepository;
    private final UserRepository userRepository;

    public ToolIpService(ToolIpRepository toolIpRepository, UserRepository userRepository) {
        this.toolIpRepository = toolIpRepository;
        this.userRepository = userRepository;
    }

    public ToolIp create(ToolIp toolIp, Long currentUserId) {
        if (toolIp == null) {
            throw new IllegalArgumentException("TOOL_IP is required");
        }
        User currentUser = getCurrentUser(currentUserId);
        toolIp.setAdsOwner(currentUser.getUserPhoneNumber());
        validateAndNormalize(toolIp);
        toolIp.setCreateDate(LocalDateTime.now());
        return toolIpRepository.save(toolIp);
    }

    @Transactional(readOnly = true)
    public ToolIp getById(Long id, Long currentUserId) {
        ToolIp toolIp = toolIpRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TOOL_IP not found: " + id));
        ensureReadable(toolIp, currentUserId);
        return toolIp;
    }

    @Transactional(readOnly = true)
    public Page<ToolIp> search(String ip,
                               String adsOwner,
                               Long currentUserId,
                               Pageable pageable) {
        User currentUser = getCurrentUser(currentUserId);
        boolean admin = isAdmin(currentUser);

        Specification<ToolIp> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (!admin) {
                predicates.add(cb.equal(root.get("adsOwner"), currentUser.getUserPhoneNumber()));
            } else {
                if (StringUtils.hasText(adsOwner)) {
                    predicates.add(cb.equal(root.get("adsOwner"), adsOwner.trim()));
                }
            }

            if (StringUtils.hasText(ip)) {
                predicates.add(cb.like(cb.lower(root.get("ip")), "%" + ip.trim().toLowerCase(Locale.ROOT) + "%"));
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };

        return toolIpRepository.findAll(specification, pageable);
    }

    public ToolIp update(Long id, ToolIp updateData, Long currentUserId) {
        if (updateData == null) {
            throw new IllegalArgumentException("updateData is required");
        }
        ToolIp existing = toolIpRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TOOL_IP not found: " + id));
        ensureWritable(existing, currentUserId);

        if (updateData.getIp() != null) {
            existing.setIp(updateData.getIp());
        }
        if (updateData.getStartDate() != null) {
            existing.setStartDate(updateData.getStartDate());
        }
        if (updateData.getExpireDate() != null) {
            existing.setExpireDate(updateData.getExpireDate());
        }
        if (updateData.getRemarks() != null) {
            existing.setRemarks(updateData.getRemarks());
        }

        validateAndNormalize(existing);
        existing.setUpdateDate(LocalDateTime.now());
        return toolIpRepository.save(existing);
    }

    public void delete(Long id, Long currentUserId) {
        ToolIp existing = toolIpRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TOOL_IP not found: " + id));
        ensureWritable(existing, currentUserId);
        toolIpRepository.delete(existing);
    }

    @Transactional(readOnly = true)
    public List<String> findAllIpStringsForUser(Long currentUserId) {
        User currentUser = getCurrentUser(currentUserId);
        boolean admin = isAdmin(currentUser);
        if (admin) {
            return toolIpRepository.findAll().stream().map(ToolIp::getIp).toList();
        }
        Specification<ToolIp> spec = (root, query, cb) -> cb.equal(root.get("adsOwner"), currentUser.getUserPhoneNumber());
        return toolIpRepository.findAll(spec).stream().map(ToolIp::getIp).toList();
    }

    private void validateAndNormalize(ToolIp toolIp) {
        if (!StringUtils.hasText(toolIp.getAdsOwner())) {
            throw new IllegalArgumentException("adsOwner is required");
        }
        toolIp.setAdsOwner(toolIp.getAdsOwner().trim());
        userRepository.findByUserPhoneNumber(toolIp.getAdsOwner())
                .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found by phone number: " + toolIp.getAdsOwner()));

        toolIp.setIp(trimToNull(toolIp.getIp()));
        if (!StringUtils.hasText(toolIp.getIp())) {
            throw new IllegalArgumentException("ip is required");
        }
        toolIp.setIp(toolIp.getIp().trim());
        toolIp.setStartDate(toolIp.getStartDate());
        toolIp.setExpireDate(toolIp.getExpireDate());
        toolIp.setRemarks(trimToNull(toolIp.getRemarks()));

        validateLength(toolIp.getIp(), "ip", 64);
        validateLength(toolIp.getRemarks(), "remarks", 128);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private void validateLength(String value, String fieldName, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must be at most " + maxLength + " characters");
        }
    }

    private void ensureReadable(ToolIp toolIp, Long currentUserId) {
        ensureAccess(toolIp, currentUserId, "read");
    }

    private void ensureWritable(ToolIp toolIp, Long currentUserId) {
        ensureAccess(toolIp, currentUserId, "modify");
    }

    private void ensureAccess(ToolIp toolIp, Long currentUserId, String action) {
        User currentUser = getCurrentUser(currentUserId);
        if (!isAdmin(currentUser) && !currentUser.getUserPhoneNumber().equals(toolIp.getAdsOwner())) {
            throw new IllegalArgumentException("Unauthorized: you can only " + action + " your own tool ips");
        }
    }

    private User getCurrentUser(Long currentUserId) {
        return userRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found: " + currentUserId));
    }

    private boolean isAdmin(User user) {
        return user.getUserRole() != null && java.util.Arrays.stream(user.getUserRole().split(",")).map(String::trim).anyMatch(role -> "admin".equalsIgnoreCase(role));
    }
}
