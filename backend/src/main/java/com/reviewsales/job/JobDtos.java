package com.reviewsales.job;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public final class JobDtos {

    private JobDtos() {
    }

    public record JobResponse(Long jobId, Long storeId, JobType type, JobStatus status, LocalDate periodStart,
                              LocalDate periodEnd, int processedCount, int errorCount, Object errorDetail,
                              OffsetDateTime requestedAt, OffsetDateTime finishedAt) {
        public static JobResponse of(Job j) {
            // 업로드 오류 행 목록은 크므로 별도 API 로 조회하고, 여기서는 수집 실패 사유 같은 객체만 내려준다
            Object detail = j.getErrorDetail() == null || j.getErrorDetail().isArray() ? null : j.getErrorDetail();
            return new JobResponse(j.getId(), j.getStoreId(), j.getType(), j.getStatus(), j.getPeriodStart(),
                    j.getPeriodEnd(), j.getProcessedCount(), j.getErrorCount(), detail, j.getRequestedAt(),
                    j.getFinishedAt());
        }
    }
}
