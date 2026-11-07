# Studio service

> `services/studio` of [Sovkora](../../README.md), one repository (maven, ai layer). Depends on `build`, `contracts`. Licence: [FSL-1.1-ALv2](../../LICENSE).

Studio & pack marketplace: pack catalogue, studio blueprints, installations and saga-driven runs, with its contract (studio-api) and HTTP client (studio-client).

## What it provides

| Capability | Endpoint |
|:---|:---|
| Pack catalogue & bundles | `/api/v1/studios/packs` · `/api/v1/studios/packs/bundles` |
| Studios & blueprints (semver'd DAGs) | `/api/v1/studios` · `/api/v1/studios/{key}/blueprints` |
| Installations | `/api/v1/studios/{key}/installations` |
| Runs (saga, approvals, cancel) | `/api/v1/studios/runs` |
| Admin runtime settings | `GET /api/v1/studios/admin/runtime-config` · `PUT /api/v1/studios/admin/runtime-config/{key}` · `GET /api/v1/studios/admin/runtime-config/audit?key=&limit=` |

| Module | Role |
|:---|:---|
| `sovkora-studio-api` | Tier-1 contract. |
| `sovkora-studio-client` | HTTP client used by the engine (`sovkora-engine-business`). |
| `sovkora-studio-service` | Spring Boot host (port `8096`), transactional outbox to the job plane. |
| `sovkora-studio-service/src/main/resources/db/migration` | Schema of `orazaka_studio_db`, Flyway: `V1__studio_baseline.sql`, applied at startup (the catalogue content comes from [sovkora-packs](https://github.com/krizaka/sovkora/tree/main/packs)). |
| `infra/initdb/80-studio.sql` | Local bootstrap only: the role and the database. |

## Saga runtime controls

Published blueprint versions are cached per service instance; the live `run.blueprint-cache-size`
row in `studio_runtime_config` bounds the cache (`0` disables it). The same table controls
`run.outcome-listener.concurrency`; changes are applied while the service is running, and outcome
consumers run on virtual threads. Step state, failure cause, and output rows are read together for
each advance. The five-step/20-item fan-out benchmark is `RunOverheadMeasurementIT` and writes its
latencies, JFR profile, and `EXPLAIN ANALYZE` plans for the job lookup and step snapshot to
`target/m1-saga-hot-path.txt` and `target/m1-saga-hot-path.jfr`.

`run.result-cache.studios` lists the Studios whose identical run (same actor, Studio version, inputs and
installation configuration) answers from the recorded result: no hold, no dispatch, no model call, and
`cachedFrom` names the run that computed it (sovkora-studio-service#61). The schema ships it with
`compliance-check` (ADR-089 §4): its ruleset is resolved from the document's kind and `asOf`, so the
recorded report, judgment included, is the answer again. A new pack version is a new Studio version
and computes afresh. A step whose answer depends on more than the inputs returns `resultCacheable:
false` in its output (compliance-check, when its day came from the clock rather than from `asOf` or
the document): the run then keeps no fingerprint and is never served from the cache.

## Admin runtime settings

Both runtime-config endpoints require the `ADMIN` role. The list response includes each setting's
typed current value, type, description, default, and persisted bounds. Replace a value with a JSON
value matching its type: integer settings take a JSON number, booleans take `true`/`false`, and list
settings take an array of strings. For example:

```http
PUT /api/v1/studios/admin/runtime-config/run.fan-out-max
Content-Type: application/json

{"value": 12}
```

Updates are audited with the administrator id and before/after values; `GET …/runtime-config/audit` reads them back, newest first (one setting with `key`, at most 200). Runtime reads query the
database directly, so a successful update is effective on the next run without a service restart.

A fresh database receives the schema and bounds from Flyway (`db/migration/V1__studio_baseline.sql`, applied by the service at startup as the
database owner; the migrator connection is `DATABASE_STUDIO_MIGRATOR_URL` / `_USER` / `_PASSWORD`, defaulting to the datasource). An existing one (dev)
is adopted at version 1 (`baseline-on-migrate`, only until its first reset); until then it is migrated from the workspace, additive and idempotent: `node scripts/dev-deploy.mjs runtime-config-admin`
([krizaka/sovkora](https://github.com/krizaka/sovkora), `infra/migrations/2026-10-23-studio-runtime-config-admin.sql`).

## Build and test

```bash
./mvnw verify -pl services/studio -am      # from the repository root: this module and what it needs
./mvnw verify -pl services/studio -amd     # this module and everything that depends on it
```

CI runs it on every pull request that touches `services/studio/` (ADR-029: one repository, one CI).
