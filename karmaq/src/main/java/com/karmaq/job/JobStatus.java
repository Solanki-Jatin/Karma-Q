package com.karmaq.job;

/**
 * Lifecycle states for a job.
 *
 * PENDING      -> job is due (or will become due) and waiting to be claimed by a worker
 * IN_PROGRESS  -> a worker has claimed the job and is executing it
 * SUCCEEDED    -> job completed without error
 * FAILED       -> job errored and has exhausted its retry attempts
 * DEAD_LETTER  -> job permanently failed and is parked for manual inspection
 * CANCELLED    -> job was cancelled by the client before execution
 */
public enum JobStatus {
    PENDING,
    IN_PROGRESS,
    SUCCEEDED,
    FAILED,
    DEAD_LETTER,
    CANCELLED
}
