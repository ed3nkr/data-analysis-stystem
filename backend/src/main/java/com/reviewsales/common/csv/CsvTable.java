package com.reviewsales.common.csv;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import com.reviewsales.common.BusinessException;
import com.reviewsales.common.ErrorCode;

/** 헤더가 있는 CSV 를 읽어 행 목록으로 만든다. 행마다 파일 기준 행 번호와 원문 줄을 함께 보관한다. */
public final class CsvTable {

    private final List<String> headers;
    private final List<Row> rows;

    private CsvTable(List<String> headers, List<Row> rows) {
        this.headers = headers;
        this.rows = rows;
    }

    /**
     * @param lineNumber 파일에서의 행 번호 (헤더가 1)
     * @param rawLine    원문 줄 (앞뒤 공백 제거)
     * @param values     열 이름 → 값 (앞뒤 공백 제거, 없는 열은 map 에 없음)
     */
    public record Row(long lineNumber, String rawLine, Map<String, String> values) {
        public String get(String column) {
            String v = values.get(column);
            return v == null ? "" : v;
        }
    }

    public static CsvTable parse(String text) {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .setAllowMissingColumnNames(true)
                .get();
        try (CSVParser parser = CSVParser.builder().setReader(new StringReader(text)).setFormat(format).get()) {
            List<String> headers = parser.getHeaderNames().stream().map(CsvTable::cleanHeader).toList();
            List<String> rawHeaders = parser.getHeaderNames();
            List<Row> rows = new ArrayList<>();
            List<CSVRecord> records = parser.getRecords();
            long line = 1;
            int scanned = 0;
            for (int i = 0; i < records.size(); i++) {
                CSVRecord r = records.get(i);
                int start = (int) r.getCharacterPosition();
                // 앞의 빈 줄은 건너뛴 위치가 레코드 시작으로 잡히므로 줄바꿈을 넘겨 실제 시작으로 맞춘다
                while (start < text.length() && (text.charAt(start) == '\n' || text.charAt(start) == '\r')) {
                    start++;
                }
                int end = i + 1 < records.size() ? (int) records.get(i + 1).getCharacterPosition() : text.length();
                String raw = text.substring(Math.min(start, text.length()), Math.min(end, text.length())).strip();
                Map<String, String> values = new LinkedHashMap<>();
                for (int c = 0; c < rawHeaders.size() && c < r.size(); c++) {
                    values.put(headers.get(c), r.get(c));
                }
                // 헤더가 1행이므로 첫 데이터 행 = 2행. 여러 줄 필드가 있으면 시작 줄 기준.
                for (; scanned < start && scanned < text.length(); scanned++) {
                    if (text.charAt(scanned) == '\n') {
                        line++;
                    }
                }
                rows.add(new Row(line, raw, values));
            }
            return new CsvTable(headers, rows);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "CSV 형식을 읽을 수 없습니다: " + e.getMessage());
        }
    }

    private static String cleanHeader(String h) {
        return h == null ? "" : h.replace("﻿", "").strip();
    }

    public List<String> headers() {
        return headers;
    }

    public List<Row> rows() {
        return rows;
    }
}
