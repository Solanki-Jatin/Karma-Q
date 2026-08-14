# Karma-Q

A distributed job scheduler backed by PostgreSQL. Submit work - immediate, delayed,
or recurring - and a pool of workers reliably executes it, with retries, crash
recovery, and observability built in.

## Why this exists

Most backend systems need to run work outside the request/response cycle: sending
emails, generating reports, syncing data, cleaning up stale records. KarmaQ is that
piece of infrastructure, built from scratch to understand - and demonstrate - the
concurrency and reliability problems a production job scheduler has to solve.

## Design

```
Client → REST API → Postgres (jobs table, source of truth)
                        ↑
              Worker Pool (N processes/threads)
              polls due jobs → executes → updates status
```

Postgres is both the job store and the coordination point, claimed via
`SELECT ... FOR UPDATE SKIP LOCKED`. No separate message broker — fewer moving
parts, and "persist, then acknowledge" comes for free from a single transactional
store.

### Non-goals

- Not a general message broker — no pub/sub, no arbitrary routing.
- Not multi-tenant or auth-hardened — single-team internal tool assumptions.
- Not horizontally scaled across data centers — one Postgres instance, multiple
  worker processes is the target scale.

## Stack

Java 21 · Spring Boot 3 · PostgreSQL · Flyway · Docker Compose · GitHub Actions ·
Micrometer/Prometheus

## Status

🚧 Early development — building in public, one commit a day.

- [x] Domain model (`Job`, `JobStatus`)
- [x] Schema + migration
- [ ] REST API (submit, get status, cancel)
- [ ] Worker pool + claim loop
- [ ] Cron-based recurring jobs
- [ ] Retry with backoff + idempotency
- [ ] Crash recovery (lease reclaim)
- [ ] Dead-letter queue
- [ ] Metrics + observability
- [ ] Docker Compose + CI

## Running locally

```bash
# Postgres via Docker (see docker-compose.yml, coming in Week 4)
mvn spring-boot:run
```

## License

MIT
