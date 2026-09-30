package com.reviewsales.store;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reviewsales.collector.CollectorClient;
import com.reviewsales.common.BusinessException;
import com.reviewsales.common.ErrorCode;
import com.reviewsales.menu.MenuRepository;

@Service
public class StoreService {

    private final StoreRepository storeRepository;
    private final MenuRepository menuRepository;
    private final StoreAccess storeAccess;
    private final CollectorClient collectorClient;

    public StoreService(StoreRepository storeRepository, MenuRepository menuRepository, StoreAccess storeAccess,
                        CollectorClient collectorClient) {
        this.storeRepository = storeRepository;
        this.menuRepository = menuRepository;
        this.storeAccess = storeAccess;
        this.collectorClient = collectorClient;
    }

    @Transactional
    public StoreDtos.StoreSummary create(Long ownerId, StoreDtos.CreateRequest req) {
        Store store = storeRepository.save(new Store(ownerId, req.name().trim(), req.category()));
        return StoreDtos.StoreSummary.of(store);
    }

    @Transactional(readOnly = true)
    public List<StoreDtos.StoreSummary> list(Long ownerId) {
        return storeRepository.findByOwnerIdOrderByIdAsc(ownerId).stream().map(StoreDtos.StoreSummary::of).toList();
    }

    @Transactional(readOnly = true)
    public StoreDtos.StoreDetail get(Long ownerId, Long storeId) {
        Store store = storeAccess.getOwned(storeId, ownerId);
        return StoreDtos.StoreDetail.of(store, menuRepository.countByStoreId(storeId));
    }

    @Transactional
    public StoreDtos.StoreDetail update(Long ownerId, Long storeId, StoreDtos.UpdateRequest req) {
        Store store = storeAccess.getOwned(storeId, ownerId);
        store.update(req.name(), req.category());
        return StoreDtos.StoreDetail.of(store, menuRepository.countByStoreId(storeId));
    }

    /** 링크로 플레이스 정보를 미리 본다 (저장하지 않음). */
    public StoreDtos.PlaceResponse previewPlace(Long ownerId, Long storeId, String placeUrl) {
        storeAccess.getOwned(storeId, ownerId);
        CollectorClient.PlaceInfo info = collectorClient.resolvePlace(placeUrl.trim());
        return new StoreDtos.PlaceResponse(info.placeId(), info.placeName(), info.address());
    }

    /** placeId 로 플레이스를 다시 조회해 이름·주소와 함께 저장한다. */
    public StoreDtos.PlaceResponse connectPlace(Long ownerId, Long storeId, String placeId) {
        storeAccess.getOwned(storeId, ownerId);
        CollectorClient.PlaceInfo info = collectorClient.resolvePlace(
                "https://m.place.naver.com/restaurant/" + placeId + "/home");
        if (!placeId.equals(info.placeId())) {
            throw new BusinessException(ErrorCode.PLACE_NOT_FOUND);
        }
        saveConnection(storeId, info);
        return new StoreDtos.PlaceResponse(info.placeId(), info.placeName(), info.address());
    }

    private void saveConnection(Long storeId, CollectorClient.PlaceInfo info) {
        Store store = storeRepository.findById(storeId).orElseThrow();
        store.connectPlace(info.placeId(), info.placeName(), info.address());
        storeRepository.save(store);
    }
}
