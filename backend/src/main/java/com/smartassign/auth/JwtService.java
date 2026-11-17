package com.smartassign.auth;

import com.smartassign.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Issues and validates HS256 JWTs. The signing secret comes from SMARTASSIGN_JWT_SECRET (>= 32 bytes).
 * If it is not set, a random secret is generated for this process only (tokens stop working after a restart).
 * Logout revokes the token id (jti) in memory until the token's natural expiry.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey key;
    private final String issuer;
    private final long expirationMinutes;
    private final Map<String, Instant> revoked = new ConcurrentHashMap<>();

    public JwtService(AppProperties props) {
        String secret = props.jwt() == null ? null : props.jwt().secret();
        if (secret == null || secret.isBlank()) {
            byte[] random = new byte[48];
            new SecureRandom().nextBytes(random);
            secret = Base64.getEncoder().encodeToString(random);
            log.warn("SMARTASSIGN_JWT_SECRET is not set - using a random per-process secret. "
                    + "Set it to a value of at least 32 characters for any shared environment.");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("SMARTASSIGN_JWT_SECRET must be at least 32 bytes long");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.issuer = props.jwt() == null || props.jwt().issuer() == null ? "smartassign" : props.jwt().issuer();
        this.expirationMinutes = props.jwt() == null || props.jwt().expirationMinutes() <= 0 ? 480 : props.jwt().expirationMinutes();
    }

    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        Instant exp = now.plus(expirationMinutes, ChronoUnit.MINUTES);
        String token = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getUsername())
                .issuer(issuer)
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
        return new IssuedToken(token, exp);
    }

    /** Returns the validated claims, or empty if the token is invalid, expired or revoked. */
    public Optional<Claims> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).requireIssuer(issuer).build()
                    .parseSignedClaims(token).getPayload();
            if (claims.getId() != null && revoked.containsKey(claims.getId())) {
                return Optional.empty();
            }
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public void revoke(String token) {
        parse(token).ifPresent(c -> {
            if (c.getId() != null) {
                revoked.put(c.getId(), c.getExpiration().toInstant());
            }
        });
        Instant now = Instant.now();
        revoked.entrySet().removeIf(e -> e.getValue().isBefore(now));
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }
}
