# KarmaQ

A distributed job scheduler built in Java and Spring Boot, backed entirely by PostgreSQL. You submit work (right now, later, or on a recurring schedule) and a pool of workers picks it up and runs it, with retries, crash recovery, and metrics built in from day one.

I built this as a hands on portfolio project, one commit a day, to actually work through the problems a production job scheduler has to solve instead of just reading about them. If you are looking at this repo's history, you will see it grow layer by layer: domain model first, then the API, then real concurrency, then reliability, then packaging. That is on purpose. Each stage only depends on what came before it.

## Table of contents

- [Why this exists](#why-this-exists)
- [How it works](#how-it-works)
- [What it does not do](#what-it-does-not-do-yet)
- [Tech stack](#tech-stack)
- [Project structure](#project-structure)
- [Running it](#running-it)
- [Using the API](#using-the-api)
- [Configuration](#configuration)
- [Testing](#testing)
- [Status and roadmap](#status-and-roadmap)
- [Design notes worth reading](#design-notes-worth-reading)
- [License](#license)

## Why this exists

Almost every real backend needs to do work outside the normal request and response cycle. Sending a welcome email after signup. Generating a report at 2am. Cleaning up stale rows once a day. Retrying a flaky call to a third party API without making the user wait for it.

Most tutorials skip this entirely and jump straight to CRUD endpoints. KarmaQ is the missing piece: the infrastructure that takes a "do this later, and make sure it actually happens" request and turns it into something reliable.

I also wanted a project that would hold up under real interview questions. Not "can you build a CRUD app" (everyone can), but "how do you make sure two workers do not grab the same job", "what happens when a worker crashes mid job", and "how do you stop a retry from running a side effect twice". Building the answers to those questions, instead of memorizing them, is the actual point of this repo.

## How it works

```
                     POST /jobs
                        |
                        v
  Client  ----->  REST API  ----->  Postgres "jobs" table
                                     (this is the source of truth)
                                          ^
                                          |  SELECT ... FOR UPDATE SKIP LOCKED
                                          |
                              +-----------+-----------+
                              |                       |
                        Worker thread 1         Worker thread N
                        (claims a job,          (claims a job,
                         runs it, updates        runs it, updates
                         status)                 status)
```

Postgres plays two roles at once here: it is the durable store for every job, and it is the coordination point that lets several workers poll the same table safely. That second part comes from a single line of SQL, `SELECT ... FOR UPDATE SKIP LOCKED`, which lets multiple workers ask "give me some jobs to do" at the same moment without ever being handed the same row. Normally that kind of coordination needs a separate message broker like RabbitMQ or Kafka. Here it falls out of Postgres for free, which keeps the system smaller and easier to reason about while still being a legitimate way to solve the problem.

The lifecycle of a single job looks like this:

```
PENDING --> IN_PROGRESS --> SUCCEEDED
               |
               +--> (failure, attempts remain) --> PENDING (with backoff delay)
               |
               +--> (failure, attempts exhausted) --> DEAD_LETTER

PENDING --> CANCELLED   (only allowed while still PENDING)
```

Recurring jobs are the one exception: instead of ending at SUCCEEDED, a job with a cron expression goes back to PENDING with its next fire time computed automatically, so it keeps running on schedule indefinitely.

## What it does not do (yet)

Being upfront about scope is part of good engineering, so a few things are intentionally out of bounds for this project:

- It is not a general purpose message broker. There is no pub/sub and no arbitrary routing between services, it only does scheduled execution.
- It is not multi tenant or hardened for public facing auth. It assumes a trusted internal team is calling the API, the way an internal tool would.
- It is not built to scale across multiple data centers. One Postgres instance with several worker processes pointed at it is the target scale, which covers the overwhelming majority of real use cases.

## Tech stack

- Java 21
- Spring Boot 3 (Web, Data JPA, Validation, Actuator)
- PostgreSQL, with Flyway for versioned schema migrations
- Micrometer and Prometheus for metrics
- cron-utils for parsing cron expressions
- Docker and Docker Compose for local and reproducible runs
- GitHub Actions for CI
- JUnit 5, Mockito, and AssertJ for testing, with Testcontainers for real database integration tests

## Project structure

```
karma-q/
├── .github/
│   └── workflows/
│       └── ci.yml                    # builds and tests on every push
├── src/
│   ├── main/
│   │   ├── java/com/karmaq/
│   │   │   ├── KarmaQApplication.java        # entry point, enables scheduling
│   │   │   │
│   │   │   ├── job/                          # the domain layer
│   │   │   │   ├── Job.java                  # the core entity, one row per job
│   │   │   │   ├── JobStatus.java            # the lifecycle states
│   │   │   │   └── JobService.java           # business rules: create, get, cancel
│   │   │   │
│   │   │   ├── repository/
│   │   │   │   └── JobRepository.java        # database access, including the
│   │   │   │                                 # SKIP LOCKED claim query
│   │   │   │
│   │   │   ├── api/                          # the HTTP layer
│   │   │   │   ├── JobController.java        # POST/GET/DELETE /jobs
│   │   │   │   ├── ApiExceptionHandler.java  # turns exceptions into clean HTTP errors
│   │   │   │   └── dto/
│   │   │   │       ├── CreateJobRequest.java # what a client is allowed to send
│   │   │   │       └── JobResponse.java      # what a client is allowed to see
│   │   │   │
│   │   │   ├── worker/                       # execution and scheduling
│   │   │   │   ├── JobHandler.java           # contract for "code that runs a job type"
│   │   │   │   ├── JobHandlerRegistry.java   # maps a job type string to its handler
│   │   │   │   ├── LoggingJobHandler.java    # a simple sample handler
│   │   │   │   ├── CronScheduler.java        # computes next fire time from a cron string
│   │   │   │   └── ConcurrentJobExecutor.java  # the real worker pool, claiming, retry
│   │   │   │                                   # with backoff, and dead lettering all live here
│   │   │   │
│   │   │   └── config/
│   │   │       ├── WorkerPoolConfig.java     # the thread pool that runs jobs
│   │   │       └── QueueMetrics.java         # exposes per status queue depth gauges
│   │   │
│   │   └── resources/
│   │       ├── application.yml               # datasource, worker pool, and lease settings
│   │       └── db/migration/
│   │           └── V1__create_jobs_table.sql  # the schema, plus the indexes the
│   │                                           # claim query relies on
│   │
│   └── test/java/com/karmaq/
│       ├── job/
│       │   └── JobServiceTest.java            # create, cancel, and idempotency rules
│       └── worker/
│           ├── ConcurrentJobExecutorTest.java       # claim logic
│           └── ConcurrentJobExecutorRetryTest.java  # retry and dead letter behavior
│
├── Dockerfile                  # multi stage build, runs as a non root user
├── docker-compose.yml          # app plus Postgres, one command to run both
├── pom.xml
├── .dockerignore
├── .gitignore
└── README.md
```

Note that the folder names double as the layers in the design: `job` holds the domain and the rules, `repository` holds persistence, `api` holds HTTP, `worker` holds execution, and `config` holds wiring. Each layer only talks to the one directly below it, which is why, for example, adding cron support only touched `worker` and `job`, and never touched `api` at all.

## Running it

**With Docker, one command:**

```bash
docker compose up --build
```

This builds the app image, starts Postgres, waits for Postgres to actually be healthy before starting KarmaQ, and runs the Flyway migration automatically on boot. The API is then available at `http://localhost:8080`.

**Without Docker, if you already have Postgres running locally:**

```bash
# create a database and user matching src/main/resources/application.yml,
# or override with environment variables / a local profile
mvn spring-boot:run
```

## Using the API

Submit a one time job:

```bash
curl -X POST http://localhost:8080/jobs \
  -H "Content-Type: application/json" \
  -d '{
        "type": "log-message",
        "payload": "hello from karmaq",
        "runAt": "2026-08-25T09:00:00Z"
      }'
```

Submit a recurring job (every day at midnight UTC):

```bash
curl -X POST http://localhost:8080/jobs \
  -H "Content-Type: application/json" \
  -d '{
        "type": "log-message",
        "payload": "daily heartbeat",
        "cronExpression": "0 0 0 * * *"
      }'
```

Check a job's status:

```bash
curl http://localhost:8080/jobs/{id}
```

Cancel a job, this only works while it is still PENDING:

```bash
curl -X DELETE http://localhost:8080/jobs/{id}
```

## Configuration

Everything worker related lives under `karmaq.worker` in `application.yml`:

| Setting | Default | What it controls |
|---|---|---|
| `pool-size` | 4 | how many threads execute jobs concurrently |
| `poll-interval-ms` | 1000 | how often the scheduler checks for due jobs |
| `lease-duration-seconds` | 30 | how long a worker can hold a job before it is considered crashed and reclaimed |
| `batch-size` | 10 | how many jobs one poll cycle claims at a time |

Metrics are exposed at `/actuator/prometheus`, including per status queue depth (`karmaq.jobs.queue_depth`) and counters for executed, succeeded, failed, retried, and dead lettered jobs.

## Testing

```bash
mvn test
```

Unit tests cover the business rules in `JobService` (including idempotency) and the claim, retry, and dead letter logic in `ConcurrentJobExecutor`, using mocked repositories so they run fast with no real database needed. A Testcontainers based integration test, which spins up a real disposable Postgres to prove the SKIP LOCKED claiming genuinely prevents double execution under real concurrency, is the next piece being added.

## Status and roadmap

- [x] Domain model (`Job`, `JobStatus`)
- [x] Schema and Flyway migration
- [x] REST API: submit, get status, cancel
- [x] Multi worker pool using the SKIP LOCKED claim query
- [x] Cron based recurring jobs
- [x] Retry with exponential backoff
- [x] Idempotency enforcement on job submission
- [x] Dead letter queue for jobs that exhaust retries
- [x] Metrics and observability via Micrometer and Prometheus
- [x] Dockerfile, Docker Compose, and GitHub Actions CI
- [ ] Testcontainers integration tests against a real Postgres
- [ ] Simple dashboard for watching jobs run live

## Design notes worth reading

A few decisions in here were deliberate trade offs, not defaults, and I think they are worth calling out for anyone reading the code:

**Why Postgres instead of a message queue.** A dedicated broker like RabbitMQ or Kafka is the "expected" answer, but it is also another moving part to run, monitor, and reason about. Since every job already needs to be persisted somewhere for status tracking, using that same store for coordination via `SKIP LOCKED` means one less system in the stack, at the cost of not being a good fit if you ever need cross service pub/sub. For the scheduling use case specifically, that trade is worth it.

**Why the claim and the execution are two separate steps.** `claimDueJobs` runs in one short transaction just to mark jobs as claimed, and the actual execution happens afterward, outside that transaction. If both happened in the same transaction, a single slow job would hold a database lock for the entire time it ran, which would stall every other worker trying to claim new work.

**Why failures reschedule instead of retrying inline.** When a job fails, it goes back to `PENDING` with a future `runAt` instead of being retried immediately in a loop. That means retries reuse the exact same claim and execution path as a brand new job, so there is no separate retry code path to maintain or get wrong.

## License

MIT
