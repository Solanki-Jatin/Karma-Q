package com.karmaq.worker;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Wraps the cron-utils library (already in pom.xml since Day 1) to answer
 * one question: "given this cron expression, when should it next run?"
 *
 * Kept as its own small class so JobService and ConcurrentJobExecutor don't
 * need to know anything about cron syntax - they just call
 * nextFireTime(expression) and get an Instant back.
 */
@Component
public class CronScheduler {

    private final CronParser parser =
            new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));

    public Instant nextFireTime(String cronExpression) {
        Cron cron = parser.parse(cronExpression);
        ExecutionTime executionTime = ExecutionTime.forCron(cron);

        Optional<ZonedDateTime> next = executionTime.nextExecution(ZonedDateTime.now(ZoneOffset.UTC));
        return next.orElseThrow(() ->
                        new IllegalArgumentException("No future execution for cron: " + cronExpression))
                .toInstant();
    }
}
