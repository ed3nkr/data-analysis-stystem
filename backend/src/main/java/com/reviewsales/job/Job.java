package com.reviewsales.job;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "job")
public class Job {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "processed_count", nullable = false)
    private int processedCount;

    @Column(name = "error_count", nullable = false)
    private int errorCount;

    /** 업로드: [{rowNumber, reason, rawLine}], 수집 실패: {code, message} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_detail", columnDefinition = "jsonb")
    private JsonNode errorDetail;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    protected Job() {
    }

    public Job(Long storeId, JobType type) {
        this.storeId = storeId;
        this.type = type;
        this.status = JobStatus.REQUESTED;
        this.requestedAt = OffsetDateTime.now();
    }

    public void start() {
        this.status = JobStatus.RUNNING;
    }

    public void complete(int processedCount, int errorCount, Object errorDetail) {
        this.status = JobStatus.COMPLETED;
        this.processedCount = processedCount;
        this.errorCount = errorCount;
        this.errorDetail = errorDetail == null ? null : JSON.valueToTree(errorDetail);
        this.finishedAt = OffsetDateTime.now();
    }

    public void fail(Object errorDetail) {
        this.status = JobStatus.FAILED;
        this.errorDetail = errorDetail == null ? null : JSON.valueToTree(errorDetail);
        this.finishedAt = OffsetDateTime.now();
    }

    /** 유효한 행이 하나도 없어 실패한 업로드: 오류 행 목록을 남긴다. */
    public void failWithRows(int errorCount, Object errorDetail) {
        this.errorCount = errorCount;
        fail(errorDetail);
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public void setPeriod(LocalDate start, LocalDate end) {
        this.periodStart = start;
        this.periodEnd = end;
    }

    public boolean isActive() {
        return status == JobStatus.REQUESTED || status == JobStatus.RUNNING;
    }

    public Long getId() {
        return id;
    }

    public Long getStoreId() {
        return storeId;
    }

    public JobType getType() {
        return type;
    }

    public JobStatus getStatus() {
        return status;
    }

    public String getFilePath() {
        return filePath;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public int getProcessedCount() {
        return processedCount;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public JsonNode getErrorDetail() {
        return errorDetail;
    }

    public OffsetDateTime getRequestedAt() {
        return requestedAt;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }
}
