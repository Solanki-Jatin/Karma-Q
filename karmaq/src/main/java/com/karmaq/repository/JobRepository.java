package com.karmaq.repository;

import com.karmaq.job.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {

    /**
     * Finds jobs eligible for a worker to claim right now:
     *   - PENDING and due (run_at <= now), OR
     *   - IN_PROGRESS but the worker lease has expired (crash recovery)
     *
     * FOR UPDATE SKIP LOCKED lets multiple worker processes poll concurrently
     * without blocking on each other or double-claiming a row - this is the
     * mechanism that replaces a separate message broker (Week 2).
     *
     * NOTE: this method just intentionally documents the query shape for
     * Day 1. The @Query/@Lock combination below will be wired up and tested
     * once the worker polling loop lands in Week 2.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = """
            SELECT * FROM jobs
            WHERE (status = 'PENDING' AND run_at <= :now)
               OR (status = 'IN_PROGRESS' AND locked_at < :leaseExpiry)
            ORDER BY run_at ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Job> findClaimableJobs(
            @Param("now") Instant now,
            @Param("leaseExpiry") Instant leaseExpiry,
            @Param("batchSize") int batchSize
    );
}
