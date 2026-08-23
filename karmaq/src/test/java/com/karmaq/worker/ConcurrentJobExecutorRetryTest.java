package com.karmaq.worker;

import com.karmaq.job.Job;
import com.karmaq.job.JobStatus;
import com.karmaq.repository.JobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Covers the two branches of handleFailure(): retry-with-backoff and
 * dead-letter. Uses reflection to call the private method directly, since
 * exercising it through the full execute() -> handler.handle() path would
 * need a real failing handler wired through Spring.
 */
@ExtendWith(MockitoExtension.class)
class ConcurrentJobExecutorRetryTest {

    @Mock
    private JobRepository jobRepository;
    @Mock
    private JobHandlerRegistry handlerRegistry;
    @Mock
    private ExecutorService jobWorkerPool;

    @Test
    void handleFailure_reschedulesWithBackoffWhenAttemptsRemain() throws Exception {
        Job job = Job.builder()
                .status(JobStatus.IN_PROGRESS)
                .attemptCount(0)
                .maxAttempts(5)
                .runAt(Instant.now())
                .build();
        when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

        Job result = invokeHandleFailure(job, new RuntimeException("boom"));

        assertThat(result.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(result.getAttemptCount()).isEqualTo(1);
        assertThat(result.getRunAt()).isAfter(Instant.now());
        assertThat(result.getLastError()).isEqualTo("boom");
    }

    @Test
    void handleFailure_movesToDeadLetterWhenAttemptsExhausted() throws Exception {
        Job job = Job.builder()
                .status(JobStatus.IN_PROGRESS)
                .attemptCount(4)
                .maxAttempts(5)
                .runAt(Instant.now())
                .build();
        when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

        Job result = invokeHandleFailure(job, new RuntimeException("still broken"));

        assertThat(result.getStatus()).isEqualTo(JobStatus.DEAD_LETTER);
        assertThat(result.getAttemptCount()).isEqualTo(5);
    }

    private Job invokeHandleFailure(Job job, Exception failure) throws Exception {
        ConcurrentJobExecutor executor = new ConcurrentJobExecutor(
                jobRepository, handlerRegistry, jobWorkerPool, 30, 10);
        Method method = ConcurrentJobExecutor.class
                .getDeclaredMethod("handleFailure", Job.class, Exception.class);
        method.setAccessible(true);
        method.invoke(executor, job, failure);
        return job;
    }
}
