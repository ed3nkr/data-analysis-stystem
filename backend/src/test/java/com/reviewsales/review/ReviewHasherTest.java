package com.reviewsales.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** 기대값은 Python(hashlib) 으로 계산한 값이다. collector/tests/test_hashing.py 와 같은 벡터를 쓴다. */
class ReviewHasherTest {

    private final ReviewHasher hasher = new ReviewHasher("salt123");
    private static final LocalDate DAY = LocalDate.of(2026, 8, 15);

    @Test
    void authorHash() {
        assertThat(hasher.authorHash("맛집탐방러"))
                .isEqualTo("fa6459ba378232302a2a89b5f9f41b6ac490e30c5ebf37bbfbaee48fcbbce27c");
        assertThat(hasher.authorHash("  맛집탐방러 ")).isEqualTo(hasher.authorHash("맛집탐방러"));
        assertThat(hasher.authorHash("")).isNull();
        assertThat(hasher.authorHash(null)).isNull();
    }

    @Test
    void dedupKeyUsesFirst50Chars() {
        String a = hasher.authorHash("맛집탐방러");
        String content = "김치찌개가 예전보다 많이 싱거워졌어요. 고기도 줄어든 느낌이에요. 다음엔 다른 메뉴를 먹어볼게요 😀 끝";
        assertThat(ReviewHasher.dedupKey(DAY, a, content))
                .isEqualTo("e3783c1c9e488865970c0ece21eabf992d55bf43b15afa13742f35573004e434");
        // 50자 이후가 달라도 같은 키
        assertThat(ReviewHasher.dedupKey(DAY, a, content + " 추가 문장")).isEqualTo(ReviewHasher.dedupKey(DAY, a, content));
    }

    @Test
    void dedupKeyWithoutAuthor() {
        assertThat(ReviewHasher.dedupKey(DAY, null, "짧은 리뷰"))
                .isEqualTo("86eaf425f5a2dca8ad2c4eb4721a178425c99c060e327dee1fad46bbb3f6bdeb");
    }

    @Test
    void codePointsNotUtf16Units() {
        // 이모지(서로게이트 쌍)도 1자로 센다 (Python 과 동일)
        assertThat(ReviewHasher.dedupKey(DAY, null, "😀".repeat(60)))
                .isEqualTo("288dc86a77ac29b93d5912063b42bc9b17a24d6b46df93adffe8da13b6a7398b");
    }
}
