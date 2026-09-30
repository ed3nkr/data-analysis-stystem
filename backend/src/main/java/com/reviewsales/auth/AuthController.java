package com.reviewsales.auth;

import java.util.Map;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reviewsales.common.ApiResponse;
import com.reviewsales.common.BusinessException;
import com.reviewsales.common.ErrorCode;
import com.reviewsales.config.AppProperties;
import com.reviewsales.owner.OwnerRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final JwtProvider jwtProvider;
    private final TokenIssuer tokenIssuer;
    private final OwnerRepository ownerRepository;
    private final AppProperties props;
    private final Environment env;

    public AuthController(JwtProvider jwtProvider, TokenIssuer tokenIssuer, OwnerRepository ownerRepository,
                          AppProperties props, Environment env) {
        this.jwtProvider = jwtProvider;
        this.tokenIssuer = tokenIssuer;
        this.ownerRepository = ownerRepository;
        this.props = props;
        this.env = env;
    }

    @Operation(summary = "사용 가능한 로그인 방식 (프론트에서 버튼 표시용)")
    @GetMapping("/providers")
    public ApiResponse<Map<String, Boolean>> providers() {
        return ApiResponse.ok(Map.of(
                "dev", env.acceptsProfiles(Profiles.of("local")),
                "kakao", props.oauth().kakao() != null && props.oauth().kakao().enabled(),
                "google", props.oauth().google() != null && props.oauth().google().enabled()));
    }

    @Operation(summary = "액세스 토큰 재발급 (리프레시 쿠키 사용)")
    @PostMapping("/refresh")
    public ApiResponse<TokenIssuer.TokenResponse> refresh(
            @CookieValue(name = TokenIssuer.REFRESH_COOKIE, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED, "리프레시 토큰이 없습니다.");
        }
        Long ownerId = Long.valueOf(jwtProvider.parse(refreshToken, JwtProvider.TYPE_REFRESH).getSubject());
        if (!ownerRepository.existsById(ownerId)) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        return ApiResponse.ok(tokenIssuer.accessOnly(ownerId));
    }

    @Operation(summary = "로그아웃 (리프레시 쿠키 삭제)")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        tokenIssuer.clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }
}
