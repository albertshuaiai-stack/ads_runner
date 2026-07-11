package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.ToolEmail;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.ToolEmailRepository;
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
public class ToolEmailService {

    private final ToolEmailRepository toolEmailRepository;
    private final UserRepository userRepository;

    public ToolEmailService(ToolEmailRepository toolEmailRepository, UserRepository userRepository) {
        this.toolEmailRepository = toolEmailRepository;
        this.userRepository = userRepository;
    }

    public ToolEmail create(ToolEmail toolEmail, Long currentUserId) {
        if (toolEmail == null) {
            throw new IllegalArgumentException("TOOL_EMAL is required");
        }
        User currentUser = getCurrentUser(currentUserId);
        toolEmail.setAdsOwner(currentUser.getUserPhoneNumber());
        validateAndNormalize(toolEmail);
        toolEmail.setCreateDate(LocalDateTime.now());
        return toolEmailRepository.save(toolEmail);
    }

    @Transactional(readOnly = true)
    public ToolEmail getById(Long id, Long currentUserId) {
        ToolEmail toolEmail = toolEmailRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TOOL_EMAL not found: " + id));
        ensureReadable(toolEmail, currentUserId);
        return toolEmail;
    }

    @Transactional(readOnly = true)
    public Page<ToolEmail> search(String userName, String emailAddress, Long currentUserId, Pageable pageable) {
        User currentUser = getCurrentUser(currentUserId);
        boolean admin = isAdmin(currentUser);
        Specification<ToolEmail> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (!admin) {
                predicates.add(criteriaBuilder.equal(root.get("adsOwner"), currentUser.getUserPhoneNumber()));
            }
            if (StringUtils.hasText(userName)) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("userName")),
                        "%" + userName.trim().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(emailAddress)) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("emailAddress")),
                        "%" + emailAddress.trim().toLowerCase() + "%"));
            }

            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return toolEmailRepository.findAll(specification, pageable);
    }

    public ToolEmail update(Long id, ToolEmail updateData, Long currentUserId) {
        if (updateData == null) {
            throw new IllegalArgumentException("updateData is required");
        }
        ToolEmail existing = toolEmailRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TOOL_EMAL not found: " + id));
        ensureWritable(existing, currentUserId);

        if (updateData.getUserName() != null) {
            existing.setUserName(updateData.getUserName());
        }
        if (updateData.getBirthdayDate() != null) {
            existing.setBirthdayDate(updateData.getBirthdayDate());
        }
        if (updateData.getEmailAddress() != null) {
            existing.setEmailAddress(updateData.getEmailAddress());
        }
        if (updateData.getEmailPwd() != null) {
            existing.setEmailPwd(updateData.getEmailPwd());
        }
        if (updateData.getParentEmail() != null) {
            existing.setParentEmail(updateData.getParentEmail());
        }
        if (updateData.getAddress() != null) {
            existing.setAddress(updateData.getAddress());
        }
        if (updateData.getRemarks() != null) {
            existing.setRemarks(updateData.getRemarks());
        }

        validateAndNormalize(existing);
        existing.setUpdateDate(LocalDateTime.now());
        return toolEmailRepository.save(existing);
    }

    public void delete(Long id, Long currentUserId) {
        ToolEmail existing = toolEmailRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TOOL_EMAL not found: " + id));
        ensureWritable(existing, currentUserId);
        toolEmailRepository.delete(existing);
    }

    private void validateAndNormalize(ToolEmail toolEmail) {
        if (!StringUtils.hasText(toolEmail.getAdsOwner())) {
            throw new IllegalArgumentException("adsOwner is required");
        }
        User owner = userRepository.findByUserPhoneNumber(toolEmail.getAdsOwner().trim())
                .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found by phone number: " + toolEmail.getAdsOwner()));
        toolEmail.setAdsOwner(owner.getUserPhoneNumber());

        toolEmail.setUserName(trimToNull(toolEmail.getUserName()));
        toolEmail.setEmailAddress(trimToNull(toolEmail.getEmailAddress()));
        toolEmail.setEmailPwd(trimToNull(toolEmail.getEmailPwd()));
        toolEmail.setParentEmail(trimToNull(toolEmail.getParentEmail()));
        toolEmail.setAddress(trimToNull(toolEmail.getAddress()));
        toolEmail.setRemarks(trimToNull(toolEmail.getRemarks()));

        validateLength(toolEmail.getUserName(), "userName", 16);
        validateLength(toolEmail.getEmailAddress(), "emailAddress", 64);
        validateLength(toolEmail.getEmailPwd(), "emailPwd", 64);
        validateLength(toolEmail.getParentEmail(), "parentEmail", 64);
        validateLength(toolEmail.getAddress(), "address", 128);
        validateLength(toolEmail.getRemarks(), "remarks", 64);
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

    private void ensureReadable(ToolEmail toolEmail, Long currentUserId) {
        ensureAccess(toolEmail, currentUserId, "read");
    }

    private void ensureWritable(ToolEmail toolEmail, Long currentUserId) {
        ensureAccess(toolEmail, currentUserId, "modify");
    }

    private void ensureAccess(ToolEmail toolEmail, Long currentUserId, String action) {
        User currentUser = getCurrentUser(currentUserId);
        if (!isAdmin(currentUser) && !currentUser.getUserPhoneNumber().equals(toolEmail.getAdsOwner())) {
            throw new IllegalArgumentException("Unauthorized: you can only " + action + " your own tool emails");
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
