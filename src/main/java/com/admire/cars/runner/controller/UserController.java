package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.entity.UserAudit;
import com.admire.cars.runner.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    private Long getCurrentUserId(HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        if (uid == null) {
            throw new IllegalArgumentException("userId not found in request");
        }
        return (Long) uid;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user", security = {})
    public ResponseEntity<Map<String, Object>> registerUser(@RequestBody UserRequest request) {
        try {
            User user = request.toUser();
            User registeredUser = userService.registerUser(user);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "User registered successfully");
            response.put("userId", registeredUser.getId());
            response.put("apiKey", registeredUser.getApiKey());
            response.put("user", UserResponse.from(registeredUser));
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long userId) {
        try {
            User user = userService.getUserById(userId);
            return ResponseEntity.ok(UserResponse.from(user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping
    @Operation(summary = "Query users with pagination")
    public ResponseEntity<Page<UserResponse>> queryUsers(
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String userName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        Page<User> users = userService.searchUsers(
                phoneNumber,
                email,
                userName,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id")));
        List<UserResponse> content = users.getContent().stream()
                .map(UserResponse::from)
                .toList();
        return ResponseEntity.ok(new PageImpl<>(content, users.getPageable(), users.getTotalElements()));
    }

    @GetMapping("/all")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<User> users = userService.getAllUsers();
        return ResponseEntity.ok(users.stream().map(UserResponse::from).toList());
    }

    @GetMapping("/enabled")
    public ResponseEntity<List<UserResponse>> getEnabledUsers() {
        List<User> users = userService.getEnabledUsers();
        return ResponseEntity.ok(users.stream().map(UserResponse::from).toList());
    }

    @PutMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> updateUser(@PathVariable Long userId, @RequestBody UserRequest request) {
        try {
            User updateData = request.toUser();
            User updatedUser = userService.updateUser(userId, updateData);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "User updated successfully");
            response.put("user", UserResponse.from(updatedUser));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(@RequestBody ChangePasswordRequest request, HttpServletRequest httpRequest) {
        try {
            Long userId = getCurrentUserId(httpRequest);
            userService.changePassword(userId, request.getOldPassword(), request.getNewPassword());
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Password changed successfully");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable Long userId) {
        try {
            userService.deleteUser(userId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "User deleted successfully");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @PostMapping("/{userId}/enable")
    public ResponseEntity<Map<String, Object>> enableUser(@PathVariable Long userId) {
        try {
            userService.enableUser(userId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "User enabled successfully");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @PostMapping("/{userId}/disable")
    public ResponseEntity<Map<String, Object>> disableUser(@PathVariable Long userId) {
        try {
            userService.disableUser(userId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "User disabled successfully");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @GetMapping("/{userId}/audit")
    public ResponseEntity<Map<String, Object>> getUserAuditHistory(@PathVariable Long userId) {
        try {
            userService.getUserById(userId);
            List<UserAudit> auditHistory = userService.getUserAuditHistory(userId);
            Map<String, Object> response = new HashMap<>();
            response.put("userId", userId);
            response.put("auditHistory", auditHistory);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    public static class UserRequest {
        private String userName;
        private String userEmail;
        private String userPhoneNumber;
        private String userRole;
        private String userPassword;
        private String expireDate;
        private Long normalAdsNumber;
        private Long matrixAdsNumber;
        private String status;

        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
        public String getUserEmail() { return userEmail; }
        public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
        public String getUserPhoneNumber() { return userPhoneNumber; }
        public void setUserPhoneNumber(String userPhoneNumber) { this.userPhoneNumber = userPhoneNumber; }
        public String getUserRole() { return userRole; }
        public void setUserRole(String userRole) { this.userRole = userRole; }
        public String getUserPassword() { return userPassword; }
        public void setUserPassword(String userPassword) { this.userPassword = userPassword; }
        public String getExpireDate() { return expireDate; }
        public void setExpireDate(String expireDate) { this.expireDate = expireDate; }
        public Long getNormalAdsNumber() { return normalAdsNumber; }
        public void setNormalAdsNumber(Long normalAdsNumber) { this.normalAdsNumber = normalAdsNumber; }
        public Long getMatrixAdsNumber() { return matrixAdsNumber; }
        public void setMatrixAdsNumber(Long matrixAdsNumber) { this.matrixAdsNumber = matrixAdsNumber; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public User toUser() {
            User user = new User();
            user.setUserName(userName);
            user.setUserEmail(userEmail);
            user.setUserPhoneNumber(userPhoneNumber);
            user.setUserRole(userRole);
            user.setUserPassword(userPassword);
            user.setExpireDate(parseExpireDate(expireDate));
            user.setNormalAdsNumber(normalAdsNumber);
            user.setMatrixAdsNumber(matrixAdsNumber);
            user.setStatus(status);
            return user;
        }
    }

    public static class ChangePasswordRequest {
        private String oldPassword;
        private String newPassword;

        public String getOldPassword() { return oldPassword; }
        public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }

    private static java.time.LocalDateTime parseExpireDate(String expireDate) {
        if (expireDate == null || expireDate.isBlank()) {
            return null;
        }

        try {
            return java.time.LocalDateTime.parse(expireDate);
        } catch (DateTimeParseException ex) {
            return LocalDate.parse(expireDate).atStartOfDay();
        }
    }
}
