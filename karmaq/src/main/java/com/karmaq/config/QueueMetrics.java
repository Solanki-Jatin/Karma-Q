package com.karmaq.config;

import com.karmaq.job.JobStatus;
import com.karmaq.repository.JobRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Exposes karmaq.jobs.queue_depth{status=...} gauges so you can watch queue
 * depth per status in Grafana/Prometheus - e.g. a PENDING count that keeps
 * climbing tells you workers can't keep up; a growing DEAD_LETTER count
 * means something is systematically broken.
 *
 * Registered once at startup (@PostConstruct) - Micrometer gauges are
 * "pull-based": we hand it a supplier function, and it calls jobRepository
 * fresh every time Prometheus scrapes /actuator/prometheus, so this never
 * goes stale.
 */
@Component
public class QueueMetrics {

    private final JobRepository jobRepository;
    private final MeterRegistry meterRegistry;

    public QueueMetrics(JobRepository jobRepository, MeterRegistry meterRegistry) {
        this.jobRepository = jobRepository;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void registerGauges() {
        for (JobStatus status : JobStatus.values()) {
            meterRegistry.gauge(
                    "karmaq.jobs.queue_depth",
                    io.micrometer.core.instrument.Tags.of("status", status.name()),
                    jobRepository,
                    repo -> repo.countByStatus(status)
            );
        }
    }
}
