package com.karmaq.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/**
 * What a client sends us to create a job. This is deliberately a different
 * class from Job.java (the database entity) - the client shouldn't be able
 * to set internal fields like status, attemptCount, or lockedBy. They only
 * get to say WHAT to run and WHEN.
 *
 * Exactly one of runAt / cronExpression should be set:
 *   - runAt only        -> one-time job, fires once at that instant
 *   - cronExpression only -> recurring job, runAt is computed by the server
 */
public record CreateJobRequest(
        @NotBlank String type,
        String payload,
        Instant runAt,
        String cronExpression,
        String idempotencyKey
) {
}
