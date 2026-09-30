package com.reviewsales.job;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<Job, Long> {

    Page<Job> findByStoreIdOrderByRequestedAtDesc(Long storeId, Pageable pageable);

    Page<Job> findByStoreIdAndTypeOrderByRequestedAtDesc(Long storeId, JobType type, Pageable pageable);

    Optional<Job> findByIdAndStoreIdAndType(Long id, Long storeId, JobType type);

    boolean existsByStoreIdAndTypeAndStatusIn(Long storeId, JobType type, Collection<JobStatus> statuses);

    boolean existsByStoreIdAndTypeAndRequestedAtAfter(Long storeId, JobType type, OffsetDateTime after);

    List<Job> findByStoreIdAndTypeAndStatus(Long storeId, JobType type, JobStatus status);

    List<Job> findByStatusIn(Collection<JobStatus> statuses);
}
