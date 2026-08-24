package com.karmaq.job;

import com.karmaq.api.dto.CreateJobRequest;
import com.karmaq.repository.JobRepository;
import com.karmaq.worker.CronScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests JobService in isolation - the repository is mocked, so these run
 * fast and don't need a real database. Testcontainers-backed integration
 * tests (real Postgres) come later, once the worker pool needs to prove
 * the SKIP LOCKED behavior actually works.
 */
@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;
    @Mock
    private CronScheduler cronScheduler;

    @Test
    void createJob_requiresRunAtOrCronExpression() {
        JobService service = new JobService(jobRepository, cronScheduler);
        CreateJobRequest request = new CreateJobRequest("log-message", "hi", null, null, null);

        assertThatThrownBy(() -> service.createJob(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runAt or cronExpression");
    }

    @Test
    void createJob_savesJobWithPendingStatus() {
        JobService service = new JobService(jobRepository, cronScheduler);
        CreateJobRequest request = new CreateJobRequest(
                "log-message", "hi", Instant.now(), null, null);
        when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

        Job saved = service.createJob(request);

        assertThat(saved.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(saved.getType()).isEqualTo("log-message");
    }

    @Test
    void cancelJob_onlyAllowedWhenPending() {
        JobService service = new JobService(jobRepository, cronScheduler);
        UUID id = UUID.randomUUID();
        Job inProgressJob = Job.builder().id(id).status(JobStatus.IN_PROGRESS).build();
        when(jobRepository.findById(id)).thenReturn(Optional.of(inProgressJob));

        assertThatThrownBy(() -> service.cancelJob(id))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IN_PROGRESS");
    }

    @Test
    void createJob_returnsExistingJobForDuplicateIdempotencyKey() {
        JobService service = new JobService(jobRepository, cronScheduler);
        Job existing = Job.builder().type("log-message").status(JobStatus.SUCCEEDED).build();
        when(jobRepository.findByIdempotencyKey("key-123")).thenReturn(Optional.of(existing));

        CreateJobRequest request = new CreateJobRequest(
                "log-message", "hi", Instant.now(), null, "key-123");

        Job result = service.createJob(request);

        assertThat(result).isSameAs(existing);
    }
}
