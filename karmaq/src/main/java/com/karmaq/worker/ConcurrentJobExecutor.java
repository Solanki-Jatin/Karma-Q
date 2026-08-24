package com.karmaq.worker;

import com.karmaq.job.Job;
import com.karmaq.job.JobStatus;
import com.karmaq.repository.JobRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Replaces SimpleJobExecutor (Day 3) - delete that file when adding this one.
 *
 * The key upgrade: instead of findAll().filter(...) on one thread, this uses
 * JobRepository.findClaimableJobs() - the SELECT ... FOR UPDATE SKIP LOCKED
 * query that's existed since Day 1 but wasn't used until now.
 *
 * Each poll cycle has two distinct phases:
 *
 *   1. claimDueJobs() - runs in ONE short database transaction. It selects
 *      and locks a batch of due jobs, immediately marks them IN_PROGRESS,
 *      and commits. Once committed, those rows no longer look PENDING to
 *      anyone else's SKIP LOCKED query - including another KarmaQ instance
 *      polling the same table. This is what makes it safe to eventually
 *      run multiple copies of this app without double-executing a job.
 *
 *   2. Each claimed job is handed to jobWorkerPool (a plain thread pool) to
 *      actually run - deliberately OUTSIDE the claim transaction, so a
 *      slow-running job doesn't hold a database lock the whole time it
 *      executes.
 *
 * workerId exists so that if you look at a job's locked_by column later,
 * you can tell which worker instance/thread ran it - useful for debugging
 * and for the crash-recovery logic coming in Week 3.
 */
@Component
public class ConcurrentJobExecutor {

    private static final Logger log = LoggerFactory.getLogger(ConcurrentJobExecutor.class);
    private final String workerId = "worker-" + UUID.randomUUID().toString().substring(0, 8);

    private final JobRepository jobRepository;
    private final JobHandlerRegistry handlerRegistry;
    private final CronScheduler cronScheduler;
    private final ExecutorService jobWorkerPool;
    private final int leaseDurationSeconds;
    private final int batchSize;

    private final Counter jobsExecutedCounter;
    private final Counter jobsSucceededCounter;
    private final Counter jobsFailedCounter;
    private final Counter jobsRetriedCounter;
    private final Counter jobsDeadLetteredCounter;

    public ConcurrentJobExecutor(
            JobRepository jobRepository,
            JobHandlerRegistry handlerRegistry,
            CronScheduler cronScheduler,
            ExecutorService jobWorkerPool,
            MeterRegistry meterRegistry,
            @Value("${karmaq.worker.lease-duration-seconds:30}") int leaseDurationSeconds,
            @Value("${karmaq.worker.batch-size:10}") int batchSize
    ) {
        this.jobRepository = jobRepository;
        this.handlerRegistry = handlerRegistry;
        this.cronScheduler = cronScheduler;
        this.jobWorkerPool = jobWorkerPool;
        this.leaseDurationSeconds = leaseDurationSeconds;
        this.batchSize = batchSize;

        this.jobsExecutedCounter = meterRegistry.counter("karmaq.jobs.executed");
        this.jobsSucceededCounter = meterRegistry.counter("karmaq.jobs.succeeded");
        this.jobsFailedCounter = meterRegistry.counter("karmaq.jobs.failed");
        this.jobsRetriedCounter = meterRegistry.counter("karmaq.jobs.retried");
        this.jobsDeadLetteredCounter = meterRegistry.counter("karmaq.jobs.dead_letter");
    }

    @Scheduled(fixedDelayString = "${karmaq.worker.poll-interval-ms:1000}")
    public void pollAndDispatch() {
        List<Job> claimed = claimDueJobs();
        for (Job job : claimed) {
            jobWorkerPool.submit(() -> execute(job));
        }
    }

    @Transactional
    protected List<Job> claimDueJobs() {
        Instant now = Instant.now();
        Instant leaseExpiry = now.minusSeconds(leaseDurationSeconds);

        List<Job> jobs = jobRepository.findClaimableJobs(now, leaseExpiry, batchSize);
        for (Job job : jobs) {
            job.setStatus(JobStatus.IN_PROGRESS);
            job.setLockedAt(now);
            job.setLockedBy(workerId);
        }
        return jobRepository.saveAll(jobs);
    }

    private void execute(Job job) {
        jobsExecutedCounter.increment();

        var handler = handlerRegistry.find(job.getType());
        if (handler.isEmpty()) {
            job.setStatus(JobStatus.FAILED);
            job.setLastError("No handler registered for type: " + job.getType());
            jobRepository.save(job);
            jobsFailedCounter.increment();
            log.warn("No handler for job {} of type {}", job.getId(), job.getType());
            return;
        }

        try {
            handler.get().handle(job);
            if (job.getCronExpression() != null) {
                // Recurring job: compute the next fire time and go back to
                // PENDING instead of SUCCEEDED - it never "finishes".
                job.setStatus(JobStatus.PENDING);
                job.setRunAt(cronScheduler.nextFireTime(job.getCronExpression()));
                job.setAttemptCount(0);
            } else {
                job.setStatus(JobStatus.SUCCEEDED);
            }
            jobsSucceededCounter.increment();
            jobRepository.save(job);
        } catch (Exception e) {
            handleFailure(job, e);
        }
    }

    /**
     * On failure: increment the attempt count, then either reschedule with
     * exponential backoff (1m, 2m, 4m, 8m, ...) or, once maxAttempts is
     * exhausted, park the job in DEAD_LETTER for manual inspection.
     *
     * Rescheduling sets status back to PENDING with a future runAt - the
     * SAME claim query picks it back up naturally next time it's due, no
     * separate retry mechanism needed.
     */
    private void handleFailure(Job job, Exception e) {
        job.setAttemptCount(job.getAttemptCount() + 1);
        job.setLastError(e.getMessage());

        if (job.getAttemptCount() >= job.getMaxAttempts()) {
            job.setStatus(JobStatus.DEAD_LETTER);
            jobsDeadLetteredCounter.increment();
            log.error("Job {} exhausted {} attempts, moving to DEAD_LETTER",
                    job.getId(), job.getMaxAttempts(), e);
        } else {
            long backoffSeconds = (long) Math.pow(2, job.getAttemptCount()) * 30;
            job.setStatus(JobStatus.PENDING);
            job.setRunAt(Instant.now().plusSeconds(backoffSeconds));
            jobsRetriedCounter.increment();
            log.warn("Job {} failed (attempt {}/{}), retrying in {}s",
                    job.getId(), job.getAttemptCount(), job.getMaxAttempts(), backoffSeconds, e);
        }

        jobRepository.save(job);
    }
}
