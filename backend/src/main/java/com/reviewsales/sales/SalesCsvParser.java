package com.reviewsales.sales;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.reviewsales.common.BusinessException;
import com.reviewsales.common.ErrorCode;
import com.reviewsales.common.csv.CsvTable;

/**
 * 매출 CSV 파서. 기본 열: 판매일시, 상품명, 수량, 결제금액 (columnMapping 으로 변경 가능)
 * 잘못된 행은 건너뛰고 {rowNumber, reason, rawLine} 로 모은다.
 */
public final class SalesCsvParser {

    public static final Map<String, String> DEFAULT_COLUMNS = Map.of(
            "soldAt", "판매일시",
            "menuName", "상품명",
            "quantity", "수량",
            "amount", "결제금액");
    private static final List<String> KEYS = List.of("soldAt", "menuName", "quantity", "amount");

    private static final List<DateTimeFormatter> DATE_TIME_FORMATS = List.of(
            f("uuuu-M-d H:mm[:ss]"), f("uuuu/M/d H:mm[:ss]"), f("uuuu.M.d H:mm[:ss]"),
            f("uuuu-M-d'T'H:mm[:ss]"), f("uuuu. M. d. H:mm[:ss]"), f("uuuuMMddHHmmss"));
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            f("uuuu-M-d"), f("uuuu/M/d"), f("uuuu.M.d"), f("uuuuMMdd"));
    private static final Pattern NUMBER_NOISE = Pattern.compile("[,\\s원₩]");

    private SalesCsvParser() {
    }

    public record SalesRow(long rowNumber, LocalDateTime soldAt, String menuName, int quantity, long amount) {
    }

    public record RowError(Long rowNumber, String reason, String rawLine) {
    }

    public record Result(int totalRows, List<SalesRow> rows, List<RowError> errors) {
    }

    /** @param mapping 사용자 매핑(키: soldAt/menuName/quantity/amount → CSV 열 이름). null/빈 값은 기본 열 사용 */
    public static Result parse(String text, Map<String, String> mapping) {
        Map<String, String> columns = resolveColumns(mapping);
        CsvTable table = CsvTable.parse(text);

        List<String> missing = columns.values().stream().filter(c -> !table.headers().contains(c)).toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.CSV_MISSING_COLUMNS,
                    "CSV 에 필수 열이 없습니다: " + String.join(", ", missing), Map.of("missing", missing));
        }

        List<SalesRow> rows = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        for (CsvTable.Row r : table.rows()) {
            String error = null;
            LocalDateTime soldAt = null;
            int quantity = 0;
            long amount = 0;
            String menuName = r.get(columns.get("menuName"));

            if (columns.values().stream().anyMatch(c -> !r.values().containsKey(c))) {
                error = "열 개수가 부족합니다";
            } else if ((soldAt = parseDateTime(r.get(columns.get("soldAt")))) == null) {
                error = "판매일시 형식 오류: '" + r.get(columns.get("soldAt")) + "'";
            } else if (menuName.isBlank()) {
                error = "상품명이 비어 있습니다";
            } else {
                Long q = parseLong(r.get(columns.get("quantity")));
                Long a = parseLong(r.get(columns.get("amount")));
                if (q == null || q > Integer.MAX_VALUE) {
                    error = "수량 형식 오류: '" + r.get(columns.get("quantity")) + "'";
                } else if (q < 1) {
                    error = "수량은 1 이상이어야 합니다: " + q;
                } else if (a == null) {
                    error = "결제금액 형식 오류: '" + r.get(columns.get("amount")) + "'";
                } else if (a < 0) {
                    error = "결제금액은 0 이상이어야 합니다: " + a;
                } else {
                    quantity = q.intValue();
                    amount = a;
                }
            }

            if (error != null) {
                errors.add(new RowError(r.lineNumber(), error, r.rawLine()));
            } else {
                rows.add(new SalesRow(r.lineNumber(), soldAt, menuName.strip(), quantity, amount));
            }
        }
        return new Result(table.rows().size(), rows, errors);
    }

    static Map<String, String> resolveColumns(Map<String, String> mapping) {
        Map<String, String> columns = new LinkedHashMap<>();
        for (String key : KEYS) {
            String custom = mapping == null ? null : mapping.get(key);
            columns.put(key, custom != null && !custom.isBlank() ? custom.strip() : DEFAULT_COLUMNS.get(key));
        }
        if (mapping != null) {
            for (String key : mapping.keySet()) {
                if (!KEYS.contains(key)) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT,
                            "columnMapping 키는 soldAt, menuName, quantity, amount 만 쓸 수 있습니다: " + key);
                }
            }
        }
        return columns;
    }

    static LocalDateTime parseDateTime(String value) {
        String v = value == null ? "" : value.strip();
        if (v.isEmpty()) {
            return null;
        }
        for (DateTimeFormatter fmt : DATE_TIME_FORMATS) {
            try {
                return LocalDateTime.parse(v, fmt);
            } catch (DateTimeParseException ignore) {
                // 다음 형식
            }
        }
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(v, fmt).atStartOfDay();
            } catch (DateTimeParseException ignore) {
                // 다음 형식
            }
        }
        return null;
    }

    static Long parseLong(String value) {
        String v = NUMBER_NOISE.matcher(value == null ? "" : value).replaceAll("");
        if (v.endsWith(".0")) {
            v = v.substring(0, v.length() - 2);
        }
        if (!v.matches("-?\\d{1,18}")) {
            return null;
        }
        return Long.parseLong(v);
    }

    private static DateTimeFormatter f(String pattern) {
        return DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT);
    }
}
