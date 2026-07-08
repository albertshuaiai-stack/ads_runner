package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.entity.UserAudit;
import com.admire.cars.runner.repository.UserRepository;
import com.admire.cars.runner.repository.UserAuditRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final UserAuditRepository auditRepository;

    @Autowired
    public UserService(UserRepository userRepository, UserAuditRepository auditRepository) {
        this.userRepository = userRepository;
        this.auditRepository = auditRepository;
    }

    public User registerUser(User user) {
        if (userRepository.findByUserName(user.getUserName()).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepository.findByUserEmail(user.getUserEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already exists");
        }
        if (userRepository.findByUserPhoneNumber(user.getUserPhoneNumber()).isPresent()) {
            throw new IllegalArgumentException("Phone number already exists");
        }

        user.setStatus("ENABLED");
        user.setCreateDate(LocalDateTime.now());
        if (user.getUserRole() == null) {
            user.setUserRole("USER");
        }
        if (user.getNormalAdsNumber() == null) {
            user.setNormalAdsNumber(0L);
        }
        if (user.getMatrixAdsNumber() == null) {
            user.setMatrixAdsNumber(0L);
        }
        User savedUser = userRepository.save(user);

        createAudit(savedUser.getId(), "CREATE", null, describeUser(savedUser), "SYSTEM");
        return savedUser;
    }

    public User updateUser(Long userId, User updateData) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }

        User user = userOpt.get();
        String oldValue = describeUser(user);

        if (updateData.getUserName() != null && !updateData.getUserName().equals(user.getUserName())) {
            if (userRepository.findByUserName(updateData.getUserName()).isPresent()) {
                throw new IllegalArgumentException("Username already exists");
            }
            user.setUserName(updateData.getUserName());
        }

        if (updateData.getUserEmail() != null && !updateData.getUserEmail().equals(user.getUserEmail())) {
            if (userRepository.findByUserEmail(updateData.getUserEmail()).isPresent()) {
                throw new IllegalArgumentException("Email already exists");
            }
            user.setUserEmail(updateData.getUserEmail());
        }

        if (updateData.getUserPhoneNumber() != null && !updateData.getUserPhoneNumber().equals(user.getUserPhoneNumber())) {
            if (userRepository.findByUserPhoneNumber(updateData.getUserPhoneNumber()).isPresent()) {
                throw new IllegalArgumentException("Phone number already exists");
            }
            user.setUserPhoneNumber(updateData.getUserPhoneNumber());
        }

        if (updateData.getUserPassword() != null) {
            user.setUserPassword(updateData.getUserPassword());
        }

        if (updateData.getUserRole() != null) {
            user.setUserRole(updateData.getUserRole());
        }

        if (updateData.getExpireDate() != null) {
            user.setExpireDate(updateData.getExpireDate());
        }

        if (updateData.getNormalAdsNumber() != null) {
            user.setNormalAdsNumber(updateData.getNormalAdsNumber());
        }

        if (updateData.getMatrixAdsNumber() != null) {
            user.setMatrixAdsNumber(updateData.getMatrixAdsNumber());
        }

        if (updateData.getStatus() != null) {
            user.setStatus(updateData.getStatus());
        }

        user.setUpdateDate(LocalDateTime.now());
        User updatedUser = userRepository.save(user);

        createAudit(userId, "UPDATE", oldValue, describeUser(updatedUser), "SYSTEM");
        return updatedUser;
    }

    public void deleteUser(Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }

        User user = userOpt.get();
        createAudit(userId, "DELETE", describeUser(user), null, "SYSTEM");
        userRepository.delete(user);
    }

    public void enableUser(Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }

        User user = userOpt.get();
        String oldStatus = user.getStatus();
        user.setStatus("ENABLED");
        user.setUpdateDate(LocalDateTime.now());
        userRepository.save(user);

        createAudit(userId, "ENABLE", "STATUS: " + oldStatus, "STATUS: ENABLED", "SYSTEM");
    }

    public void disableUser(Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }

        User user = userOpt.get();
        String oldStatus = user.getStatus();
        user.setStatus("DISABLED");
        user.setUpdateDate(LocalDateTime.now());
        userRepository.save(user);

        createAudit(userId, "DISABLE", "STATUS: " + oldStatus, "STATUS: DISABLED", "SYSTEM");
    }

    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Page<User> searchUsers(String phoneNumber, String email, String userName, Pageable pageable) {
        Specification<User> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(phoneNumber)) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("userPhoneNumber")),
                        "%" + phoneNumber.toLowerCase() + "%"));
            }
            if (StringUtils.hasText(email)) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("userEmail")),
                        "%" + email.toLowerCase() + "%"));
            }
            if (StringUtils.hasText(userName)) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("userName")),
                        "%" + userName.toLowerCase() + "%"));
            }

            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        return userRepository.findAll(specification, pageable);
    }

    public List<User> getEnabledUsers() {
        return userRepository.findByStatus("ENABLED");
    }

    public List<UserAudit> getUserAuditHistory(Long userId) {
        return auditRepository.findByUserIdOrderByOperationDateDesc(userId);
    }

    private void createAudit(Long userId, String operation, String oldValue, String newValue, String operator) {
        UserAudit audit = new UserAudit();
        audit.setUserId(userId);
        audit.setOperation(operation);
        audit.setOldValue(oldValue);
        audit.setNewValue(newValue);
        audit.setOperator(operator);
        audit.setOperationDate(LocalDateTime.now());
        auditRepository.save(audit);
    }

    private String describeUser(User user) {
        return "User{"
                + "id=" + user.getId()
                + ", userName='" + user.getUserName() + '\''
                + ", userEmail='" + user.getUserEmail() + '\''
                + ", userPhoneNumber='" + user.getUserPhoneNumber() + '\''
                + ", userRole='" + user.getUserRole() + '\''
                + ", expireDate=" + user.getExpireDate()
                + ", normalAdsNumber=" + user.getNormalAdsNumber()
                + ", matrixAdsNumber=" + user.getMatrixAdsNumber()
                + ", status='" + user.getStatus() + '\''
                + ", createDate=" + user.getCreateDate()
                + ", updateDate=" + user.getUpdateDate()
                + '}';
    }
}
