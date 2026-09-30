package com.reviewsales.review;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** review 원본 저장. (store_id, dedup_key) 가 이미 있으면 건너뛴다 (ON CONFLICT DO NOTHING). */
@Component
public class ReviewWriter {

    private final JdbcTemplate jdbc;

    public ReviewWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record NewReview(LocalDate writtenAt, LocalDate visitedAt, BigDecimal rating, String content,
                            String authorHash, String dedupKey) {
    }

    public record Saved(int accepted, int duplicated) {
    }

    public Saved insert(Long storeId, Long jobId, ReviewSource source, List<NewReview> reviews) {
        if (reviews.isEmpty()) {
            return new Saved(0, 0);
        }
        List<Object[]> args = new ArrayList<>(reviews.size());
        for (NewReview r : reviews) {
            args.add(new Object[]{storeId, jobId, source.name(), r.dedupKey(), r.content(), r.rating(),
                    r.writtenAt(), r.visitedAt(), r.authorHash()});
        }
        int[] counts = jdbc.batchUpdate("""
                insert into review (store_id, job_id, source, dedup_key, content, rating, written_at, visited_at, author_hash)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (store_id, dedup_key) do nothing
                """, args);
        int accepted = 0;
        for (int c : counts) {
            accepted += c > 0 ? 1 : 0;
        }
        return new Saved(accepted, reviews.size() - accepted);
    }
}
