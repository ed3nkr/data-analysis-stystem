package com.reviewsales.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.reviewsales.store.Store;
import com.reviewsales.store.StoreCategory;

class ReviewCollectServiceTest {

    @Test
    void sinceIsSixMonthsAgoWhenNeverCollected() {
        Store store = new Store(1L, "s", StoreCategory.KOREAN);
        assertThat(ReviewCollectService.sinceFor(store, 6))
                .isEqualTo(LocalDate.now(ZoneId.of("Asia/Seoul")).minusMonths(6));
    }

    @Test
    void sinceIsLastCollectedDateInSeoul() {
        Store store = new Store(1L, "s", StoreCategory.KOREAN);
        // UTC 2026-09-29 16:00 = KST 2026-09-30 01:00
        store.markCollected(OffsetDateTime.of(2026, 9, 29, 16, 0, 0, 0, ZoneOffset.UTC));
        assertThat(ReviewCollectService.sinceFor(store, 6)).isEqualTo(LocalDate.of(2026, 9, 30));
    }
}
