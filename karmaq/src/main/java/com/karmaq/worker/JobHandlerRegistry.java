package com.karmaq.worker;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A lookup table: job type string -> the JobHandler that knows how to run it.
 *
 * Handlers register themselves here (see LoggingJobHandler for an example).
 * When the executor picks up a job, it asks this registry "who handles
 * type X?" instead of having a giant if/else chain of job types anywhere
 * else in the codebase.
 */
@Component
public class JobHandlerRegistry {

    private final Map<String, JobHandler> handlers = new HashMap<>();

    public void register(String type, JobHandler handler) {
        handlers.put(type, handler);
    }

    public Optional<JobHandler> find(String type) {
        return Optional.ofNullable(handlers.get(type));
    }
}
