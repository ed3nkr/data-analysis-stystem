package com.reviewsales.review;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 리뷰. 원본 컬럼은 저장 후 수정하지 않는다 (저장은 ReviewWriter 가 담당). 분석 결과 컬럼은 5주차 이후 채운다. */
@Entity
@Table(name = "review")
public class Review {

    @Id
    private Long id;

    @Column(name = "store_id", nullable = false, updatable = false)
    private Long storeId;

    @Column(name = "job_id", nullable = false, updatable = false)
    private Long jobId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ReviewSource source;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "dedup_key", length = 64, nullable = false, updatable = false)
    private String dedupKey;

    // --- 원본 ---
    @Column(nullable = false, updatable = false)
    private String content;

    @Column(precision = 2, scale = 1, updatable = false)
    private BigDecimal rating;

    @Column(name = "written_at", nullable = false, updatable = false)
    private LocalDate writtenAt;

    @Column(name = "visited_at", updatable = false)
    private LocalDate visitedAt;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "author_hash", length = 64, updatable = false)
    private String authorHash;

    // --- 분석 결과 (지금은 NULL) ---
    private Float reliability;

    private Boolean excluded;

    @Column(name = "exclude_reason")
    private String excludeReason;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "neg_types", columnDefinition = "text[]")
    private List<String> negTypes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "neg_probs", columnDefinition = "jsonb")
    private JsonNode negProbs;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "menu_ids", columnDefinition = "bigint[]")
    private List<Long> menuIds;

    @Column(name = "time_slot")
    private String timeSlot;

    @Column(name = "model_version")
    private String modelVersion;

    @Column(name = "analyzed_at")
    private OffsetDateTime analyzedAt;

    protected Review() {
    }

    public Long getId() {
        return id;
    }

    public ReviewSource getSource() {
        return source;
    }

    public String getContent() {
        return content;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public LocalDate getWrittenAt() {
        return writtenAt;
    }

    public LocalDate getVisitedAt() {
        return visitedAt;
    }

    public Float getReliability() {
        return reliability;
    }

    public Boolean getExcluded() {
        return excluded;
    }

    public String getExcludeReason() {
        return excludeReason;
    }

    public List<String> getNegTypes() {
        return negTypes;
    }

    public JsonNode getNegProbs() {
        return negProbs;
    }

    public List<Long> getMenuIds() {
        return menuIds;
    }

    public String getTimeSlot() {
        return timeSlot;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public OffsetDateTime getAnalyzedAt() {
        return analyzedAt;
    }
}
