package com.karmaq.job;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A unit of schedulable work.
 *
 * A job is either one-time (runAt is set, cronExpression is null) or recurring
 * (cronExpression is set; runAt holds the next computed fire time).
 *
 * Concurrency note: workers claim rows using
 *   SELECT ... FOR UPDATE SKIP LOCKED
 * so multiple worker processes can safely poll the same table without
 * double-claiming a job. See JobRepository for the claim query.
 */
@Entity
@Table(name = "jobs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Job {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    /** Logical job type - used by the worker to dispatch to the right handler. */
    @Column(nullable = false)
    private String type;

    /** Arbitrary JSON payload passed to the job handler at execution time. */
    @Column(columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private JobStatus status = JobStatus.PENDING;

    /** When this job is next due to run. */
    @Column(name = "run_at", nullable = false)
    private Instant runAt;

    /** Null for one-time jobs; set for recurring jobs. */
    @Column(name = "cron_expression")
    private String cronExpression;

    /** Prevents double-execution when a job is retried or reclaimed after a crash. */
    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Builder.Default
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Builder.Default
    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 5;

    /** Set when a worker claims the job; used for crash-recovery lease expiry. */
    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by")
    private String lockedBy;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
