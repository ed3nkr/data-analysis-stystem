package com.reviewsales.menu;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.reviewsales.auth.CurrentOwner;
import com.reviewsales.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Menu")
@RestController
@RequestMapping("/api/v1/stores/{storeId}/menus")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @Operation(summary = "메뉴 목록")
    @GetMapping
    public ApiResponse<List<MenuDtos.MenuResponse>> list(@CurrentOwner Long ownerId, @PathVariable Long storeId) {
        return ApiResponse.ok(menuService.list(ownerId, storeId));
    }

    @Operation(summary = "메뉴 등록 (normalizedName 은 서버가 생성)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MenuDtos.MenuResponse> create(@CurrentOwner Long ownerId, @PathVariable Long storeId,
                                                     @Valid @RequestBody MenuDtos.CreateRequest request) {
        return ApiResponse.ok(menuService.create(ownerId, storeId, request));
    }

    @Operation(summary = "메뉴 수정")
    @PatchMapping("/{menuId}")
    public ApiResponse<MenuDtos.MenuResponse> update(@CurrentOwner Long ownerId, @PathVariable Long storeId,
                                                     @PathVariable Long menuId,
                                                     @Valid @RequestBody MenuDtos.UpdateRequest request) {
        return ApiResponse.ok(menuService.update(ownerId, storeId, menuId, request));
    }

    @Operation(summary = "메뉴 삭제 (매출 기록이 연결된 메뉴는 409 MENU_IN_USE)")
    @DeleteMapping("/{menuId}")
    public ResponseEntity<Void> delete(@CurrentOwner Long ownerId, @PathVariable Long storeId,
                                       @PathVariable Long menuId) {
        menuService.delete(ownerId, storeId, menuId);
        return ResponseEntity.noContent().build();
    }
}
