package com.reviewsales.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reviewsales.common.ApiResponse;
import com.reviewsales.owner.AuthProvider;
import com.reviewsales.owner.Owner;
import com.reviewsales.owner.OwnerService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** local 프로필 전용 개발 로그인. OAuth 키 없이 시연하기 위한 것이다. */
@Profile("local")
@Tag(name = "Auth")
@RestController
@RequestMapping("/api/v1/auth")
public class DevLoginController {

    private final OwnerService ownerService;
    private final TokenIssuer tokenIssuer;

    public DevLoginController(OwnerService ownerService, TokenIssuer tokenIssuer) {
        this.ownerService = ownerService;
        this.tokenIssuer = tokenIssuer;
    }

    @Operation(summary = "[local 전용] 개발용 로그인")
    @PostMapping("/dev-login")
    public ApiResponse<TokenIssuer.TokenResponse> devLogin(@Valid @RequestBody DevLoginRequest request,
                                                           HttpServletResponse response) {
        String email = request.email().trim().toLowerCase();
        Owner owner = ownerService.findOrCreate(AuthProvider.DEV, email, email);
        return ApiResponse.ok(tokenIssuer.issue(owner.getId(), response));
    }

    public record DevLoginRequest(@NotBlank @Email String email) {
    }
}
