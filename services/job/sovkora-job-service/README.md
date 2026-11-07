# Job service

> `services/job/sovkora-job-service` of [Sovkora](../../../README.md), one repository (maven, ai layer). Depends on `build`, `contracts`, `engine`. Licence: [FSL-1.1-ALv2](../../../LICENSE).

Asynchronous job executor of the Sovkora engine: drains the interactive and batch lanes, owns the capability registry and the worker registry.

## What it provides

The asynchronous executor (port `8090`): drains `orazaka.jobs.interactive` and `orazaka.jobs.batch`,
owns the capability registry (`/internal/v1/capabilities`) and worker registry
(`/internal/v1/workers`). Owns its schema, `src/main/resources/db/migration/V1__job_baseline.sql`, applied by Flyway at startup (history table `flyway_history_job`; `infra/initdb/30-jobs-config.sql` only creates the pgvector extension locally) (jobs, outbox, dedup, config plane,
capability seed, and the grounded-research tables of ADR-087 — sources, the document cache keyed by source,
URL and content hash, the fetch audit, consents — with research off and every source disabled by default; and the law corpus of ADR-089 — instruments, dated and
hashed article versions, imports, the retrieval cache, rule proposals, the demand for unsupported kinds — with the
`law.*` settings (default and bounds, enforced by the database, on `orazaka_runtime_config`) all off and the sources
`law-corpus` and `donnees-quebec` disabled by default).

**External checks** (sovkora-packs#138): the engine's `orazaka.core.checks.courtrecords`, `.credit` and `.identity`
run here through one executor, handler key `check.external` (`ExternalCheckStrategy`), on the engine's
`ExternalCheckService` and the mock connector until the pilot's go. Step inputs `subject` and `consentedPurposes` (a
purpose is granted only when listed); outputs `outcome`, `refusal`, `category`, `providerRef`, `calls` and
`resultJson`; consumption `{calls: 0|1}` (0 releases the hold, 1 settles one `CALL` of `EXTERNAL_CHECK`). The job's
own hold covers the call (no second hold); a job with no hold is refused `BUDGET_EXHAUSTED`. Every call is audited in
`external_check_audit` (append-only, the subject as a keyed HMAC, no personal data). Fails closed until an operator
seals `checks.subject-pepper` — the seed leaves it absent. An input off the contract fails as `INPUT_INVALID`
(`InvalidJobInputException`). `ExecutorRegistryFitnessTest` [EXEC-003] keeps the seeded `handler_key`s and the
executors in step, both ways.

## Build and test

```bash
./mvnw verify -pl services/job/sovkora-job-service -am      # from the repository root: this module and what it needs
./mvnw verify -pl services/job/sovkora-job-service -amd     # this module and everything that depends on it
```

CI runs it on every pull request that touches `services/job/sovkora-job-service/` (ADR-029: one repository, one CI).
