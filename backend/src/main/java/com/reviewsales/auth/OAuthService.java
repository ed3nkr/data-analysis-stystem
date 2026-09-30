package com.reviewsales.auth;

import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.reviewsales.common.BusinessException;
import com.reviewsales.common.ErrorCode;
import com.reviewsales.config.AppProperties;
import com.reviewsales.owner.AuthProvider;
import com.reviewsales.owner.Owner;
import com.reviewsales.owner.OwnerService;

import io.jsonwebtoken.Claims;

/**
 * 카카오·구글 OAuth (Authorization Code). 키가 없는 제공자는 비활성화된다.
 * state = 서명된 JWT(provider, nonce, 10분) + 같은 nonce 를 HttpOnly 쿠키에 보관해 콜백에서 대조한다.
 */
@Service
public class OAuthService {

    private static final Logger log = LoggerFactory.getLogger(OAuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppProperties props;
    private final JwtProvider jwtProvider;
    private final OwnerService ownerService;
    private final RestClient http = RestClient.create();

    public OAuthService(AppProperties props, JwtProvider jwtProvider, OwnerService ownerService) {
        this.props = props;
        this.jwtProvider = jwtProvider;
        this.ownerService = ownerService;
    }

    public record AuthorizeRedirect(URI location, String nonce) {
    }

    public AuthorizeRedirect authorize(String providerName) {
        AuthProvider provider = parse(providerName);
        AppProperties.Provider cfg = config(provider);
        String nonce = randomToken();
        String state = jwtProvider.createOAuthState(provider.name(), nonce);
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(cfg.authorizeUri())
                .queryParam("client_id", cfg.clientId())
                .queryParam("redirect_uri", redirectUri(provider))
                .queryParam("response_type", "code")
                .queryParam("state", state);
        if (provider == AuthProvider.GOOGLE) {
            b.queryParam("scope", "openid email");
        }
        return new AuthorizeRedirect(b.encode().build().toUri(), nonce);
    }

    /** 콜백 처리: state 검증 → 토큰 교환 → 사용자 식별값 조회 → owner 생성/조회 */
    public Owner callback(String providerName, String code, String state, String cookieNonce, String error) {
        AuthProvider provider = parse(providerName);
        AppProperties.Provider cfg = config(provider);
        verifyState(provider, state, cookieNonce);
        if (error != null && !error.isBlank()) {
            throw new BusinessException(ErrorCode.OAUTH_FAILED, "사용자가 로그인을 취소했거나 제공자 오류: " + error);
        }
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.OAUTH_FAILED, "인가 코드가 없습니다.");
        }
        try {
            String accessToken = exchangeCode(provider, cfg, code);
            JsonNode user = http.get().uri(cfg.userInfoUri())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve().body(JsonNode.class);
            String userId;
            String email;
            if (provider == AuthProvider.KAKAO) {
                userId = user == null ? null : user.path("id").asText(null);
                email = user == null ? null : user.path("kakao_account").path("email").asText(null);
            } else {
                userId = user == null ? null : user.path("sub").asText(null);
                email = user == null ? null : user.path("email").asText(null);
            }
            if (userId == null || userId.isBlank()) {
                throw new BusinessException(ErrorCode.OAUTH_FAILED, "사용자 식별값을 받지 못했습니다.");
            }
            return ownerService.findOrCreate(provider, userId, email);
        } catch (RestClientException e) {
            log.warn("OAuth {} 실패: {}", provider, e.getMessage());
            throw new BusinessException(ErrorCode.OAUTH_FAILED);
        }
    }

    public String successRedirect() {
        return props.oauth().frontendBaseUrl().replaceAll("/+$", "") + "/login/success";
    }

    private String exchangeCode(AuthProvider provider, AppProperties.Provider cfg, String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", cfg.clientId());
        if (cfg.clientSecret() != null && !cfg.clientSecret().isBlank()) {
            form.add("client_secret", cfg.clientSecret());
        }
        form.add("redirect_uri", redirectUri(provider));
        form.add("code", code);
        JsonNode token = http.post().uri(cfg.tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve().body(JsonNode.class);
        String accessToken = token == null ? null : token.path("access_token").asText(null);
        if (accessToken == null) {
            throw new BusinessException(ErrorCode.OAUTH_FAILED, "액세스 토큰을 받지 못했습니다.");
        }
        return accessToken;
    }

    private void verifyState(AuthProvider provider, String state, String cookieNonce) {
        if (state == null || state.isBlank() || cookieNonce == null || cookieNonce.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_STATE);
        }
        Claims claims;
        try {
            claims = jwtProvider.parse(state, JwtProvider.TYPE_OAUTH_STATE);
        } catch (BusinessException e) {
            throw new BusinessException(ErrorCode.INVALID_STATE);
        }
        if (!provider.name().equals(claims.getSubject()) || !cookieNonce.equals(claims.get("nonce", String.class))) {
            throw new BusinessException(ErrorCode.INVALID_STATE);
        }
    }

    String redirectUri(AuthProvider provider) {
        return props.oauth().redirectBase().replaceAll("/+$", "")
                + "/api/v1/auth/oauth/" + provider.name().toLowerCase(Locale.ROOT) + "/callback";
    }

    private AuthProvider parse(String name) {
        return switch (name == null ? "" : name.toLowerCase(Locale.ROOT)) {
            case "kakao" -> AuthProvider.KAKAO;
            case "google" -> AuthProvider.GOOGLE;
            default -> throw new BusinessException(ErrorCode.UNSUPPORTED_PROVIDER);
        };
    }

    private AppProperties.Provider config(AuthProvider provider) {
        AppProperties.Provider cfg = provider == AuthProvider.KAKAO ? props.oauth().kakao() : props.oauth().google();
        if (cfg == null || !cfg.enabled()) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_PROVIDER,
                    provider.name() + " 로그인이 설정되지 않았습니다 (" + provider.name() + "_CLIENT_ID 필요).");
        }
        return cfg;
    }

    private static String randomToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
