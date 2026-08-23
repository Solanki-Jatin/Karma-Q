package com.karmaq.job;

import com.karmaq.api.dto.CreateJobRequest;
import com.karmaq.repository.JobRepository;
import com.karmaq.worker.CronScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Where the RULES live. The controller (JobController) only knows about
 * HTTP - it takes a request in, hands it here, and turns the result into
 * a response. This class knows nothing about HTTP - it just knows what a
 * "valid job" is and what's allowed to happen to one.
 *
 * Keeping these separate means we could add a CLI or a message-based
 * entry point later without duplicating any of this logic.
 */
@Service
public class JobService {

    private final JobRepository jobRepository;
    private final CronScheduler cronScheduler;

    public JobService(JobRepository jobRepository, CronScheduler cronScheduler) {
        this.jobRepository = jobRepository;
        this.cronScheduler = cronScheduler;
    }

    public Job createJob(CreateJobRequest request) {
        if (request.runAt() == null && request.cronExpression() == null) {
            throw new IllegalArgumentException("Either runAt or cronExpression must be provided");
        }

        // Recurring jobs: compute the first fire time from the cron
        // expression. One-time jobs: use the given runAt as-is.
        Instant runAt = request.cronExpression() != null
                ? cronScheduler.nextFireTime(request.cronExpression())
                : request.runAt();

        Job job = Job.builder()
                .type(request.type())
                .payload(request.payload())
                .runAt(runAt)
                .cronExpression(request.cronExpression())
                .idempotencyKey(request.idempotencyKey())
                .status(JobStatus.PENDING)
                .build();

        return jobRepository.save(job);
    }

    public Job getJob(UUID id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No job found with id " + id));
    }

    public Job cancelJob(UUID id) {
        Job job = getJob(id);

        if (job.getStatus() != JobStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot cancel job in status " + job.getStatus() + " - only PENDING jobs can be cancelled");
        }

        job.setStatus(JobStatus.CANCELLED);
        return jobRepository.save(job);
    }
}
