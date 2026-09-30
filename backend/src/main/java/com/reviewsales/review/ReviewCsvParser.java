package com.reviewsales.review;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.reviewsales.common.BusinessException;
import com.reviewsales.common.ErrorCode;
import com.reviewsales.common.csv.CsvTable;
import com.reviewsales.sales.SalesCsvParser.RowError;

/** 리뷰 CSV 파서. 열: writtenAt, content, visitedAt, rating, author (writtenAt·content 필수) */
public final class ReviewCsvParser {

    static final List<String> REQUIRED = List.of("writtenAt", "content");
    private static final Pattern DATE_PREFIX = Pattern.compile("^(\\d{4})[-./](\\d{1,2})[-./](\\d{1,2})");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("uuuu-M-d")
            .withResolverStyle(ResolverStyle.STRICT);

    private ReviewCsvParser() {
    }

    public record ReviewRow(long rowNumber, LocalDate writtenAt, LocalDate visitedAt, BigDecimal rating,
                            String content, String author) {
    }

    public record Result(int totalRows, List<ReviewRow> rows, List<RowError> errors) {
    }

    public static Result parse(String text) {
        CsvTable table = CsvTable.parse(text);
        List<String> missing = REQUIRED.stream().filter(c -> !table.headers().contains(c)).toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.CSV_MISSING_COLUMNS,
                    "CSV 에 필수 열이 없습니다: " + String.join(", ", missing), Map.of("missing", missing));
        }
        List<ReviewRow> rows = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        for (CsvTable.Row r : table.rows()) {
            String error = null;
            LocalDate writtenAt = parseDate(r.get("writtenAt"));
            LocalDate visitedAt = null;
            BigDecimal rating = null;
            String content = r.get("content").strip();
            if (writtenAt == null) {
                error = "writtenAt 형식 오류: '" + r.get("writtenAt") + "'";
            } else if (content.isEmpty()) {
                error = "content 가 비어 있습니다";
            } else if (!r.get("visitedAt").isBlank() && (visitedAt = parseDate(r.get("visitedAt"))) == null) {
                error = "visitedAt 형식 오류: '" + r.get("visitedAt") + "'";
            } else if (!r.get("rating").isBlank()) {
                rating = parseRating(r.get("rating"));
                if (rating == null) {
                    error = "rating 은 0~5 사이 숫자여야 합니다: '" + r.get("rating") + "'";
                }
            }
            if (error != null) {
                errors.add(new RowError(r.lineNumber(), error, r.rawLine()));
            } else {
                rows.add(new ReviewRow(r.lineNumber(), writtenAt, visitedAt, rating, content, r.get("author")));
            }
        }
        return new Result(table.rows().size(), rows, errors);
    }

    /** yyyy-MM-dd (구분자 - . / 허용, 뒤에 시간이 붙어 있으면 날짜만 사용) */
    static LocalDate parseDate(String value) {
        Matcher m = DATE_PREFIX.matcher(value == null ? "" : value.strip());
        if (!m.find()) {
            return null;
        }
        try {
            return LocalDate.parse(m.group(1) + "-" + m.group(2) + "-" + m.group(3), ISO);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static BigDecimal parseRating(String value) {
        try {
            BigDecimal r = new BigDecimal(value.strip());
            if (r.signum() < 0 || r.compareTo(BigDecimal.valueOf(5)) > 0 || r.scale() > 1) {
                return null;
            }
            return r;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
