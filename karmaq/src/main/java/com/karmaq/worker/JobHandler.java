package com.karmaq.worker;

import com.karmaq.job.Job;

/**
 * The contract for "code that actually does the work" for a given job type.
 *
 * Example: a job with type = "send-email" needs a handler that knows how to
 * parse the payload and send an email. That logic does NOT belong in
 * JobService or the executor - it belongs in a class implementing this
 * interface, registered under the "send-email" key.
 *
 * This keeps the scheduler generic - KarmaQ itself has no idea what an
 * "email" or a "report" is. It only knows how to find due jobs and hand
 * them to whichever handler is registered for their type.
 */
public interface JobHandler {

    /**
     * Executes the given job. Implementations should throw an exception on
     * failure - the executor catches it, records it, and (starting Week 2)
     * decides whether to retry.
     */
    void handle(Job job) throws Exception;
}
