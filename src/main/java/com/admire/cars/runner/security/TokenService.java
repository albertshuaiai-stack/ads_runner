package com.admire.cars.runner.security;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TokenService {
    private final ConcurrentHashMap<String, Long> tokens = new ConcurrentHashMap<>();

    public String createToken(Long userId) {
        String token = UUID.randomUUID().toString();
        tokens.put(token, userId);
        return token;
    }

    public Long validateToken(String token) {
        return tokens.get(token);
    }

    public void revokeToken(String token) {
        tokens.remove(token);
    }
}
