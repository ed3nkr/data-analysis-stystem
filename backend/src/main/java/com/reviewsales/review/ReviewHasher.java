package com.reviewsales.review;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import com.reviewsales.config.AppProperties;

/**
 * 리뷰 식별용 해시. collector(Python) 의 hashing.py 와 반드시 같은 규칙을 쓴다.
 * <pre>
 * author_hash = SHA-256(닉네임.strip() + salt)          — 닉네임이 없으면 null (원문은 저장하지 않음)
 * dedup_key   = SHA-256(writtenAt(yyyy-MM-dd) + "|" + (author_hash 또는 "") + "|" + content.strip() 앞 50자)
 * </pre>
 * "50자"는 유니코드 코드포인트 기준 (Python 문자열 슬라이싱과 동일).
 */
@Component
public class ReviewHasher {

    private static final Logger log = LoggerFactory.getLogger(ReviewHasher.class);
    static final String LOCAL_DEV_SALT = "local-dev-salt";

    private final String salt;

    @Autowired
    public ReviewHasher(AppProperties props, Environment env) {
        String s = props.authorHashSalt();
        if (s == null || s.isBlank()) {
            if (!env.acceptsProfiles(Profiles.of("local", "test"))) {
                throw new IllegalStateException("AUTHOR_HASH_SALT 환경변수가 필요합니다.");
            }
            log.warn("AUTHOR_HASH_SALT 가 없어 개발용 salt 를 사용합니다. collector 도 같은 값을 써야 합니다.");
            s = LOCAL_DEV_SALT;
        }
        this.salt = s;
    }

    ReviewHasher(String salt) {
        this.salt = salt;
    }

    public String authorHash(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return null;
        }
        return sha256(nickname.strip() + salt);
    }

    public static String dedupKey(LocalDate writtenAt, String authorHash, String content) {
        String body = content == null ? "" : content.strip();
        int end = body.offsetByCodePoints(0, Math.min(50, body.codePointCount(0, body.length())));
        return sha256(writtenAt + "|" + (authorHash == null ? "" : authorHash) + "|" + body.substring(0, end));
    }

    static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
