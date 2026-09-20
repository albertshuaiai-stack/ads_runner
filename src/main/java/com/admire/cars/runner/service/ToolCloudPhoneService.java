package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.ToolCloudPhone;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.ToolCloudPhoneRepository;
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
public class ToolCloudPhoneService {

    private final ToolCloudPhoneRepository toolCloudPhoneRepository;
    private final UserRepository userRepository;

    public ToolCloudPhoneService(ToolCloudPhoneRepository toolCloudPhoneRepository, UserRepository userRepository) {
        this.toolCloudPhoneRepository = toolCloudPhoneRepository;
        this.userRepository = userRepository;
    }

    public ToolCloudPhone create(ToolCloudPhone phone, Long currentUserId) {
        if (phone == null) throw new IllegalArgumentException("TOOL_CLOUD_PHONE is required");
        User currentUser = getCurrentUser(currentUserId);
        phone.setAdsOwner(currentUser.getUserPhoneNumber());
        validateAndNormalize(phone);
        phone.setCreateDate(LocalDateTime.now());
        return toolCloudPhoneRepository.save(phone);
    }

    @Transactional(readOnly = true)
    public ToolCloudPhone getById(Long id, Long currentUserId) {
        ToolCloudPhone phone = toolCloudPhoneRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TOOL_CLOUD_PHONE not found: " + id));
        ensureReadable(phone, currentUserId);
        return phone;
    }

    @Transactional(readOnly = true)
    public Page<ToolCloudPhone> search(String countryCd, String adsOwner, Long currentUserId, Pageable pageable) {
        User currentUser = getCurrentUser(currentUserId);
        boolean admin = isAdmin(currentUser);
        Specification<ToolCloudPhone> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!admin) {
                predicates.add(cb.equal(root.get("adsOwner"), currentUser.getUserPhoneNumber()));
            } else {
                if (StringUtils.hasText(adsOwner)) {
                    predicates.add(cb.equal(root.get("adsOwner"), adsOwner.trim()));
                }
            }
            if (StringUtils.hasText(countryCd)) {
                predicates.add(cb.equal(cb.lower(root.get("countryCd")), countryCd.trim().toLowerCase()));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
        return toolCloudPhoneRepository.findAll(spec, pageable);
    }

    public ToolCloudPhone update(Long id, ToolCloudPhone updateData, Long currentUserId) {
        if (updateData == null) throw new IllegalArgumentException("updateData is required");
        ToolCloudPhone existing = toolCloudPhoneRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TOOL_CLOUD_PHONE not found: " + id));
        ensureWritable(existing, currentUserId);

        if (updateData.getCountryCd() != null) existing.setCountryCd(updateData.getCountryCd());
        if (updateData.getPhoneNumber() != null) existing.setPhoneNumber(updateData.getPhoneNumber());
        if (updateData.getStartDate() != null) existing.setStartDate(updateData.getStartDate());
        if (updateData.getExpireDate() != null) existing.setExpireDate(updateData.getExpireDate());
        if (updateData.getRemarks() != null) existing.setRemarks(updateData.getRemarks());

        validateAndNormalize(existing);
        existing.setUpdateDate(LocalDateTime.now());
        return toolCloudPhoneRepository.save(existing);
    }

    public void delete(Long id, Long currentUserId) {
        ToolCloudPhone existing = toolCloudPhoneRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TOOL_CLOUD_PHONE not found: " + id));
        ensureWritable(existing, currentUserId);
        toolCloudPhoneRepository.delete(existing);
    }

    private void validateAndNormalize(ToolCloudPhone phone) {
        if (!StringUtils.hasText(phone.getPhoneNumber())) {
            throw new IllegalArgumentException("phoneNumber is required");
        }
        phone.setPhoneNumber(phone.getPhoneNumber().trim());
        phone.setCountryCd(trimToNull(phone.getCountryCd()));
        phone.setRemarks(trimToNull(phone.getRemarks()));
        phone.setAdsOwner(trimToNull(phone.getAdsOwner()));

        if (!toolCloudPhoneRepository.existsByPhoneNumberIgnoreCase(phone.getPhoneNumber())) {
            // when creating, repository check will be true only if phone exists; we allow duplicates? keeping simple: allow
        }

        validateLength(phone.getCountryCd(), "countryCd", 32);
        validateLength(phone.getPhoneNumber(), "phoneNumber", 64);
        validateLength(phone.getRemarks(), "remarks", 128);
        validateLength(phone.getAdsOwner(), "adsOwner", 64);
    }

    @Transactional(readOnly = true)
    public List<String> findAllPhoneNumbers(Long currentUserId) {
        User currentUser = getCurrentUser(currentUserId);
        boolean admin = isAdmin(currentUser);
        List<ToolCloudPhone> list;
        if (admin) {
            list = toolCloudPhoneRepository.findAll();
        } else {
            Specification<ToolCloudPhone> spec = (root, query, cb) -> cb.equal(root.get("adsOwner"), currentUser.getUserPhoneNumber());
            list = toolCloudPhoneRepository.findAll(spec);
        }
        List<String> phones = new java.util.ArrayList<>();
        for (ToolCloudPhone p : list) {
            if (p.getPhoneNumber() != null) phones.add(p.getPhoneNumber());
        }
        return phones;
    }

    private void ensureReadable(ToolCloudPhone phone, Long currentUserId) {
        ensureAccess(phone, currentUserId, "read");
    }

    private void ensureWritable(ToolCloudPhone phone, Long currentUserId) {
        ensureAccess(phone, currentUserId, "modify");
    }

    private void ensureAccess(ToolCloudPhone phone, Long currentUserId, String action) {
        User currentUser = getCurrentUser(currentUserId);
        if (!isAdmin(currentUser) && !currentUser.getUserPhoneNumber().equals(phone.getAdsOwner())) {
            throw new IllegalArgumentException("Unauthorized: you can only " + action + " your own cloud phones");
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

    private String trimToNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private void validateLength(String value, String fieldName, int max) {
        if (value != null && value.length() > max) throw new IllegalArgumentException(fieldName + " must be at most " + max + " characters");
    }
}
