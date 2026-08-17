package com.karmaq.api;

import com.karmaq.api.dto.CreateJobRequest;
import com.karmaq.api.dto.JobResponse;
import com.karmaq.job.Job;
import com.karmaq.job.JobService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * The three doors into the system:
 *   POST   /jobs      - submit a new job
 *   GET    /jobs/{id} - check a job's status
 *   DELETE /jobs/{id} - cancel a job (only if it hasn't started yet)
 *
 * This class does almost nothing itself - it translates HTTP <-> JobService
 * calls. That's intentional: if we ever need a second interface (a CLI, an
 * internal gRPC endpoint), JobService already has all the real logic.
 */
@RestController
@RequestMapping("/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    public ResponseEntity<JobResponse> createJob(@Valid @RequestBody CreateJobRequest request) {
        Job job = jobService.createJob(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(JobResponse.from(job));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> getJob(@PathVariable UUID id) {
        Job job = jobService.getJob(id);
        return ResponseEntity.ok(JobResponse.from(job));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<JobResponse> cancelJob(@PathVariable UUID id) {
        Job job = jobService.cancelJob(id);
        return ResponseEntity.ok(JobResponse.from(job));
    }
}
