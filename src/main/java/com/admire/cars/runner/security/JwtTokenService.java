package com.admire.cars.runner.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JwtTokenService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-seconds}")
    private long expirationSeconds;

    // blacklist to support logout: token -> expiryEpochMillis
    private final ConcurrentHashMap<String, Long> blacklist = new ConcurrentHashMap<>();

    public String createToken(Long userId) {
        String key = Base64.getEncoder().encodeToString(secret.getBytes());
        Instant now = Instant.now();
        Date issuedAt = Date.from(now);
        Date exp = Date.from(now.plusSeconds(expirationSeconds));
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setIssuedAt(issuedAt)
                .setExpiration(exp)
                .signWith(SignatureAlgorithm.HS256, key)
                .compact();
    }

    public Long validateToken(String token) {
        if (token == null) return null;
        // check blacklist
        Long blacklistedUntil = blacklist.get(token);
        if (blacklistedUntil != null) {
            if (blacklistedUntil > System.currentTimeMillis()) return null;
            else blacklist.remove(token);
        }
        try {
            String key = Base64.getEncoder().encodeToString(secret.getBytes());
            Claims claims = Jwts.parser().setSigningKey(key).parseClaimsJws(token).getBody();
            String subj = claims.getSubject();
            return Long.valueOf(subj);
        } catch (Exception e) {
            return null;
        }
    }

    public void revokeToken(String token) {
        if (token == null) return;
        try {
            String key = Base64.getEncoder().encodeToString(secret.getBytes());
            Claims claims = Jwts.parser().setSigningKey(key).parseClaimsJws(token).getBody();
            Date exp = claims.getExpiration();
            blacklist.put(token, exp.getTime());
        } catch (Exception ignored) {
        }
    }
}
