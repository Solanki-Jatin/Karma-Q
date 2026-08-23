package com.karmaq.worker;

import com.karmaq.job.Job;
import com.karmaq.job.JobStatus;
import com.karmaq.repository.JobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Tests claimDueJobs() in isolation - the repository is mocked, so this
 * doesn't need a real Postgres instance (that's what an integration test
 * with Testcontainers would be for, later).
 *
 * What this proves: when the claim query returns jobs, this class marks
 * them IN_PROGRESS and stamps lockedAt/lockedBy BEFORE handing them off
 * for execution - which is the behavior that makes the SKIP LOCKED
 * approach safe.
 *
 * Test lives in the same package (com.karmaq.worker) so it can call the
 * protected claimDueJobs() method directly.
 */
@ExtendWith(MockitoExtension.class)
class ConcurrentJobExecutorTest {

    @Mock
    private JobRepository jobRepository;
    @Mock
    private JobHandlerRegistry handlerRegistry;
    @Mock
    private CronScheduler cronScheduler;
    @Mock
    private ExecutorService jobWorkerPool;

    @Test
    void claimDueJobs_marksJobsInProgressWithLockMetadata() {
        Job job = Job.builder()
                .type("log-message")
                .status(JobStatus.PENDING)
                .runAt(Instant.now())
                .build();

        when(jobRepository.findClaimableJobs(any(), any(), anyInt()))
                .thenReturn(List.of(job));
        when(jobRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        ConcurrentJobExecutor executor = new ConcurrentJobExecutor(
                jobRepository, handlerRegistry, cronScheduler, jobWorkerPool, 30, 10);

        List<Job> claimed = executor.claimDueJobs();

        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).getStatus()).isEqualTo(JobStatus.IN_PROGRESS);
        assertThat(claimed.get(0).getLockedAt()).isNotNull();
        assertThat(claimed.get(0).getLockedBy()).startsWith("worker-");
    }
}
