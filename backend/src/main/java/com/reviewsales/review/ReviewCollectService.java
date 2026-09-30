package com.reviewsales.review;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.reviewsales.common.BusinessException;
import com.reviewsales.common.ErrorCode;
import com.reviewsales.config.AppProperties;
import com.reviewsales.job.Job;
import com.reviewsales.job.JobRepository;
import com.reviewsales.job.JobStatus;
import com.reviewsales.job.JobType;
import com.reviewsales.store.Store;
import com.reviewsales.store.StoreAccess;
import com.reviewsales.store.StoreRepository;

/** 네이버 리뷰 수집 요청 접수. 실제 수집은 ReviewCollectWorker 가 비동기로 한다. */
@Service
public class ReviewCollectService {

    private static final List<JobStatus> ACTIVE = List.of(JobStatus.REQUESTED, JobStatus.RUNNING);

    private final StoreAccess storeAccess;
    private final StoreRepository storeRepository;
    private final JobRepository jobRepository;
    private final ReviewCollectWorker worker;
    private final TransactionTemplate tx;
    private final AppProperties props;
    private final Map<Long, Object> storeLocks = new ConcurrentHashMap<>();

    public ReviewCollectService(StoreAccess storeAccess, StoreRepository storeRepository, JobRepository jobRepository,
                                ReviewCollectWorker worker, TransactionTemplate tx, AppProperties props) {
        this.storeAccess = storeAccess;
        this.storeRepository = storeRepository;
        this.jobRepository = jobRepository;
        this.worker = worker;
        this.tx = tx;
        this.props = props;
    }

    public ReviewDtos.CollectAccepted request(Long ownerId, Long storeId) {
        storeAccess.getOwned(storeId, ownerId);
        return requestInternal(storeId);
    }

    /** 소유권 검사 없이 요청 (주간 자동 수집용) */
    ReviewDtos.CollectAccepted requestInternal(Long storeId) {
        synchronized (storeLocks.computeIfAbsent(storeId, k -> new Object())) {
            Job job = tx.execute(s -> {
                Store store = storeRepository.findById(storeId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
                if (!store.isPlaceConnected()) {
                    throw new BusinessException(ErrorCode.PLACE_NOT_CONNECTED);
                }
                if (jobRepository.existsByStoreIdAndTypeAndStatusIn(storeId, JobType.REVIEW_COLLECT, ACTIVE)) {
                    throw new BusinessException(ErrorCode.JOB_ALREADY_RUNNING);
                }
                OffsetDateTime threshold = OffsetDateTime.now().minus(props.collect().minInterval());
                if (jobRepository.existsByStoreIdAndTypeAndRequestedAtAfter(storeId, JobType.REVIEW_COLLECT, threshold)) {
                    throw new BusinessException(ErrorCode.COLLECT_TOO_FREQUENT);
                }
                Job created = jobRepository.save(new Job(storeId, JobType.REVIEW_COLLECT));
                // 커밋된 뒤에 비동기 작업을 시작해야 worker 가 job 을 읽을 수 있다
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        worker.run(created.getId());
                    }
                });
                return created;
            });
            return new ReviewDtos.CollectAccepted(job.getId(), job.getType(), job.getStatus());
        }
    }

    /** since = last_collected_at 의 날짜, 없으면 6개월 전 (Asia/Seoul) */
    static LocalDate sinceFor(Store store, int lookbackMonths) {
        ZoneId kst = ZoneId.of("Asia/Seoul");
        return store.getLastCollectedAt() != null
                ? store.getLastCollectedAt().atZoneSameInstant(kst).toLocalDate()
                : LocalDate.now(kst).minusMonths(lookbackMonths);
    }
}
