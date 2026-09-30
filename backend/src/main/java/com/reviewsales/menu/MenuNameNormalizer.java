package com.reviewsales.menu;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 메뉴명 정규화.
 * 앞뒤 공백 제거 → 괄호와 괄호 안 옵션 제거 → 내부 공백 제거 → 영문 소문자화
 * 예) "아메리카노(ICE)" → "아메리카노", " 김치찌개 (1인) " → "김치찌개", "Cafe Latte[L]" → "cafelatte"
 */
public final class MenuNameNormalizer {

    /** 가장 안쪽 괄호 쌍 (소/대/중괄호, 전각 괄호) */
    private static final Pattern INNER_BRACKETS =
            Pattern.compile("\\([^()]*\\)|\\[[^\\[\\]]*]|\\{[^{}]*}|（[^（）]*）");
    /** 짝이 맞지 않는 여는 괄호부터 끝까지 (예: "아메리카노(ICE") */
    private static final Pattern DANGLING_OPEN = Pattern.compile("[(\\[{（].*$");
    private static final Pattern STRAY_CLOSE = Pattern.compile("[)\\]}）]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00A0\\u3000]+");

    private MenuNameNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.strip();
        String prev;
        do { // 중첩 괄호는 안쪽부터 반복 제거
            prev = s;
            s = INNER_BRACKETS.matcher(s).replaceAll("");
        } while (!s.equals(prev));
        s = DANGLING_OPEN.matcher(s).replaceAll("");
        s = STRAY_CLOSE.matcher(s).replaceAll("");
        s = WHITESPACE.matcher(s).replaceAll("");
        return s.toLowerCase(Locale.ROOT);
    }
}
