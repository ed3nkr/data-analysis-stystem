package com.reviewsales.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MenuMatcherTest {

    private static Menu menu(long id, String posName, String... aliases) {
        Menu m = new Menu(1L, posName, List.of(aliases));
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    private final MenuMatcher matcher = new MenuMatcher(List.of(
            menu(1, "김치찌개(1인)", "김찌"),
            menu(2, "아메리카노", "아아", "Iced Americano"),
            menu(3, "제육볶음")));

    @Test
    void matchesByNormalizedName() {
        assertThat(matcher.match("김치찌개(2인)")).isEqualTo(1L);
        assertThat(matcher.match(" 김치 찌개 ")).isEqualTo(1L);
        assertThat(matcher.match("아메리카노(ICE)")).isEqualTo(2L);
    }

    @Test
    void matchesByAlias() {
        assertThat(matcher.match("김찌")).isEqualTo(1L);
        assertThat(matcher.match("ICED AMERICANO (L)")).isEqualTo(2L);
        assertThat(matcher.match("아아")).isEqualTo(2L);
    }

    @Test
    void unmatchedReturnsNull() {
        assertThat(matcher.match("된장찌개")).isNull();
        assertThat(matcher.match("(ICE)")).isNull();
    }

    @Test
    void normalizedNameWinsOverAlias() {
        MenuMatcher m = new MenuMatcher(List.of(menu(10, "라떼", "카페라떼"), menu(11, "카페라떼")));
        assertThat(m.match("카페라떼")).isEqualTo(11L);
    }
}
