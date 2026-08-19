package com.karmaq.worker;

import com.karmaq.job.Job;
import com.karmaq.job.JobStatus;
import com.karmaq.repository.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Today's version: ONE thread, polling once a second, running jobs one at
 * a time. This is intentionally the simplest thing that can work end-to-end -
 * submit a job via the API, watch it actually execute.
 *
 * What's still missing (this is the Week 2 worker pool's job, not today's):
 *   - Multiple concurrent workers (the SKIP LOCKED query from JobRepository
 *     isn't even needed yet with only one thread - findAll+filter is fine
 *     for now, since there's no contention)
 *   - Retries with backoff
 *   - Crash recovery / lease expiry
 *
 * Keeping this version simple and correct first means the Week 2 upgrade
 * is a clear, reviewable diff instead of a rewrite.
 */
@Component
public class SimpleJobExecutor {

    private static final Logger log = LoggerFactory.getLogger(SimpleJobExecutor.class);

    private final JobRepository jobRepository;
    private final JobHandlerRegistry handlerRegistry;

    public SimpleJobExecutor(JobRepository jobRepository, JobHandlerRegistry handlerRegistry) {
        this.jobRepository = jobRepository;
        this.handlerRegistry = handlerRegistry;
    }

    @Scheduled(fixedDelayString = "${karmaq.worker.poll-interval-ms:1000}")
    public void pollAndExecute() {
        List<Job> dueJobs = jobRepository.findAll().stream()
                .filter(j -> j.getStatus() == JobStatus.PENDING)
                .filter(j -> !j.getRunAt().isAfter(Instant.now()))
                .toList();

        for (Job job : dueJobs) {
            execute(job);
        }
    }

    private void execute(Job job) {
        job.setStatus(JobStatus.IN_PROGRESS);
        jobRepository.save(job);

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
            log.error("Job {} failed", job.getId(), e);
        }

        jobRepository.save(job);
    }
}
