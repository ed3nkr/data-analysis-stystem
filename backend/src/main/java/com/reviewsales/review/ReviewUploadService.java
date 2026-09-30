package com.reviewsales.review;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.reviewsales.common.BusinessException;
import com.reviewsales.common.ErrorCode;
import com.reviewsales.common.UploadStorage;
import com.reviewsales.common.csv.CsvText;
import com.reviewsales.job.Job;
import com.reviewsales.job.JobRepository;
import com.reviewsales.job.JobService;
import com.reviewsales.job.JobStatus;
import com.reviewsales.job.JobType;
import com.reviewsales.sales.SalesCsvParser.RowError;
import com.reviewsales.store.StoreAccess;

/** 리뷰 파일 등록 (수집 실패 시 대체 수단). 파일이 작으므로 요청 안에서 바로 처리하고 202 로 결과를 돌려준다. */
@Service
public class ReviewUploadService {

    private static final Logger log = LoggerFactory.getLogger(ReviewUploadService.class);
    private static final int MAX_STORED_ERRORS = 1000;

    private final StoreAccess storeAccess;
    private final JobService jobService;
    private final JobRepository jobRepository;
    private final UploadStorage uploadStorage;
    private final ReviewHasher hasher;
    private final ReviewWriter writer;
    private final TransactionTemplate tx;

    public ReviewUploadService(StoreAccess storeAccess, JobService jobService, JobRepository jobRepository,
                               UploadStorage uploadStorage, ReviewHasher hasher, ReviewWriter writer,
                               TransactionTemplate tx) {
        this.storeAccess = storeAccess;
        this.jobService = jobService;
        this.jobRepository = jobRepository;
        this.uploadStorage = uploadStorage;
        this.hasher = hasher;
        this.writer = writer;
        this.tx = tx;
    }

    public ReviewDtos.UploadResult upload(Long ownerId, Long storeId, MultipartFile file) {
        storeAccess.getOwned(storeId, ownerId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "업로드할 파일이 없습니다.");
        }
        UploadStorage.checkSize(file.getSize());
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        Job job = jobService.create(storeId, JobType.REVIEW_UPLOAD);
        Long jobId = job.getId();
        try {
            String path = uploadStorage.save(storeId, jobId, file.getOriginalFilename(), bytes);
            jobService.update(jobId, j -> {
                j.setFilePath(path);
                j.start();
            });
            ReviewCsvParser.Result parsed = ReviewCsvParser.parse(CsvText.decode(bytes).text());

            List<ReviewWriter.NewReview> reviews = new ArrayList<>(parsed.rows().size());
            for (ReviewCsvParser.ReviewRow r : parsed.rows()) {
                String authorHash = hasher.authorHash(r.author()); // 닉네임 원문은 저장하지 않는다
                reviews.add(new ReviewWriter.NewReview(r.writtenAt(), r.visitedAt(), r.rating(), r.content(),
                        authorHash, ReviewHasher.dedupKey(r.writtenAt(), authorHash, r.content())));
            }
            List<RowError> storedErrors = new ArrayList<>(parsed.errors().size() > MAX_STORED_ERRORS
                    ? parsed.errors().subList(0, MAX_STORED_ERRORS) : parsed.errors());

            ReviewWriter.Saved saved = tx.execute(s -> {
                ReviewWriter.Saved result = writer.insert(storeId, jobId, ReviewSource.FILE, reviews);
                Job j = jobRepository.findById(jobId).orElseThrow();
                j.complete(result.accepted(), parsed.errors().size(), storedErrors);
                return result;
            });
            return new ReviewDtos.UploadResult(jobId, JobType.REVIEW_UPLOAD, JobStatus.COMPLETED, saved.accepted(),
                    saved.duplicated(), parsed.errors().size());
        } catch (BusinessException e) {
            jobService.fail(jobId, List.of(new RowError(null, e.code().name() + ": " + e.getMessage(), null)));
            throw e;
        } catch (RuntimeException e) {
            log.error("리뷰 파일 등록 실패 job={}", jobId, e);
            jobService.fail(jobId, List.of(new RowError(null, "INTERNAL_ERROR", null)));
            throw e;
        }
    }
}
