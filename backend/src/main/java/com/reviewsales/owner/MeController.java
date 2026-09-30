package com.reviewsales.owner;

import java.time.OffsetDateTime;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reviewsales.auth.CurrentOwner;
import com.reviewsales.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final OwnerService ownerService;

    public MeController(OwnerService ownerService) {
        this.ownerService = ownerService;
    }

    @Operation(summary = "내 정보")
    @GetMapping
    public ApiResponse<MeResponse> me(@CurrentOwner Long ownerId) {
        Owner owner = ownerService.get(ownerId);
        return ApiResponse.ok(new MeResponse(owner.getId(), owner.getProvider(), owner.getEmail(), owner.getCreatedAt()));
    }

    public record MeResponse(Long ownerId, AuthProvider provider, String email, OffsetDateTime createdAt) {
    }
}
