package com.admire.cars.runner.security;

import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JwtTokenService {

    @Value("${jwt.secret}")
    private String secret;

    // blacklist to support logout: token -> blockedUntilMillis (Long.MAX_VALUE for permanent)
    private final ConcurrentHashMap<String, Long> blacklist = new ConcurrentHashMap<>();

    private final UserRepository userRepository;

    public JwtTokenService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Create token using phone number and password. Token does NOT include expiration.
    public String createToken(String phone, String password) {
        String key = Base64.getEncoder().encodeToString(secret.getBytes());
        Instant now = Instant.now();
        Date issuedAt = Date.from(now);
        String pwdHash = sha256Hex(password);
        return Jwts.builder()
                .setSubject(phone)
                .setIssuedAt(issuedAt)
                .claim("p", pwdHash)
                .signWith(SignatureAlgorithm.HS256, key)
                .compact();
    }

    // Validate token: verify signature, check blacklist, compare claim hash with stored password hash
    public Long validateToken(String token) {
        if (token == null) return null;
        Long blockedUntil = blacklist.get(token);
        if (blockedUntil != null) {
            if (blockedUntil > System.currentTimeMillis()) return null;
            else blacklist.remove(token);
        }
        try {
            String key = Base64.getEncoder().encodeToString(secret.getBytes());
            Claims claims = Jwts.parser().setSigningKey(key).parseClaimsJws(token).getBody();
            String phone = claims.getSubject();
            String claimPwdHash = claims.get("p", String.class);
            if (phone == null || claimPwdHash == null) return null;
            Optional<User> uOpt = userRepository.findByUserPhoneNumber(phone);
            if (uOpt.isEmpty()) return null;
            User u = uOpt.get();
            String storedPwd = u.getUserPassword();
            String storedHash = sha256Hex(storedPwd);
            if (!storedHash.equals(claimPwdHash)) return null;
            return u.getId();
        } catch (Exception e) {
            return null;
        }
    }

    // Revoke token permanently (no expiration tokens)
    public void revokeToken(String token) {
        if (token == null) return;
        // mark as permanently blocked
        blacklist.put(token, Long.MAX_VALUE);
    }
}

