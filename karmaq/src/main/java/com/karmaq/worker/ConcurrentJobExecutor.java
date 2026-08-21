package com.karmaq.worker;

import com.karmaq.job.Job;
import com.karmaq.job.JobStatus;
import com.karmaq.repository.JobRepository;
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
    private final ExecutorService jobWorkerPool;
    private final int leaseDurationSeconds;
    private final int batchSize;

    public ConcurrentJobExecutor(
            JobRepository jobRepository,
            JobHandlerRegistry handlerRegistry,
            ExecutorService jobWorkerPool,
            @Value("${karmaq.worker.lease-duration-seconds:30}") int leaseDurationSeconds,
            @Value("${karmaq.worker.batch-size:10}") int batchSize
    ) {
        this.jobRepository = jobRepository;
        this.handlerRegistry = handlerRegistry;
        this.jobWorkerPool = jobWorkerPool;
        this.leaseDurationSeconds = leaseDurationSeconds;
        this.batchSize = batchSize;
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
        var handler = handlerRegistry.find(job.getType());
        if (handler.isEmpty()) {
            job.setStatus(JobStatus.FAILED);
            job.setLastError("No handler registered for type: " + job.getType());
            jobRepository.save(job);
            log.warn("No handler for job {} of type {}", job.getId(), job.getType());
            return;
        }

        try {
            handler.get().handle(job);
            job.setStatus(JobStatus.SUCCEEDED);
        } catch (Exception e) {
            job.setStatus(JobStatus.FAILED);
            job.setLastError(e.getMessage());
            log.error("Job {} failed on {}", job.getId(), workerId, e);
        }

        jobRepository.save(job);
    }
}
