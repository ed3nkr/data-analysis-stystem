package com.reviewsales.store;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.reviewsales.common.BusinessException;
import com.reviewsales.common.ErrorCode;

/** /api/v1/stores/{storeId}/** 공통 소유권 검사: 없으면 404, 남의 매장이면 403 */
@Component
public class StoreAccess {

    private final StoreRepository storeRepository;

    public StoreAccess(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    @Transactional(readOnly = true)
    public Store getOwned(Long storeId, Long ownerId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
        if (!store.getOwnerId().equals(ownerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_STORE);
        }
        return store;
    }
}
