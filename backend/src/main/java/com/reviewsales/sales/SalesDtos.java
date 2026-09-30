package com.reviewsales.sales;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.reviewsales.common.UploadStorage;
import com.reviewsales.job.Job;
import com.reviewsales.job.JobStatus;

public final class SalesDtos {

    private SalesDtos() {
    }

    public record UploadResult(Long uploadId, LocalDate periodStart, LocalDate periodEnd, int totalRows,
                               int successRows, int errorRows, List<String> newMenuCandidates) {
    }

    public record UploadHistory(Long uploadId, String fileName, JobStatus status, LocalDate periodStart,
                                LocalDate periodEnd, int successRows, int errorRows, OffsetDateTime requestedAt,
                                OffsetDateTime finishedAt) {
        static UploadHistory of(Job j) {
            return new UploadHistory(j.getId(), UploadStorage.originalName(j.getFilePath()), j.getStatus(),
                    j.getPeriodStart(), j.getPeriodEnd(), j.getProcessedCount(), j.getErrorCount(),
                    j.getRequestedAt(), j.getFinishedAt());
        }
    }

    public record UploadErrors(Long uploadId, JobStatus status, int errorRows, int returnedRows, boolean truncated,
                               List<SalesCsvParser.RowError> errors) {
    }

    public enum GroupBy { DAY, MENU }

    public record DaySummary(LocalDate date, long quantity, long amount) {
    }

    public record MenuSummary(Long menuId, String menuName, boolean matched, long quantity, long amount) {
    }

    public record Summary(LocalDate from, LocalDate to, GroupBy groupBy, long totalQuantity, long totalAmount,
                          List<?> items) {
    }
}
