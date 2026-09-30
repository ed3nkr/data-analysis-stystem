package com.reviewsales.menu;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reviewsales.common.BusinessException;
import com.reviewsales.common.ErrorCode;
import com.reviewsales.sales.SalesRecordRepository;
import com.reviewsales.store.StoreAccess;

@Service
public class MenuService {

    private final MenuRepository menuRepository;
    private final SalesRecordRepository salesRecordRepository;
    private final StoreAccess storeAccess;

    public MenuService(MenuRepository menuRepository, SalesRecordRepository salesRecordRepository,
                       StoreAccess storeAccess) {
        this.menuRepository = menuRepository;
        this.salesRecordRepository = salesRecordRepository;
        this.storeAccess = storeAccess;
    }

    @Transactional(readOnly = true)
    public List<MenuDtos.MenuResponse> list(Long ownerId, Long storeId) {
        storeAccess.getOwned(storeId, ownerId);
        return menuRepository.findByStoreIdOrderByIdAsc(storeId).stream().map(MenuDtos.MenuResponse::of).toList();
    }

    @Transactional
    public MenuDtos.MenuResponse create(Long ownerId, Long storeId, MenuDtos.CreateRequest req) {
        storeAccess.getOwned(storeId, ownerId);
        String normalized = requireNormalizable(req.posName());
        if (menuRepository.existsByStoreIdAndNormalizedName(storeId, normalized)) {
            throw new BusinessException(ErrorCode.MENU_ALREADY_EXISTS);
        }
        Menu menu = menuRepository.save(new Menu(storeId, req.posName(), req.aliases()));
        return MenuDtos.MenuResponse.of(menu);
    }

    @Transactional
    public MenuDtos.MenuResponse update(Long ownerId, Long storeId, Long menuId, MenuDtos.UpdateRequest req) {
        storeAccess.getOwned(storeId, ownerId);
        Menu menu = get(storeId, menuId);
        if (req.posName() != null) {
            String normalized = requireNormalizable(req.posName());
            if (!normalized.equals(menu.getNormalizedName())
                    && menuRepository.existsByStoreIdAndNormalizedName(storeId, normalized)) {
                throw new BusinessException(ErrorCode.MENU_ALREADY_EXISTS);
            }
            menu.rename(req.posName());
        }
        if (req.aliases() != null) {
            menu.setAliases(req.aliases());
        }
        return MenuDtos.MenuResponse.of(menu);
    }

    @Transactional
    public void delete(Long ownerId, Long storeId, Long menuId) {
        storeAccess.getOwned(storeId, ownerId);
        Menu menu = get(storeId, menuId);
        if (salesRecordRepository.existsByMenuId(menu.getId())) {
            throw new BusinessException(ErrorCode.MENU_IN_USE);
        }
        menuRepository.delete(menu);
    }

    private Menu get(Long storeId, Long menuId) {
        return menuRepository.findByIdAndStoreId(menuId, storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MENU_NOT_FOUND));
    }

    private static String requireNormalizable(String posName) {
        String normalized = MenuNameNormalizer.normalize(posName);
        if (normalized.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "메뉴명이 비어 있습니다 (괄호 옵션만으로는 등록할 수 없습니다).");
        }
        return normalized;
    }
}
