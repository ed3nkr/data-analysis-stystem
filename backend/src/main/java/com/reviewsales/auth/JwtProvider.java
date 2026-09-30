package com.reviewsales.auth;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import com.reviewsales.common.BusinessException;
import com.reviewsales.common.ErrorCode;
import com.reviewsales.config.AppProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** HS256 JWT 발급/검증. 비밀키는 JWT_SECRET 환경변수로만 받는다. */
@Component
public class JwtProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtProvider.class);

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";
    public static final String TYPE_OAUTH_STATE = "oauth_state";
    private static final String CLAIM_TYPE = "typ";

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtProvider(AppProperties props, Environment env) {
        String secret = props.jwt().secret();
        if (secret == null || secret.isBlank()) {
            if (!env.acceptsProfiles(Profiles.of("local", "test"))) {
                throw new IllegalStateException("JWT_SECRET 환경변수가 필요합니다.");
            }
            byte[] random = new byte[48];
            new SecureRandom().nextBytes(random);
            secret = Base64.getEncoder().encodeToString(random);
            log.warn("JWT_SECRET 이 없어 임시 키를 생성했습니다. 재시작하면 기존 토큰은 무효가 됩니다.");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET 은 32바이트 이상이어야 합니다.");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.accessTtl = props.jwt().accessTtl();
        this.refreshTtl = props.jwt().refreshTtl();
    }

    public String createAccessToken(Long ownerId) {
        return create(String.valueOf(ownerId), TYPE_ACCESS, accessTtl, Map.of());
    }

    public String createRefreshToken(Long ownerId) {
        return create(String.valueOf(ownerId), TYPE_REFRESH, refreshTtl, Map.of());
    }

    public String createOAuthState(String provider, String nonce) {
        return create(provider, TYPE_OAUTH_STATE, Duration.ofMinutes(10), Map.of("nonce", nonce));
    }

    public Duration accessTtl() {
        return accessTtl;
    }

    public Duration refreshTtl() {
        return refreshTtl;
    }

    /** 토큰을 검증하고 claims 를 돌려준다. 만료 → TOKEN_EXPIRED, 그 외 → AUTH_REQUIRED */
    public Claims parse(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            if (!expectedType.equals(claims.get(CLAIM_TYPE, String.class))) {
                throw new BusinessException(ErrorCode.AUTH_REQUIRED, "토큰 종류가 올바르지 않습니다.");
            }
            return claims;
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED, "유효하지 않은 토큰입니다.");
        }
    }

    private String create(String subject, String type, Duration ttl, Map<String, Object> extra) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claim(CLAIM_TYPE, type)
                .claims(extra)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
