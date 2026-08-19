package com.karmaq.worker;

import com.karmaq.job.Job;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The simplest possible handler, useful for testing the whole pipeline
 * end-to-end before any "real" job types exist. Registers itself under
 * the type "log-message" - submit a job with that type and this prints
 * its payload.
 *
 * Real handlers (e.g. "send-email", "generate-report") will follow this
 * same shape: implement JobHandler, register in the constructor.
 */
@Component
public class LoggingJobHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(LoggingJobHandler.class);
    private static final String TYPE = "log-message";

    public LoggingJobHandler(JobHandlerRegistry registry) {
        registry.register(TYPE, this);
    }

    @Override
    public void handle(Job job) {
        log.info("[KarmaQ] Executing job {} (type={}): {}", job.getId(), TYPE, job.getPayload());
    }
}
