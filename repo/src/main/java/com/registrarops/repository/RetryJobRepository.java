package com.registrarops.repository;

import com.registrarops.entity.RetryJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RetryJobRepository extends JpaRepository<RetryJob, Long> {

    @Query("SELECT j FROM RetryJob j WHERE j.status = :status " +
           "AND j.nextRetryAt <= :now AND j.attemptCount < j.maxAttempts ORDER BY j.nextRetryAt ASC")
    List<RetryJob> findReady(@Param("status") RetryJob.Status status,
                             @Param("now") LocalDateTime now);

    default List<RetryJob> findReadyPending(LocalDateTime now) {
        return findReady(RetryJob.Status.PENDING, now);
    }

    List<RetryJob> findByStatus(RetryJob.Status status);
}
