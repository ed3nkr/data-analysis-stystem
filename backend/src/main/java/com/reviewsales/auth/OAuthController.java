package com.reviewsales.auth;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.reviewsales.config.AppProperties;
import com.reviewsales.owner.Owner;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/v1/auth/oauth")
public class OAuthController {

    static final String STATE_COOKIE = "oauth_state";
    private static final String STATE_COOKIE_PATH = "/api/v1/auth/oauth";

    private final OAuthService oauthService;
    private final TokenIssuer tokenIssuer;
    private final boolean secureCookie;

    public OAuthController(OAuthService oauthService, TokenIssuer tokenIssuer, AppProperties props) {
        this.oauthService = oauthService;
        this.tokenIssuer = tokenIssuer;
        this.secureCookie = props.cookie().secure();
    }

    @Operation(summary = "소셜 로그인 시작 (kakao|google) → 제공자 인증 페이지로 302")
    @GetMapping("/{provider}")
    public ResponseEntity<Void> authorize(@PathVariable String provider) {
        OAuthService.AuthorizeRedirect redirect = oauthService.authorize(provider);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, stateCookie(redirect.nonce(), Duration.ofMinutes(10)))
                .location(redirect.location())
                .build();
    }

    @Operation(summary = "소셜 로그인 콜백 → 리프레시 쿠키 설정 후 프론트 /login/success 로 302")
    @GetMapping("/{provider}/callback")
    public ResponseEntity<Void> callback(@PathVariable String provider,
                                         @RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error,
                                         @CookieValue(name = STATE_COOKIE, required = false) String nonce,
                                         HttpServletResponse response) {
        Owner owner = oauthService.callback(provider, code, state, nonce, error);
        tokenIssuer.issue(owner.getId(), response); // 리프레시 쿠키 설정 (액세스 토큰은 프론트가 /auth/refresh 로 받는다)
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, stateCookie("", Duration.ZERO))
                .location(java.net.URI.create(oauthService.successRedirect()))
                .build();
    }

    private String stateCookie(String value, Duration maxAge) {
        return ResponseCookie.from(STATE_COOKIE, value).httpOnly(true).secure(secureCookie)
                .path(STATE_COOKIE_PATH).sameSite("Lax").maxAge(maxAge).build().toString();
    }
}
