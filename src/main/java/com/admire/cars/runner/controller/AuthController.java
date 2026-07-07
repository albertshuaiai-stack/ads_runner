package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final com.admire.cars.runner.security.TokenService tokenService;

    public AuthController(UserRepository userRepository, com.admire.cars.runner.security.TokenService tokenService) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest req) {
        Optional<User> userOpt = userRepository.findByUserName(req.getUsername());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = userOpt.get();
        
        if (!"ENABLED".equals(user.getStatus())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        // NOTE: passwords are stored in plain text in DB for this simple example.
        if (!user.getUserPassword().equals(req.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String token = tokenService.createToken(user.getId());
        return ResponseEntity.ok(new LoginResponse(token));
    }

    public static class LoginRequest {
        private String username;
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class LoginResponse {
        private String amToken;

        public LoginResponse(String amToken) { this.amToken = amToken; }
        public String getAmToken() { return amToken; }
        public void setAmToken(String amToken) { this.amToken = amToken; }
    }
}