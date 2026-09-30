package com.reviewsales.review;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByStoreIdAndWrittenAtBetweenOrderByWrittenAtDescIdDesc(Long storeId, LocalDate from,
                                                                            LocalDate to, Pageable pageable);
}
