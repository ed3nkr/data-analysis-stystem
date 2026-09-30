package com.reviewsales.common.csv;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import com.reviewsales.common.BusinessException;
import com.reviewsales.common.ErrorCode;

/**
 * CSV 바이트 → 문자열. UTF-8(BOM 포함)과 EUC-KR(MS949, EUC-KR 의 상위 집합)을 자동 판별한다.
 * 판별 순서: UTF-8 BOM → 엄격한 UTF-8 디코딩 → 엄격한 MS949 디코딩 → 실패 시 CSV_ENCODING_UNSUPPORTED
 */
public final class CsvText {

    private static final Charset MS949 = Charset.forName("MS949");

    private CsvText() {
    }

    public record Decoded(String text, String encoding) {
    }

    public static Decoded decode(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            String text = strict(bytes, 3, StandardCharsets.UTF_8);
            if (text == null) {
                throw new BusinessException(ErrorCode.CSV_ENCODING_UNSUPPORTED);
            }
            return new Decoded(text, "UTF-8");
        }
        String utf8 = strict(bytes, 0, StandardCharsets.UTF_8);
        if (utf8 != null) {
            return new Decoded(utf8, "UTF-8");
        }
        String euckr = strict(bytes, 0, MS949);
        if (euckr != null) {
            return new Decoded(euckr, "EUC-KR");
        }
        throw new BusinessException(ErrorCode.CSV_ENCODING_UNSUPPORTED);
    }

    private static String strict(byte[] bytes, int offset, Charset charset) {
        try {
            String s = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset))
                    .toString();
            // 바이너리 파일(NUL 포함)은 텍스트로 보지 않는다
            return s.indexOf('\0') >= 0 ? null : s;
        } catch (CharacterCodingException e) {
            return null;
        }
    }
}
