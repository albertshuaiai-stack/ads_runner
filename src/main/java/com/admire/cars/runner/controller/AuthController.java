package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.AdsMatrixInfoRepository;
import com.admire.cars.runner.repository.AdsNormalInfoRepository;
import com.admire.cars.runner.repository.UserRepository;
import com.admire.cars.runner.security.PasswordCryptoService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final AdsNormalInfoRepository adsNormalInfoRepository;
    private final AdsMatrixInfoRepository adsMatrixInfoRepository;
    private final com.admire.cars.runner.security.JwtTokenService tokenService;
    private final PasswordCryptoService passwordCryptoService;

    public AuthController(
            UserRepository userRepository,
            AdsNormalInfoRepository adsNormalInfoRepository,
            AdsMatrixInfoRepository adsMatrixInfoRepository,
            com.admire.cars.runner.security.JwtTokenService tokenService,
            PasswordCryptoService passwordCryptoService) {
        this.userRepository = userRepository;
        this.adsNormalInfoRepository = adsNormalInfoRepository;
        this.adsMatrixInfoRepository = adsMatrixInfoRepository;
        this.tokenService = tokenService;
        this.passwordCryptoService = passwordCryptoService;
    }

    @PostMapping("/login")
    @Operation(summary = "Login and get AMtoken", security = {})
    public ResponseEntity<?> login(@RequestBody AuthLoginRequest req) {
        if (req.getLoginId() == null || req.getLoginId().isBlank()) {
            return failure(HttpStatus.UNAUTHORIZED, "loginId is required");
        }
        if (req.getPassword() == null || req.getPassword().isBlank()) {
            return failure(HttpStatus.UNAUTHORIZED, "password is required");
        }

        String loginId = req.getLoginId().trim();
        Optional<User> userOpt = userRepository.findByUserEmail(loginId);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByUserPhoneNumber(loginId);
        }
        if (userOpt.isEmpty()) {
            return failure(HttpStatus.UNAUTHORIZED, "User not found with provided email or phone number");
        }
        User user = userOpt.get();

        if (!"ENABLED".equalsIgnoreCase(user.getStatus())) {
            return failure(HttpStatus.UNAUTHORIZED, "User is disabled");
        }

        if (!passwordCryptoService.matches(req.getPassword(), user.getUserPassword())) {
            return failure(HttpStatus.UNAUTHORIZED, "Password is incorrect");
        }
        Long normalAdsTotalCount = adsNormalInfoRepository.countByAdsOwner(user.getUserPhoneNumber());
        Long matrixAdsTotalCount = adsMatrixInfoRepository.countByAdsOwner(user.getUserPhoneNumber());
        String token = tokenService.createToken(user.getUserPhoneNumber(), user.getUserPassword());
        return ResponseEntity.ok(new AuthLoginResponse(
                token,
                user.getExpireDate(),
                user.getUserName(),
                user.getUserRole(),
                normalizeRoles(user.getUserRole()),
                normalAdsTotalCount == null ? 0L : normalAdsTotalCount,
                matrixAdsTotalCount == null ? 0L : matrixAdsTotalCount));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @org.springframework.web.bind.annotation.RequestHeader(value = "AMtoken", required = false) String amToken,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = null;
        if (amToken != null && !amToken.isBlank()) token = amToken;
        else if (authHeader != null && authHeader.startsWith("Bearer ")) token = authHeader.substring(7);

        if (token != null) {
            tokenService.revokeToken(token);
        }
        return ResponseEntity.ok().build();
    }

    private String normalizeRoles(String userRole) {
        if (userRole == null || userRole.isBlank()) {
            return "";
        }
        return Arrays.stream(userRole.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private ResponseEntity<Map<String, Object>> failure(HttpStatus status, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", message);
        return ResponseEntity.status(status).body(response);
    }
}