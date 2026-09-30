package com.reviewsales.review;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.reviewsales.common.BusinessException;
import com.reviewsales.store.Store;
import com.reviewsales.store.StoreRepository;

/** 주간 자동 수집. 기본 비활성화 — COLLECT_WEEKLY_ENABLED=true 로 켠다. */
@Component
@ConditionalOnProperty(prefix = "app.collect", name = "weekly-enabled", havingValue = "true")
public class WeeklyCollectScheduler {

    private static final Logger log = LoggerFactory.getLogger(WeeklyCollectScheduler.class);

    private final StoreRepository storeRepository;
    private final ReviewCollectService collectService;

    public WeeklyCollectScheduler(StoreRepository storeRepository, ReviewCollectService collectService) {
        this.storeRepository = storeRepository;
        this.collectService = collectService;
    }

    @Scheduled(cron = "${app.collect.weekly-cron}", zone = "Asia/Seoul")
    public void collectAll() {
        for (Store store : storeRepository.findByPlaceIdIsNotNull()) {
            try {
                var accepted = collectService.requestInternal(store.getId());
                log.info("주간 자동 수집 요청 store={} job={}", store.getId(), accepted.jobId());
            } catch (BusinessException e) {
                log.info("주간 자동 수집 건너뜀 store={}: {}", store.getId(), e.code());
            }
        }
    }
}
