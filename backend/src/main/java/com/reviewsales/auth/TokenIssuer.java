package com.reviewsales.auth;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.reviewsales.config.AppProperties;

import jakarta.servlet.http.HttpServletResponse;

/** 액세스 토큰 발급 + 리프레시 토큰 쿠키(HttpOnly, Path=/api/v1/auth) 설정/삭제 */
@Component
public class TokenIssuer {

    public static final String REFRESH_COOKIE = "refresh_token";
    public static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private final JwtProvider jwtProvider;
    private final boolean secureCookie;

    public TokenIssuer(JwtProvider jwtProvider, AppProperties props) {
        this.jwtProvider = jwtProvider;
        this.secureCookie = props.cookie().secure();
    }

    /** 액세스 토큰을 만들고 리프레시 쿠키를 응답에 심는다. */
    public TokenResponse issue(Long ownerId, HttpServletResponse response) {
        setRefreshCookie(response, jwtProvider.createRefreshToken(ownerId), jwtProvider.refreshTtl());
        return accessOnly(ownerId);
    }

    public TokenResponse accessOnly(Long ownerId) {
        return new TokenResponse(jwtProvider.createAccessToken(ownerId), "Bearer", jwtProvider.accessTtl().toSeconds());
    }

    public void clearRefreshCookie(HttpServletResponse response) {
        setRefreshCookie(response, "", Duration.ZERO);
    }

    private void setRefreshCookie(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(secureCookie)
                .path(REFRESH_COOKIE_PATH)
                .sameSite("Lax")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
    }
}
