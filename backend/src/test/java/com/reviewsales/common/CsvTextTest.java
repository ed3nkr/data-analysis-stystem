package com.reviewsales.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.reviewsales.common.csv.CsvText;

class CsvTextTest {

    private static final String SAMPLE = "판매일시,상품명,수량,결제금액\n2026-07-01 12:00:00,김치찌개(1인),1,9000\n";

    @Test
    void utf8WithBom() {
        byte[] body = SAMPLE.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[body.length + 3];
        bytes[0] = (byte) 0xEF;
        bytes[1] = (byte) 0xBB;
        bytes[2] = (byte) 0xBF;
        System.arraycopy(body, 0, bytes, 3, body.length);
        CsvText.Decoded d = CsvText.decode(bytes);
        assertThat(d.encoding()).isEqualTo("UTF-8");
        assertThat(d.text()).isEqualTo(SAMPLE);
    }

    @Test
    void utf8WithoutBom() {
        CsvText.Decoded d = CsvText.decode(SAMPLE.getBytes(StandardCharsets.UTF_8));
        assertThat(d.encoding()).isEqualTo("UTF-8");
        assertThat(d.text()).isEqualTo(SAMPLE);
    }

    @Test
    void eucKr() {
        CsvText.Decoded d = CsvText.decode(SAMPLE.getBytes(Charset.forName("EUC-KR")));
        assertThat(d.encoding()).isEqualTo("EUC-KR");
        assertThat(d.text()).isEqualTo(SAMPLE);
    }

    @Test
    void cp949ExtendedHangul() {
        // '똠' 은 EUC-KR 표준에는 없고 CP949(MS949) 확장 영역에 있다
        String s = "상품명\n똠양꿍\n";
        CsvText.Decoded d = CsvText.decode(s.getBytes(Charset.forName("MS949")));
        assertThat(d.text()).isEqualTo(s);
    }

    @Test
    void binaryRejected() {
        byte[] bytes = {(byte) 0xFF, (byte) 0xFE, 0x00, 0x41, (byte) 0x80, (byte) 0xFF};
        assertThatThrownBy(() -> CsvText.decode(bytes))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code()).isEqualTo(ErrorCode.CSV_ENCODING_UNSUPPORTED);
    }
}
