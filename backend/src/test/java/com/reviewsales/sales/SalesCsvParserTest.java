package com.reviewsales.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.reviewsales.common.BusinessException;
import com.reviewsales.common.ErrorCode;

class SalesCsvParserTest {

    @Test
    void parsesValidRowsAndCollectsErrors() {
        String csv = """
                판매일시,상품명,수량,결제금액
                2026-07-01 12:00:00,김치찌개(1인),1,9000
                2026/07/01 12:05,"제육볶음, 곱빼기",2,"22,000"
                2026-07-32 12:00:00,비빔밥,1,9000
                2026-07-01 13:00:00,,1,9000
                2026-07-01 13:00:00,콜라,abc,2000
                2026-07-01 13:00:00,콜라,0,0
                2026-07-01 13:00:00,콜라,1,-500
                2026-07-01 13:00:00,콜라

                2026-07-02 18:30:00,갈비탕,1,13000원
                """;
        SalesCsvParser.Result r = SalesCsvParser.parse(csv, null);

        assertThat(r.totalRows()).isEqualTo(9);
        assertThat(r.rows()).extracting(SalesCsvParser.SalesRow::menuName)
                .containsExactly("김치찌개(1인)", "제육볶음, 곱빼기", "갈비탕");
        assertThat(r.rows().get(1).amount()).isEqualTo(22000);
        assertThat(r.rows().get(1).soldAt()).isEqualTo(LocalDateTime.of(2026, 7, 1, 12, 5));
        assertThat(r.rows().get(2).amount()).isEqualTo(13000);
        // 빈 줄 다음 행도 파일 기준 행 번호를 유지한다
        assertThat(r.rows().get(2).rowNumber()).isEqualTo(11);

        assertThat(r.errors()).hasSize(6);
        assertThat(r.errors()).extracting(SalesCsvParser.RowError::rowNumber)
                .containsExactly(4L, 5L, 6L, 7L, 8L, 9L);
        assertThat(r.errors().get(0).reason()).contains("판매일시");
        assertThat(r.errors().get(0).rawLine()).isEqualTo("2026-07-32 12:00:00,비빔밥,1,9000");
        assertThat(r.errors().get(1).reason()).contains("상품명");
        assertThat(r.errors().get(2).reason()).contains("수량 형식");
        assertThat(r.errors().get(3).reason()).contains("수량은 1 이상");
        assertThat(r.errors().get(4).reason()).contains("결제금액은 0 이상");
        assertThat(r.errors().get(5).reason()).contains("열 개수");
    }

    @Test
    void missingColumnsReported() {
        String csv = "판매일시,메뉴,수량\n2026-07-01 12:00:00,김치찌개,1\n";
        assertThatThrownBy(() -> SalesCsvParser.parse(csv, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.code()).isEqualTo(ErrorCode.CSV_MISSING_COLUMNS);
                    assertThat(be.data()).isEqualTo(Map.of("missing", List.of("상품명", "결제금액")));
                });
    }

    @Test
    void columnMapping() {
        String csv = "일시,메뉴,개수,매출액,비고\n2026-07-01 12:00,김치찌개,2,18000,x\n";
        Map<String, String> mapping = Map.of("soldAt", "일시", "menuName", "메뉴", "quantity", "개수", "amount", "매출액");
        SalesCsvParser.Result r = SalesCsvParser.parse(csv, mapping);
        assertThat(r.rows()).singleElement().satisfies(row -> {
            assertThat(row.quantity()).isEqualTo(2);
            assertThat(row.amount()).isEqualTo(18000);
        });
    }

    @Test
    void partialMappingFallsBackToDefaults() {
        String csv = "판매일시,메뉴,수량,결제금액\n2026-07-01 12:00,김치찌개,1,9000\n";
        SalesCsvParser.Result r = SalesCsvParser.parse(csv, Map.of("menuName", "메뉴"));
        assertThat(r.rows()).hasSize(1);
    }

    @Test
    void unknownMappingKeyRejected() {
        assertThatThrownBy(() -> SalesCsvParser.parse("a\n1\n", Map.of("price", "금액")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code()).isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void headerWithBomAndSpaces() {
        String csv = "﻿ 판매일시 , 상품명 ,수량,결제금액\n2026-07-01 12:00,김치찌개,1,9000\n";
        assertThat(SalesCsvParser.parse(csv, null).rows()).hasSize(1);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "2026-07-01 12:00:00|2026-07-01T12:00:00",
            "2026-07-01 12:00|2026-07-01T12:00",
            "2026-7-1 9:05|2026-07-01T09:05",
            "2026/07/01 12:00:30|2026-07-01T12:00:30",
            "2026.07.01 12:00|2026-07-01T12:00",
            "2026-07-01T12:00:00|2026-07-01T12:00:00",
            "20260701120000|2026-07-01T12:00:00",
            "2026-07-01|2026-07-01T00:00",
    })
    void dateFormats(String raw, String expected) {
        assertThat(SalesCsvParser.parseDateTime(raw)).isEqualTo(LocalDateTime.parse(expected));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"2026-02-30 12:00", "2026-13-01 12:00", "2026-07-01 25:00", "어제", "''"})
    void invalidDates(String raw) {
        assertThat(SalesCsvParser.parseDateTime(raw)).isNull();
    }

    @Test
    void numbers() {
        assertThat(SalesCsvParser.parseLong("1,000")).isEqualTo(1000L);
        assertThat(SalesCsvParser.parseLong(" 9000원 ")).isEqualTo(9000L);
        assertThat(SalesCsvParser.parseLong("₩13,000")).isEqualTo(13000L);
        assertThat(SalesCsvParser.parseLong("2.0")).isEqualTo(2L);
        assertThat(SalesCsvParser.parseLong("1.5")).isNull();
        assertThat(SalesCsvParser.parseLong("하나")).isNull();
        assertThat(SalesCsvParser.parseLong("")).isNull();
    }
}
