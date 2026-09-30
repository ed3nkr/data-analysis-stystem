package com.reviewsales.job;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.reviewsales.common.BusinessException;
import com.reviewsales.common.ErrorCode;
import com.reviewsales.common.PageResponse;
import com.reviewsales.store.StoreAccess;

@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final JobRepository jobRepository;
    private final StoreAccess storeAccess;
    private final TransactionTemplate newTx;

    public JobService(JobRepository jobRepository, StoreAccess storeAccess, PlatformTransactionManager txManager) {
        this.jobRepository = jobRepository;
        this.storeAccess = storeAccess;
        this.newTx = new TransactionTemplate(txManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 별도 트랜잭션으로 job 을 만든다 (본 처리 실패와 무관하게 이력이 남도록). */
    public Job create(Long storeId, JobType type) {
        return newTx.execute(s -> jobRepository.save(new Job(storeId, type)));
    }

    /** 별도 트랜잭션으로 job 을 수정한다. */
    public Job update(Long jobId, Consumer<Job> change) {
        return newTx.execute(s -> {
            Job job = jobRepository.findById(jobId).orElseThrow();
            change.accept(job);
            return job;
        });
    }

    public void fail(Long jobId, Object errorDetail) {
        update(jobId, job -> job.fail(errorDetail));
    }

    @Transactional(readOnly = true)
    public JobDtos.JobResponse get(Long ownerId, Long jobId) {
        Job job = jobRepository.findById(jobId).orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        try {
            storeAccess.getOwned(job.getStoreId(), ownerId);
        } catch (BusinessException e) {
            // 남의 작업은 존재 여부도 드러내지 않는다
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND);
        }
        return JobDtos.JobResponse.of(job);
    }

    @Transactional(readOnly = true)
    public PageResponse<JobDtos.JobResponse> list(Long ownerId, Long storeId, JobType type, int page, int size) {
        storeAccess.getOwned(storeId, ownerId);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<Job> jobs = type == null
                ? jobRepository.findByStoreIdOrderByRequestedAtDesc(storeId, pageable)
                : jobRepository.findByStoreIdAndTypeOrderByRequestedAtDesc(storeId, type, pageable);
        return PageResponse.of(jobs, JobDtos.JobResponse::of);
    }

    /** 서버가 재시작되면 실행 중이던 비동기 작업은 사라지므로 FAILED 로 정리한다. */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        Integer n = newTx.execute(s -> {
            List<Job> active = jobRepository.findByStatusIn(List.of(JobStatus.REQUESTED, JobStatus.RUNNING));
            active.forEach(j -> j.fail(Map.of("code", "INTERRUPTED", "message", "서버 재시작으로 중단된 작업")));
            return active.size();
        });
        if (n != null && n > 0) {
            log.warn("중단된 작업 {}건을 FAILED 로 정리했습니다.", n);
        }
    }
}
