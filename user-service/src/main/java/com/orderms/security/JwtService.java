package com.orderms.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * JWT Service responsible for:
 * <ul>
 *   <li>Generating access tokens and refresh tokens</li>
 *   <li>Extracting claims from tokens</li>
 *   <li>Validating tokens (signature, expiry)</li>
 *   <li>Blacklisting tokens on logout via Redis</li>
 * </ul>
 */
@Slf4j
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiry-ms}")
    private long accessTokenExpiryMs;

    @Value("${jwt.refresh-token-expiry-ms}")
    private long refreshTokenExpiryMs;

    @Value("${redis.prefix.token-blacklist}")
    private String blacklistPrefix;

    private final StringRedisTemplate redisTemplate;

    public JwtService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ── Token generation ──────────────────────────────────────────────────────

    public String generateAccessToken(UserDetails userDetails) {
        return generateAccessToken(new HashMap<>(), userDetails);
    }

    public String generateAccessToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return buildToken(extraClaims, userDetails, accessTokenExpiryMs, "ACCESS");
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return buildToken(new HashMap<>(), userDetails, refreshTokenExpiryMs, "REFRESH");
    }

    private String buildToken(Map<String, Object> extraClaims, UserDetails userDetails,
                               long expiryMs, String tokenType) {
        Date now    = new Date(System.currentTimeMillis());
        Date expiry = new Date(System.currentTimeMillis() + expiryMs);

        Map<String, Object> claims = new HashMap<>(extraClaims);
        claims.put("type", tokenType);
        claims.put("authorities", userDetails.getAuthorities());

        return Jwts.builder()
                .claims(claims)
                .subject(userDetails.getUsername())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    // ── Claims extraction ─────────────────────────────────────────────────────

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractTokenId(String token) {
        return extractClaim(token, Claims::getId);
    }

    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(extractAllClaims(token));
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(
                java.util.Base64.getEncoder().encodeToString(jwtSecret.getBytes()));
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // ── Validation ────────────────────────────────────────────────────────────

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            String username = extractUsername(token);
            return username.equals(userDetails.getUsername())
                    && !isTokenExpired(token)
                    && !isBlacklisted(token);
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("JWT validation failed: {}", ex.getMessage());
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    // ── Blacklisting (logout) ─────────────────────────────────────────────────

    /**
     * Blacklist a token in Redis until it naturally expires.
     * On logout, the access token's JTI is stored so it is rejected on subsequent requests.
     */
    public void blacklistToken(String token) {
        try {
            String jti         = extractTokenId(token);
            Date   expiration  = extractClaim(token, Claims::getExpiration);
            long   ttlSeconds  = (expiration.getTime() - System.currentTimeMillis()) / 1000;
            if (ttlSeconds > 0) {
                redisTemplate.opsForValue()
                        .set(blacklistPrefix + jti, "true", Duration.ofSeconds(ttlSeconds));
                log.debug("Token JTI {} blacklisted for {} seconds", jti, ttlSeconds);
            }
        } catch (JwtException ex) {
            log.warn("Could not blacklist token: {}", ex.getMessage());
        }
    }

    public boolean isBlacklisted(String token) {
        try {
            String jti = extractTokenId(token);
            return Boolean.TRUE.toString().equals(
                    redisTemplate.opsForValue().get(blacklistPrefix + jti));
        } catch (Exception ex) {
            return false;
        }
    }
}
