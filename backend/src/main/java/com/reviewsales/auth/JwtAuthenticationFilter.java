package com.reviewsales.auth;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.reviewsales.common.BusinessException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Authorization: Bearer 액세스 토큰을 읽어 ownerId 를 principal 로 설정한다. */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ATTR_AUTH_ERROR = "authErrorCode";

    private final JwtProvider jwtProvider;

    public JwtAuthenticationFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            try {
                var claims = jwtProvider.parse(header.substring(7).trim(), JwtProvider.TYPE_ACCESS);
                Long ownerId = Long.valueOf(claims.getSubject());
                var auth = new UsernamePasswordAuthenticationToken(ownerId, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (BusinessException e) {
                // 엔트리포인트에서 TOKEN_EXPIRED / AUTH_REQUIRED 를 구분해 응답한다
                request.setAttribute(ATTR_AUTH_ERROR, e.code());
            }
        }
        chain.doFilter(request, response);
    }
}
