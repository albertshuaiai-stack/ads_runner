package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.entity.UserAudit;
import com.admire.cars.runner.repository.UserRepository;
import com.admire.cars.runner.repository.UserAuditRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        User savedUser = userRepository.save(user);

        createAudit(savedUser.getId(), "CREATE", null, user.toString(), "SYSTEM");
        return savedUser;
    }

    public User updateUser(Long userId, User updateData) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }

        User user = userOpt.get();
        String oldValue = user.toString();

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

        user.setUpdateDate(LocalDateTime.now());
        User updatedUser = userRepository.save(user);

        createAudit(userId, "UPDATE", oldValue, updatedUser.toString(), "SYSTEM");
        return updatedUser;
    }

    public void deleteUser(Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }

        User user = userOpt.get();
        createAudit(userId, "DELETE", user.toString(), null, "SYSTEM");
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
}
