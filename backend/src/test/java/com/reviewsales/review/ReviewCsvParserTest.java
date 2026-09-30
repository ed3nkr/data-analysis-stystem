package com.reviewsales.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.reviewsales.common.BusinessException;
import com.reviewsales.common.ErrorCode;

class ReviewCsvParserTest {

    @Test
    void parsesRowsAndErrors() {
        String csv = """
                writtenAt,content,visitedAt,rating,author
                2026-08-15,"맛있어요, 또 올게요",2026-08-14,5,맛집탐방러
                2026.08.16 13:20,김치찌개가 싱거워요,,,
                ,내용만 있음,,,a
                2026-08-17,,,,a
                2026-08-18,별점 오류,,6,a
                2026-08-19,방문일 오류,어제,,a
                """;
        ReviewCsvParser.Result r = ReviewCsvParser.parse(csv);
        assertThat(r.totalRows()).isEqualTo(6);
        assertThat(r.rows()).hasSize(2);
        var first = r.rows().get(0);
        assertThat(first.writtenAt()).isEqualTo(LocalDate.of(2026, 8, 15));
        assertThat(first.visitedAt()).isEqualTo(LocalDate.of(2026, 8, 14));
        assertThat(first.rating()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(first.content()).isEqualTo("맛있어요, 또 올게요");
        var second = r.rows().get(1);
        assertThat(second.writtenAt()).isEqualTo(LocalDate.of(2026, 8, 16));
        assertThat(second.rating()).isNull();
        assertThat(second.author()).isEmpty();
        assertThat(r.errors()).extracting(e -> e.rowNumber()).containsExactly(4L, 5L, 6L, 7L);
    }

    @Test
    void onlyRequiredColumns() {
        ReviewCsvParser.Result r = ReviewCsvParser.parse("content,writtenAt\n좋아요,2026-08-01\n");
        assertThat(r.rows()).hasSize(1);
        assertThat(r.rows().get(0).author()).isEmpty();
    }

    @Test
    void missingRequiredColumns() {
        assertThatThrownBy(() -> ReviewCsvParser.parse("content,author\nx,y\n"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code()).isEqualTo(ErrorCode.CSV_MISSING_COLUMNS);
    }

    @Test
    void rating() {
        assertThat(ReviewCsvParser.parseRating("4.5")).isEqualByComparingTo("4.5");
        assertThat(ReviewCsvParser.parseRating("0")).isEqualByComparingTo("0");
        assertThat(ReviewCsvParser.parseRating("5.1")).isNull();
        assertThat(ReviewCsvParser.parseRating("4.25")).isNull();
        assertThat(ReviewCsvParser.parseRating("-1")).isNull();
        assertThat(ReviewCsvParser.parseRating("다섯")).isNull();
    }
}
