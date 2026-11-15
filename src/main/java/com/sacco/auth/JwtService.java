package com.sacco.auth;

import com.sacco.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Issues and validates signed JWT access tokens. The signing key is derived
 * from {@code sacco.security.jwt-secret}; in production this must be a
 * high-entropy secret provided via environment variable.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final long EXPIRATION_HOURS = 12;

    private final SecretKey key;

    public JwtService(@Value("${sacco.security.jwt-secret:}") String secret) {
        String effective = secret;
        if (effective == null || effective.isBlank()) {
            byte[] random = new byte[48];
            new SecureRandom().nextBytes(random);
            effective = Base64.getEncoder().encodeToString(random);
            log.warn("sacco.security.jwt-secret is not configured; using an ephemeral signing key. "
                    + "Set SACCO_JWT_SECRET to keep sessions valid across restarts.");
        }

        // Pad/truncate to a stable length; jjwt requires >= 256 bits for HS256.
        String padded = effective;
        while (padded.length() < 32) {
            padded = padded + padded;
        }
        if (padded.length() > 64) {
            padded = padded.substring(0, 64);
        }
        this.key = Keys.hmacShaKeyFor(padded.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getUserId());
        claims.put("username", user.getUsername());
        claims.put("role", user.getRole());
        claims.put("memberId", user.getMemberId());
        claims.put("passwordResetRequired", Boolean.TRUE.equals(user.getPasswordResetRequired()));

        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(EXPIRATION_HOURS * 3600);

        return Jwts.builder()
                .claims(claims)
                .subject(user.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            Claims claims = parseClaims(token);
            return claims.getExpiration() != null && claims.getExpiration().after(new Date());
        } catch (Exception ex) {
            return false;
        }
    }

    public Long getUserId(Claims claims) {
        Object v = claims.get("userId");
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) return Long.parseLong(s);
        return null;
    }

    public String getUsername(Claims claims) {
        return claims.get("username", String.class);
    }

    public String getRole(Claims claims) {
        return claims.get("role", String.class);
    }

    public Long getMemberId(Claims claims) {
        Object v = claims.get("memberId");
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }
}
