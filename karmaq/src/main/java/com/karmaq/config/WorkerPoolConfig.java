package com.karmaq.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The pool of threads that actually EXECUTE jobs (as opposed to the single
 * @Scheduled thread that polls for due jobs - that's separate, see
 * ConcurrentJobExecutor).
 *
 * Pool size is read from karmaq.worker.pool-size in application.yml, so you
 * can tune concurrency without touching code.
 */
@Configuration
public class WorkerPoolConfig {

    @Bean
    public ExecutorService jobWorkerPool(@Value("${karmaq.worker.pool-size:4}") int poolSize) {
        AtomicInteger counter = new AtomicInteger(1);
        return Executors.newFixedThreadPool(poolSize, runnable -> {
            Thread thread = new Thread(runnable, "karmaq-worker-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });
    }
}
