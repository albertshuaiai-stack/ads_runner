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
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JwtTokenService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-seconds:1800}")
    private long expirationSeconds;

    // blacklist to support logout: token -> blockedUntilMillis (Long.MAX_VALUE for permanent)
    private final ConcurrentHashMap<String, Long> blacklist = new ConcurrentHashMap<>();

    private final UserRepository userRepository;
    private final PasswordCryptoService passwordCryptoService;

    public JwtTokenService(UserRepository userRepository, PasswordCryptoService passwordCryptoService) {
        this.userRepository = userRepository;
        this.passwordCryptoService = passwordCryptoService;
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

    // Create token using phone number and password with configured expiration.
    public String createToken(String phone, String password) {
        String key = Base64.getEncoder().encodeToString(secret.getBytes());
        Instant now = Instant.now();
        Date issuedAt = Date.from(now);
        Date expiration = Date.from(now.plusSeconds(expirationSeconds));
        String pwdHash = sha256Hex(passwordCryptoService.decrypt(password));
        return Jwts.builder()
                .setSubject(phone)
                .setIssuedAt(issuedAt)
                .setExpiration(expiration)
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
            Set<String> validHashes = buildValidPasswordHashes(u.getUserPassword());
            if (!validHashes.contains(claimPwdHash)) return null;
            return u.getId();
        } catch (Exception e) {
            return null;
        }
    }

    // Revoke token until its natural expiration.
    public void revokeToken(String token) {
        if (token == null) return;
        try {
            String key = Base64.getEncoder().encodeToString(secret.getBytes());
            Claims claims = Jwts.parser().setSigningKey(key).parseClaimsJws(token).getBody();
            Date expiration = claims.getExpiration();
            long blockedUntil = expiration != null ? expiration.getTime() : Long.MAX_VALUE;
            blacklist.put(token, blockedUntil);
        } catch (Exception e) {
            blacklist.put(token, Long.MAX_VALUE);
        }
    }

    private Set<String> buildValidPasswordHashes(String storedPassword) {
        Set<String> hashes = new LinkedHashSet<>();
        if (storedPassword == null) {
            return hashes;
        }

        hashes.add(sha256Hex(storedPassword));
        try {
            String decryptedPassword = passwordCryptoService.decrypt(storedPassword);
            if (decryptedPassword != null) {
                hashes.add(sha256Hex(decryptedPassword));
            }
        } catch (Exception ignored) {
            // Keep compatibility with tokens created from the stored value even if decrypt fails.
        }
        return hashes;
    }
}
