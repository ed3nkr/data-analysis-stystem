package com.reviewsales.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.reviewsales.common.BusinessException;
import com.reviewsales.common.ErrorCode;
import com.reviewsales.config.AppProperties;

class JwtProviderTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-1234";

    private static JwtProvider provider(Duration accessTtl) {
        var props = new AppProperties(new AppProperties.Jwt(SECRET, accessTtl, Duration.ofDays(14)),
                null, null, null, null, null, null);
        return new JwtProvider(props, new MockEnvironment());
    }

    @Test
    void accessTokenRoundTrip() {
        JwtProvider p = provider(Duration.ofMinutes(30));
        String token = p.createAccessToken(42L);
        assertThat(p.parse(token, JwtProvider.TYPE_ACCESS).getSubject()).isEqualTo("42");
    }

    @Test
    void expiredTokenIsTokenExpired() {
        JwtProvider p = provider(Duration.ofSeconds(-5));
        String token = p.createAccessToken(1L);
        assertThatThrownBy(() -> p.parse(token, JwtProvider.TYPE_ACCESS))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code()).isEqualTo(ErrorCode.TOKEN_EXPIRED);
    }

    @Test
    void refreshTokenCannotBeUsedAsAccessToken() {
        JwtProvider p = provider(Duration.ofMinutes(30));
        String refresh = p.createRefreshToken(1L);
        assertThatThrownBy(() -> p.parse(refresh, JwtProvider.TYPE_ACCESS))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code()).isEqualTo(ErrorCode.AUTH_REQUIRED);
    }

    @Test
    void tamperedTokenRejected() {
        JwtProvider p = provider(Duration.ofMinutes(30));
        String token = p.createAccessToken(1L) + "x";
        assertThatThrownBy(() -> p.parse(token, JwtProvider.TYPE_ACCESS)).isInstanceOf(BusinessException.class);
    }

    @Test
    void missingSecretOutsideLocalFails() {
        var props = new AppProperties(new AppProperties.Jwt("", Duration.ofMinutes(30), Duration.ofDays(14)),
                null, null, null, null, null, null);
        var env = new MockEnvironment();
        env.setActiveProfiles("prod");
        assertThatThrownBy(() -> new JwtProvider(props, env)).isInstanceOf(IllegalStateException.class);
    }
}
