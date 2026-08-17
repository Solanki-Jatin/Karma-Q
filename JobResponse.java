package com.karmaq.api.dto;

import com.karmaq.job.Job;
import com.karmaq.job.JobStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * What we send back to a client. Again, a separate class from Job.java -
 * we choose exactly what's exposed (no lockedBy, no internal lease details)
 * instead of accidentally leaking the whole database row over the API.
 */
public record JobResponse(
        UUID id,
        String type,
        JobStatus status,
        Instant runAt,
        int attemptCount,
        int maxAttempts,
        String lastError
) {
    /** Converts a database entity into the shape we're willing to show clients. */
    public static JobResponse from(Job job) {
        return new JobResponse(
                job.getId(),
                job.getType(),
                job.getStatus(),
                job.getRunAt(),
                job.getAttemptCount(),
                job.getMaxAttempts(),
                job.getLastError()
        );
    }
}
