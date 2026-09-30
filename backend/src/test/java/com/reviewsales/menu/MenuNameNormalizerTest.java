package com.reviewsales.menu;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MenuNameNormalizerTest {

    @ParameterizedTest(name = "[{0}] → [{1}]")
    @CsvSource(delimiter = '|', value = {
            "아메리카노(ICE)|아메리카노",
            "김치찌개(1인)|김치찌개",
            "  김치찌개 (1인)  |김치찌개",
            "된장 찌개|된장찌개",
            "Cafe Latte|cafelatte",
            "Cafe Latte[L]|cafelatte",
            "제육볶음(매운맛)(2인)|제육볶음",
            "비빔밥(곱빼기(특))|비빔밥",
            "라떼（HOT）|라떼",
            "아메리카노(ICE|아메리카노",
            "콜라)|콜라",
            "ICED AMERICANO|icedamericano",
            "Coke Zero 500ml|cokezero500ml",
    })
    void normalize(String raw, String expected) {
        assertThat(MenuNameNormalizer.normalize(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"(ICE)", "'   '", "''"})
    void emptyResults(String raw) {
        assertThat(MenuNameNormalizer.normalize(raw)).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void nullIsEmpty() {
        assertThat(MenuNameNormalizer.normalize(null)).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void fullWidthAndNbspSpacesRemoved() {
        assertThat(MenuNameNormalizer.normalize("공기　밥 (추가)")).isEqualTo("공기밥");
    }
}
