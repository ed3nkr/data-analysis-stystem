package com.reviewsales.review;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.reviewsales.job.JobStatus;
import com.reviewsales.job.JobType;

public final class ReviewDtos {

    private ReviewDtos() {
    }

    public record UploadResult(Long jobId, JobType type, JobStatus status, int acceptedRows, int duplicatedRows,
                               int errorRows) {
    }

    public record CollectAccepted(Long jobId, JobType type, JobStatus status) {
    }

    /** 리뷰 원문 + 분석 결과 필드 (분석 전이므로 지금은 null) */
    public record ReviewResponse(Long reviewId, ReviewSource source, LocalDate writtenAt, LocalDate visitedAt,
                                 BigDecimal rating, String content, Float reliability, Boolean excluded,
                                 String excludeReason, List<String> negTypes, JsonNode negProbs, List<Long> menuIds,
                                 String timeSlot, String modelVersion, OffsetDateTime analyzedAt) {
        static ReviewResponse of(Review r) {
            return new ReviewResponse(r.getId(), r.getSource(), r.getWrittenAt(), r.getVisitedAt(), r.getRating(),
                    r.getContent(), r.getReliability(), r.getExcluded(), r.getExcludeReason(), r.getNegTypes(),
                    r.getNegProbs(), r.getMenuIds(), r.getTimeSlot(), r.getModelVersion(), r.getAnalyzedAt());
        }
    }
}
