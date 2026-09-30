package com.reviewsales.review;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.reviewsales.collector.CollectorClient;
import com.reviewsales.common.BusinessException;
import com.reviewsales.config.AppProperties;
import com.reviewsales.config.AsyncConfig;
import com.reviewsales.job.Job;
import com.reviewsales.job.JobRepository;
import com.reviewsales.job.JobService;
import com.reviewsales.store.Store;
import com.reviewsales.store.StoreRepository;

/** collector 를 호출해 리뷰를 수집·저장하는 비동기 작업 */
@Component
public class ReviewCollectWorker {

    private static final Logger log = LoggerFactory.getLogger(ReviewCollectWorker.class);

    private final JobRepository jobRepository;
    private final JobService jobService;
    private final StoreRepository storeRepository;
    private final CollectorClient collectorClient;
    private final ReviewWriter writer;
    private final TransactionTemplate tx;
    private final AppProperties props;

    public ReviewCollectWorker(JobRepository jobRepository, JobService jobService, StoreRepository storeRepository,
                               CollectorClient collectorClient, ReviewWriter writer, TransactionTemplate tx,
                               AppProperties props) {
        this.jobRepository = jobRepository;
        this.jobService = jobService;
        this.storeRepository = storeRepository;
        this.collectorClient = collectorClient;
        this.writer = writer;
        this.tx = tx;
        this.props = props;
    }

    @Async(AsyncConfig.COLLECT_EXECUTOR)
    public void run(Long jobId) {
        try {
            Job job = jobService.update(jobId, Job::start);
            Store store = storeRepository.findById(job.getStoreId()).orElseThrow();
            var since = ReviewCollectService.sinceFor(store, props.collect().defaultLookbackMonths());
            log.info("리뷰 수집 시작 job={} store={} place={} since={}", jobId, store.getId(), store.getPlaceId(), since);

            CollectorClient.CollectResult result = collectorClient.collectReviews(store.getPlaceId(), since,
                    props.collector().maxRequests());

            List<ReviewWriter.NewReview> reviews = new ArrayList<>();
            for (CollectorClient.CollectedReview r : result.reviews() == null
                    ? List.<CollectorClient.CollectedReview>of() : result.reviews()) {
                if (r.writtenAt() == null || r.content() == null || r.content().isBlank()) {
                    continue;
                }
                String content = r.content().strip();
                // dedup_key 는 backend 에서 같은 규칙으로 다시 계산해 저장한다 (파일 등록분과 일관성 유지)
                reviews.add(new ReviewWriter.NewReview(r.writtenAt(), r.visitedAt(), r.rating(), content,
                        r.authorHash(), ReviewHasher.dedupKey(r.writtenAt(), r.authorHash(), content)));
            }
            ReviewWriter.Saved saved = tx.execute(s -> {
                ReviewWriter.Saved res = writer.insert(store.getId(), jobId, ReviewSource.NAVER, reviews);
                Job j = jobRepository.findById(jobId).orElseThrow();
                Store st = storeRepository.findById(store.getId()).orElseThrow();
                st.markCollected(j.getRequestedAt());
                j.complete(res.accepted(), 0, null);
                return res;
            });
            log.info("리뷰 수집 완료 job={} 신규={} 중복={} 요청={} 중단사유={}", jobId, saved.accepted(),
                    saved.duplicated(), result.requestCount(), result.stoppedReason());
        } catch (BusinessException e) {
            log.warn("리뷰 수집 실패 job={} {}: {}", jobId, e.code(), e.getMessage());
            jobService.fail(jobId, Map.of("code", e.code().name(), "message", e.getMessage()));
        } catch (RuntimeException e) {
            log.error("리뷰 수집 실패 job={}", jobId, e);
            jobService.fail(jobId, Map.of("code", "INTERNAL_ERROR", "message", String.valueOf(e.getMessage())));
        }
    }
}
