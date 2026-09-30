package com.reviewsales.store;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class StoreDtos {

    private StoreDtos() {
    }

    public record CreateRequest(@NotBlank @Size(max = 100) String name, @NotNull StoreCategory category) {
    }

    public record UpdateRequest(@Size(min = 1, max = 100) String name, StoreCategory category) {
    }

    public record PlacePreviewRequest(@NotBlank @Size(max = 2000) String placeUrl) {
    }

    public record PlaceConnectRequest(@NotBlank @Pattern(regexp = "\\d{1,20}", message = "placeId 는 숫자여야 합니다.")
                                      String placeId) {
    }

    public record PlaceResponse(String placeId, String placeName, String address) {
    }

    public record StoreSummary(Long storeId, String name, StoreCategory category, boolean placeConnected) {
        static StoreSummary of(Store s) {
            return new StoreSummary(s.getId(), s.getName(), s.getCategory(), s.isPlaceConnected());
        }
    }

    public record StoreDetail(Long storeId, String name, StoreCategory category, boolean placeConnected,
                              PlaceResponse place, OffsetDateTime lastCollectedAt, long menuCount,
                              OffsetDateTime createdAt) {
        static StoreDetail of(Store s, long menuCount) {
            PlaceResponse place = s.isPlaceConnected()
                    ? new PlaceResponse(s.getPlaceId(), s.getPlaceName(), s.getAddress()) : null;
            return new StoreDetail(s.getId(), s.getName(), s.getCategory(), s.isPlaceConnected(), place,
                    s.getLastCollectedAt(), menuCount, s.getCreatedAt());
        }
    }
}
