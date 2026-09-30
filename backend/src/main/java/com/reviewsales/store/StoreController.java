package com.reviewsales.store;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.reviewsales.auth.CurrentOwner;
import com.reviewsales.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Store")
@RestController
@RequestMapping("/api/v1/stores")
public class StoreController {

    private final StoreService storeService;

    public StoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    @Operation(summary = "매장 등록")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StoreDtos.StoreSummary> create(@CurrentOwner Long ownerId,
                                                      @Valid @RequestBody StoreDtos.CreateRequest request) {
        return ApiResponse.ok(storeService.create(ownerId, request));
    }

    @Operation(summary = "내 매장 목록")
    @GetMapping
    public ApiResponse<List<StoreDtos.StoreSummary>> list(@CurrentOwner Long ownerId) {
        return ApiResponse.ok(storeService.list(ownerId));
    }

    @Operation(summary = "매장 상세 (place 정보, lastCollectedAt, menuCount)")
    @GetMapping("/{storeId}")
    public ApiResponse<StoreDtos.StoreDetail> get(@CurrentOwner Long ownerId, @PathVariable Long storeId) {
        return ApiResponse.ok(storeService.get(ownerId, storeId));
    }

    @Operation(summary = "매장 수정")
    @PatchMapping("/{storeId}")
    public ApiResponse<StoreDtos.StoreDetail> update(@CurrentOwner Long ownerId, @PathVariable Long storeId,
                                                     @Valid @RequestBody StoreDtos.UpdateRequest request) {
        return ApiResponse.ok(storeService.update(ownerId, storeId, request));
    }

    @Operation(summary = "네이버 플레이스 링크 미리보기 (저장 안 함)")
    @PostMapping("/{storeId}/place/preview")
    public ApiResponse<StoreDtos.PlaceResponse> previewPlace(@CurrentOwner Long ownerId, @PathVariable Long storeId,
                                                             @Valid @RequestBody StoreDtos.PlacePreviewRequest request) {
        return ApiResponse.ok(storeService.previewPlace(ownerId, storeId, request.placeUrl()));
    }

    @Operation(summary = "네이버 플레이스 연결")
    @PutMapping("/{storeId}/place")
    public ApiResponse<StoreDtos.PlaceResponse> connectPlace(@CurrentOwner Long ownerId, @PathVariable Long storeId,
                                                             @Valid @RequestBody StoreDtos.PlaceConnectRequest request) {
        return ApiResponse.ok(storeService.connectPlace(ownerId, storeId, request.placeId()));
    }
}
