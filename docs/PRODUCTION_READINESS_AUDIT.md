---
title: "Orazaka — Production readiness audit"
description: "Full-codebase review: security, availability, scalability, failure modes, complexity and refactoring candidates, with a sequenced remediation plan toward a first production launch."
category: Architecture
order: 20
---

# Orazaka — Production readiness audit

> **Scope.** Every Java module (`sovkora-libs`, `orazaka-apps/services`), the Python media worker,
> the six npm workspaces, `infra/`, and the governance suite. Static review at commit `21167dd4`.
> The build was **not executed** — the audit host runs JDK 11, the project targets Java 21. Every
> finding below is therefore evidence-based (file + line), not test-derived; §8 lists what must be
> verified by actually running the suite.
>
> **Verdict.** The architecture is sound and unusually well governed. The blockers are not
> architectural — they are four security defects and one operational hole, all fixable in weeks, not
> quarters. **Do not launch until §2 is closed.**

---

## 1. What the codebase is

| | |
|:---|:---|
| Java (main) | ~53 000 LOC · 1 110 files · 8 services + 14 libraries |
| Java (test) | 424 test files · 14 ArchUnit governance suites · 23 shared architecture rules |
| TypeScript | ~44 000 LOC · 6 workspaces (web-client 24k, cli 11k, mobile 3.7k) |
| Python | ~1 500 LOC (media worker, MLX/ffmpeg) |
| Databases | 6 bounded contexts, own role + own database each, no cross-context FK |
| Messaging | 2 topic exchanges + DLX, exponential retry, prefetch backpressure |

### What is genuinely good — and should not be touched

These are above the median for a system of this age, and several are above the median for systems
already in production. They are listed first because the rest of this document is criticism, and the
ratio would otherwise mislead.

- **The AMQP topology.** DLQ per queue via `orazaka.dlx`, exponential retry, `prefetch=1` with
  `concurrency=2..4` on the inference consumers — and the *reasoning* is in the YAML comments
  (`orazaka-job-service/…/application.yml:49-56`). Backpressure on a memory-bound single machine was
  thought about, not inherited from a template.
- **The credit ledger.** Over-draft prevented by a DB `CHECK` plus a conditional single-statement
  `UPDATE`, append-only enforced by a `BEFORE UPDATE OR DELETE` trigger, idempotency by a `UNIQUE`
  key *and* `processed_messages`. Two independent guards on the one operation whose failure is
  unrecoverable. This is how it should be done.
- **Observability is wired.** Micrometer Tracing over the OTel bridge with OTLP export in **all
  eight** services, not two. That is the part teams normally defer.
- **Governance is real.** 23 ArchUnit rules shared through `orazaka-test-support`, applied per
  module, including the tier-isolation rule [SEAM-002] and the SQL boundary rule [SEAM-001]. The
  ADR-034 amendment (the interpreter moved out of `business` because SEAM-002 forbade it) proves the
  rules actually bind decisions rather than decorating them.
- **Studio tenant scoping.** Every endpoint in `StudioInstallationController` and
  `StudioRunController` takes `actor.getSubject()` and passes it to the query. Not one endpoint
  trusts a path id alone. `StudioAccessService.evaluateAll` deliberately takes **one** entitlement
  snapshot for N studios rather than N calls, with the reason in the javadoc.

---

## 2. Blockers — do not go to production with these open

### 2.1 ✅ CLOSED (wave 1) — Cross-tenant access to all generated and uploaded media

> **Correction, established by reading the audited commit.** Two of this finding's three claims were
> wrong, and the severity qualifier with them.
>
> - **Path A is false.** At `21601dd4`, `SecurityConfig.java:139-140` reads
>   `.requestMatchers("/uploads/**").hasAnyAuthority(ADMIN, USER)` — not `permitAll()`. An anonymous
>   request received `401`. The media tree was **not** readable "with no credential at all".
> - **The bearer claim is false.** `session.user.id` carries the signed HS256 session JWT, not a user
>   id (`auth-options.ts:74` — `id: data.token`, with the comment above it saying so). The BFF was
>   sending a valid token; the field is misleadingly named.
> - **Path B is true and was the whole finding.** `route.ts:45` builds the target from
>   `segments.join("/")` without comparing `segments[0]` to the session, so **any signed-in user
>   could read any other user's media**. Cross-tenant, and critical — but *authenticated*, which
>   changes the breach-notification analysis this section rests on.
>
> The prescribed fix was right regardless and has been applied.

**Two independent paths, either one sufficient.**

*Path A — the backend serves the upload tree publicly.*

`orazaka-conversation-service/…/config/SecurityConfig.java:139` puts `/uploads/**` behind
`permitAll()`. `WebMvcConfig.java:45-49` maps that path onto a `ResourceHandlerRegistry` rooted at
the upload directory. `MediaFileStore.save()` writes results to
`/uploads/{userId}/{jobId}/output/{filename}`. The edge routes `/` to the conversation service as a
catch-all (`orazaka-edge/…/application.yml:26`), so **the entire media tree of every tenant is
readable over the public edge with no credential at all**, given a URL.

*Path B — the BFF authenticates the caller but not the resource.*

`orazaka-web-client/src/app/uploads/[...path]/route.ts` checks that a session exists, then builds
`${ROUTER_URL}/uploads/${segments.join("/")}` **without ever comparing `segments[0]` to
`session.user.id`**. Any logged-in user can read any other user's media by editing one path segment.
It also sends `Authorization: Bearer ${userId}` — a raw user id presented as a bearer token, which
no backend validates (and could not: it is not a token).

**Why this is the top finding.** The product's entire positioning is Loi 25 / GDPR sovereignty
(`docs/MASTER_FEATURES.md` §2). The assets involved are real-estate interior photos, client
voiceovers, generated commercial video — the most identifying content the platform holds. A single
leaked URL in a referrer header, a browser history sync, or a shared link is a reportable breach.

**Fix.**
1. Delete the `permitAll()` on `/uploads/**` and delete the static `ResourceHandlerRegistry` mapping.
2. Replace it with an authenticated controller that (a) reads the actor from the JWT, (b) resolves
   the asset through a repository lookup that filters on `actor_id`, (c) streams from disk, (d)
   supports `Range` for video. Never derive the file path from user input.
3. In the BFF, forward the real session JWT, not the user id.
4. Add the integration test that is missing everywhere: *actor B requests actor A's asset id → 404*
   (404, not 403 — do not confirm existence).

### 2.2 🔴 CRITICAL — No schema migration path; the database can be created but never evolved

`flyway.enabled: false` in all four services that mention it (`orazaka-conversation-service:35`,
`orazaka-identity-service:19`, `orazaka-job-service:31`, `orazaka-automation-service:19`). There is
no `db/migration` directory, no Liquibase, no equivalent. The schema exists **only** in
`infra/initdb/*.sql`, which the Postgres image executes only when the data directory is empty.

Consequence: the day the first production database exists, every subsequent `ALTER TABLE` is a
manual `psql` session — unversioned, unreviewed, unlogged, not reproducible across environments, and
with no rollback. Adding one column to `credit_ledger_entry` becomes an incident waiting for a bad
evening. This is the single largest *operational* gap and it is invisible in the local phase by
construction, because `orazaka stop --purge` recreates the world every time.

**Fix.** Adopt Flyway now, while there is no production data to preserve.
- `V1__<context>_baseline.sql` per context = today's `initdb` file minus the dev seeds.
- Dev seeds move to `afterMigrate` callbacks or a `dev` profile, never into `V*` files.
- `flyway.enabled: true`, `validate-on-migrate: true`, `clean-disabled: true` (a `flyway:clean`
  against production is how companies lose databases).
- `SqlBoundaryRules` [SEAM-001] must scan `db/migration/**` as well as `infra/initdb/**`, or the
  no-cross-context-FK guarantee silently stops being enforced the day the SQL moves.

### 2.3 🔴 CRITICAL — `/internal/v1/**` is `permitAll()` on the money path

`orazaka-billing-service/…/SecurityConfig.java:96`, `orazaka-studio-service/…/SecurityConfig.java:93`
and `orazaka-identity-service/…/SecurityConfig.java:116` all `permitAll()` the `/internal/v1/**`
prefix. The stated protection (`docs/STUDIO_ARCHITECTURE.md` §10, ADR-033) is that the edge does not
route it.

That is **network-topology security**, and it holds exactly as long as the network topology does. In
a default Kubernetes namespace every pod reaches every pod. One SSRF anywhere in the estate — and
this platform *fetches URLs* (`SearchWebRequest`, MCP servers, RAG ingestion from web sources) —
turns into unauthenticated `hold` / `settle` / `release` against the credit ledger. The append-only
trigger does not help: these are legitimate mutations, correctly recorded.

**Fix.** Authenticate service-to-service calls. The M2M JWT machinery already exists
(`M2mJwtProperties`, `ApiKeyExchangeFilter`). Require it on `/internal/v1/**`:
`.requestMatchers("/internal/v1/**").hasAuthority("SERVICE")`. Keep the edge non-routing as
the *second* layer, not the only one. mTLS is the stronger option when the platform reaches a mesh.

> **Correction.** This section originally prescribed `hasAuthority("SCOPE_internal")`. That cannot
> match: the converter is `setAuthoritiesClaimName("roles")` with `setAuthorityPrefix("")`, so the
> authority is the raw claim value and a prefixed matcher fails closed against a correct token — the
> failure mode that invites loosening the matcher. The adopted convention is `roles: ["SERVICE"]` →
> `hasAuthority("SERVICE")` (ADR-035).
>
> **Missing instance.** This finding names billing, studio and identity. `orazaka-knowledge-service`
> exposes `/internal/v1/knowledge` (`KnowledgeController:20`) and has **neither**
> `spring-boot-starter-security` **nor** a `SecurityConfig` — its internal surface is open
> unconditionally, with no `permitAll()` to remove. It was outside wave 1's file manifest and
> **remains open**.

### 2.4 🔴 CRITICAL — `orazaka-job-service` accepts every HTTP request

`orazaka-job-service/…/SecurityConfig.java:28`:

```java
.authorizeHttpRequests(authz -> authz.anyRequest().permitAll());
```

The whole HTTP surface, unauthenticated. The service is primarily an AMQP consumer, which is why
this passed review — but it exposes actuator and whatever REST it grows, and "it has no endpoints
today" is not a security control. **Fix**: `anyRequest().authenticated()`, with `/actuator/health`
and `/actuator/info` explicitly permitted, matching the four services that already do this correctly.

### 2.5 🟠 HIGH — Database role passwords hardcoded and committed

`infra/initdb/10-identity.sql:12`, `40-knowledge.sql:12`, `50-automation.sql:11`,
`70-billing.sql:16`, `80-studio.sql:19` each carry a literal
`CREATE ROLE … LOGIN PASSWORD 'orazaka_<ctx>_pass'`. They are in git, they follow a guessable
pattern, and one of them owns the credit ledger.

> **Correction.** The count is five files short. The same literal is the *default* of
> `${<CTX>_DB_PASSWORD:orazaka_<ctx>_pass}` in all five services' `application.yml`, so removing it
> from `infra/initdb` alone would have left it committed and in effect. Both are now gone, and the
> yaml has no default at all — an unset password fails the service at startup instead of silently
> using a guessable one.

Acceptable for the local phase; it must not survive the first deploy. **Fix**: psql variables
(`:'studio_password'`) fed from the environment, sourced from a secret manager in non-local
profiles. Add a build check that fails on a literal `PASSWORD '` in `infra/**` outside a `dev`
guard — otherwise this reappears with the next context.

### 2.6 🔴 CRITICAL, DEFERRED — `/intent/route` signs authentication the caller declares about itself

Recorded by M1.5 as a **blocking pre-deployment item**, not fixed there (ADR-062 §7).

`POST /api/v1/intent/route` is `.permitAll()`. `IntentController.resolveGate` reads `verified` and
`scopes` **from the request body's `metadata`**, `GateService` checks a hardcoded `"intent:route"`
against that list, and signs `AUTH_PASS` with `intentTokenSecret` — which no yaml sets, so it is the
committed literal `orazaka-default-dev-secret-change-in-production`. The claim validation
`M2mJwtProperties` describes (`iss`, `sub`, `aud`, scopes) is written in three places and implemented
in none (M0.5 inventory §2.3).

**Why it is inert today and still blocking.** Nothing verifies an `IntentToken`; the only consumers
are a Zod shape schema and a console that displays it. **The danger is the ordering.** The first
service to implement verification of that signature will turn a self-declared-scope system into
what looks like working authentication — green tests, a valid HMAC, and a caller who wrote their own
`verified: true`. **Fix before that happens, not after**: validate a real M2M JWT on the route (issuer,
subject, audience, scopes from the token, never from the body), take the secret from the environment
with no default, and only then let anything trust the signature.

---

## 3. Availability, scalability, failure modes

### 3.1 🟠 Single points of failure with no recovery story

One Postgres container hosts six databases, including the financial ledger. One RabbitMQ. No
replication, no PITR, no backup schedule, no documented restore, no restore *drill*. A backup you
have never restored is a hypothesis, not a backup.

**Fix, in order**: managed Postgres with PITR (or streaming replica + WAL archiving) → a quarterly
restore drill with a written RTO/RPO → RabbitMQ quorum queues (classic queues lose messages on node
loss; the `orazaka.jobs` queue carries paid work). Split the ledger onto its own instance once
revenue justifies it — different backup class, different blast radius.

### 3.2 🟠 The edge has no read timeout and no circuit breaker

`orazaka-edge/…/ProxyClientConfig.java:24` sets `connectTimeout(5s)` and nothing else. A backend that
accepts the connection and then stalls holds the proxy call open indefinitely. Virtual threads make
this cheap in memory but not free: sockets, file descriptors and the downstream connection pool all
leak, and the facade degrades for *every* route because one is sick.

**Fix**: a per-route read timeout (long only for the SSE routes, which need an explicit carve-out),
plus a circuit breaker (Resilience4j) that fails fast with `503` when a backend is down instead of
queueing traffic against it. Add per-route concurrency limits so one slow backend cannot consume the
whole edge.

### 3.3 🟠 Rate limiting covers two services out of eight

`RateLimitFilter` exists only in `orazaka-conversation-service` and `orazaka-job-service`. Missing on:

- **identity** — no throttle on login ⇒ credential stuffing, and no throttle on
  forgot-password ⇒ mail-bomb + user enumeration.
- **billing** — unthrottled writes on the money path.
- **studio** — `POST /runs` is the most expensive endpoint in the product: one call fans out to
  `run.fan-out-max` inference jobs. `run.max-concurrent-per-actor` caps concurrency but not
  *arrival rate*.

**Fix**: move the filter into the shared platform kit (§5.2) and apply it everywhere, with per-tier
limits read from `orazaka_rate_limit_tiers` — the table already exists.

### 3.4 🟡 `PathResolver` uses a dev-machine heuristic at runtime

`conversation-service` and `job-service` both contain:

```java
while (root != null && !Files.exists(root.resolve("AGENTS.md"))) { root = root.getParent(); }
```

Locating the upload root by walking up until a *repository governance file* appears. In a container
`AGENTS.md` does not exist, the loop exhausts, and the code silently falls back to the process CWD.
Media paths then depend on the working directory the container happens to start in — different in
Docker, in Kubernetes, and under a different entrypoint. Silent, environment-dependent data
placement is how you discover after a rollout that last week's videos are unreachable.

**Fix**: an explicit, required, absolute `orazaka.uploads.directory` with **no** fallback — fail
fast at startup if unset. Delete the walk-up entirely.

### 3.5 🟡 No encryption at rest

`COMP-05` is still 📅 ROADMAP in `docs/MASTER_FEATURES.md`. Chat messages, run inputs, blueprint
configs (which hold brand kits and, per `RunScope`, secrets) and all generated media are plaintext on
disk. Combined with §2.1 this compounds: an unauthenticated read of an unencrypted store.

**Fix**: at minimum, volume-level encryption plus Postgres TDE or `pgcrypto` on the columns that hold
`secrets`; verify `RunScope.toString()` really does exclude `secrets` (the design mandates it — the
test must exist and be named for it).

---

## 4. Correctness and complexity

### 4.1 🟠 SQL lives in the application layer, in the two most important classes

| File | Lines | Raw SQL statements |
|:---|---:|---:|
| `studioservice/application/service/RunSagaService.java` | 612 | 16 |
| `billingservice/application/service/CreditLedgerService.java` | 549 | 16 |
| `studioservice/application/service/StudioRunService.java` | 365 | 9 |
| `billingservice/application/service/PlanCatalogService.java` | 235 | 7 |
| …15 further `*Service` classes in billing and studio | | 1–6 each |

Both flagship services inject `JdbcTemplate` directly into `application/service` and write SQL inline
(`RunSagaService.java:23,36`). This inverts the hexagon: the application layer, which should express
*what the product does*, is coupled to Postgres syntax.

It passes governance because `GovernanceRules.assertServicePackageOnlyServices` checks type **names**,
not content — a `*Service` may legally contain anything. That is a rule gap, not a rule violation.

The right pattern is already in the codebase, applied exactly once:
`studioservice/infrastructure/adapter/persistence/JdbcBlueprintRepositoryAdapter.java` sits behind
the `BlueprintRepository` port. Nothing prevents generalising it.

**Fix.**
1. Extract `RunRepository`, `InstallationRepository`, `StudioCatalogRepository`,
   `LedgerRepository`, `WalletRepository`, `HoldRepository`… as ports in `domain/port`, with
   `Jdbc*RepositoryAdapter` implementations in `infrastructure/adapter/persistence`.
2. Add a governance rule — `assertNoPersistenceApiInApplicationLayer` — forbidding
   `org.springframework.jdbc..`, `jakarta.persistence..` and `javax.sql..` from
   `..application.service..`. Without the rule the drift returns within two features.
3. This also unlocks unit-testing the saga without a database, which is why `RunSagaService` has the
   thinnest test coverage of any class its size.

### 4.2 🟠 `RunSagaService` does six jobs

25 methods spanning: DAG advancement, scope construction, fan-out expansion, SQL, outbox emission,
credit settlement and run terminalisation. It is the highest-risk class in the product — it decides
whether a paying customer is charged.

**Split into**: `RunSagaService` (state transitions only) · `RunScopeService` (scope/template
resolution) · `FanOutPlanner` (expansion + caps) · `RunRepositoryAdapter` (all SQL) ·
keep `CreditReservationService` and `OutboxService` as-is. Target ≤200 lines each. Do this
**before** the next Studio ships, not after.

### 4.3 🟡 `AssetFileResolver` resolves ambiguously

`AssetFileResolver.findByPrefix` returns `matches[0]` from a `startsWith` scan of a directory. Two
assets sharing a prefix resolve to whichever the filesystem lists first — non-deterministic across
platforms. **Fix**: resolve assets by exact id through a database lookup that also carries the owner,
which §2.1 requires anyway.

### 4.4 🟡 `MediaFileStore` builds paths without containment checks

`dir.resolve(filename)` with no `normalize()` + `startsWith(root)` assertion. Today `filename` is a
literal (`"image.png"`, `"speech.mp3"`) and `userId`/`jobId` come from a `JobCommand` off the broker,
so it is not currently exploitable — but the containment check is one line and the exposure is a
**write** primitive. Add it; do not rely on every future caller remembering.

### 4.5 🟡 File-size rule violations in the UI

AGENTS.md §8 caps `.tsx` at 250 lines. `orazaka-design-system/src/icon.tsx` is **841**. Two data
files also dwarf the rest: `translations.ts` (1 442) and `translations.types.ts` (667).

These are registries, not components — the rule as written does not fit them. **Decide and write it
down**: either carve out an explicit exemption in AGENTS.md §8 for generated/registry files, or split
`icon.tsx` by domain and generate `translations.types.ts` from `translations.ts`. Leaving a
documented rule silently violated is worse than either.

---

## 5. Factorisation — the largest single win available

### 5.1 The duplication, measured

| Class | Copies | Where |
|:---|---:|:---|
| `SecurityConfig` | 5 | studio · identity · conversation · billing · job |
| `MessageDedupService` | 5 | knowledge · automation · studio · billing (+ the port) |
| `DataSourceConfig` | 5 | knowledge · automation · studio · identity · billing |
| `AmqpConstants` / `AmqpConfiguration` | 4 each | knowledge · automation · studio · billing |
| `OutboxRelay` | 4 | + `OutboxService` ×2, `OutboxStore` ×2, `OutboxEventEntity` ×2 |
| `SessionJwtProperties` | 3 | |
| `MediaFileStore` · `PathResolver` · `UserDirectoryService` · `HttpUserDirectoryAdapter` · `UserCredentialsProviderAdapter` · `IdentityJpaConfiguration` · `ContextService` · `JobCommand` | 2 each | conversation ↔ job |

Roughly **1 500 lines of copy-paste**. The line count is the small problem. The real one is that
§2.3 (`/internal/v1` `permitAll`) has to be fixed in **five** files, and whichever one is missed stays
vulnerable — with no test that would notice.

### 5.2 The fix: a Tier-2 platform kit

Create `sovkora-libs/orazaka-service-kit` (Tier-2 Platform SDK per AGENTS.md §2 — shared with SDK
discipline, stable, backward-compatible) holding the plumbing every service repeats:

```
orazaka-service-kit/
  security/     ServiceSecurityConfig (opinionated: JWT resource server, CSRF off,
                actuator health/info permitted, /internal/** requiring SCOPE_internal,
                everything else authenticated) + RateLimitFilter
  amqp/         DlxTopology, RetryPolicy, DedupInterceptor, standard exchange/queue names
  outbox/       OutboxStore + OutboxRelay + the pending-events schema fragment
  persistence/  ContextDataSourceConfig driven by <ctx>DataSourceProperties
  media/        MediaPathResolver (fail-fast, no walk-up) + containment-checked MediaFileStore
```

Each service then declares *what differs* — its context name, its queue bindings — and inherits the
rest. Tier-2 discipline applies: it changes rarely and never breaks compatibility.

**Do not put `JobCommand` here.** It is a wire contract between two bounded contexts and belongs in
a Tier-1 module (`contracts/sovkora-jobs-api`). Two owners for one wire format is how
a producer adds a field the consumer silently drops.

**Expected effect**: ~1 500 lines deleted, one place to fix a security rule, and new services start
from ~200 lines of genuine domain code instead of ~600 with 400 copied.

---

## 6. Findings index

| # | Severity | Finding | Evidence |
|:---:|:---|:---|:---|
| 1 | ✅ | ~~Unauthenticated~~ **authenticated** cross-tenant media read — see the correction in §2.1 | `WebMvcConfig.java:45`, `uploads/[...path]/route.ts` · closed by wave 1 (ADR-035) |
| 2 | 🔴 | No schema migration path; Flyway disabled everywhere | 4× `application.yml`, no `db/migration` |
| 3 | ✅ | `/internal/v1/**` `permitAll()` on billing + studio + identity | 3× `SecurityConfig.java` · closed by wave 1 (ADR-035). **knowledge-service is a 4th instance, still open** — see §2.3 |
| 4 | ✅ | `job-service` permits every request | `jobservice/…/SecurityConfig.java:28` · closed by wave 1 |
| 5 | ✅ | DB passwords hardcoded and committed | 5× `infra/initdb/*.sql` **and 5× `application.yml` defaults, which this row missed** · closed by wave 1 |
| 6 | 🟠 | SPOF Postgres + RabbitMQ, no backup/restore/PITR | `infra/docker-compose.yml` |
| 7 | 🟠 | Edge: no read timeout, no circuit breaker | `ProxyClientConfig.java:24` |
| 8 | 🟠 | Rate limiting on 2 services of 8 | `RateLimitFilter` |
| 9 | 🟠 | SQL in `application/service` (17 classes) | `RunSagaService`, `CreditLedgerService`, … |
| 10 | 🟠 | `RunSagaService` does six jobs (612 lines, 25 methods) | `RunSagaService.java` |
| 11 | 🟠 | ~1 500 lines duplicated across services | §5.1 table |
| 12 | 🟡 | `PathResolver` walk-up heuristic breaks in containers | 2× `PathResolver.java` |
| 13 | ✅ | ~~No encryption at rest~~ — **closed** by ADR-054: envelope encryption, per-file data key, master key outside `.env`, 379 files migrated and verified | `MASTER_FEATURES.md` COMP-05 · `engine/sovkora-engine-assets` · commit `2b41a478` |
| 14 | 🟡 | Ambiguous asset resolution (`matches[0]`) | `AssetFileResolver.java` |
| 15 | 🟡 | `MediaFileStore` lacks path containment check | 2× `MediaFileStore.java` |
| 16 | 🟡 | `.tsx` 250-line rule violated by registries | `icon.tsx:841` |
| 17 | 🟡 | `JobCommand` duplicated — a wire contract with two owners | conversation + job |
| 18 | 🟡 | Governance gap: name-based service rule permits SQL inline | `GovernanceRules:365` |
| 19 | 🔴 | `/intent/route` signs `AUTH_PASS` for scopes the caller declares; secret is a committed literal — inert until something verifies the token, then an auth bypass | §2.6 · `IntentController.resolveGate`, `GateService.java:60` · deferred by M1.5 |
| 20 | ✅ | ~~Video composition bills the **intended** duration~~ — **closed** by ADR-063: frames counted and rate read on the written file (constant 30 fps forced so a frame count means seconds); the divergence contract now covers video with the 2 s-voiceover fixture that billed 9.00 for 2.02 s, seen failing on the old composer | `composer.py` `_measure` · `TestCompositionDivergenceContract` · ADR-063 §7 |
| 21 | ✅ | ~~A chat turn whose prompt carries inline media skips the whole interceptor pipeline~~ — **closed** by ADR-063. **Exposure: the chat API itself** — `Base64MediaExtractor` reads the prompt string, so any caller who puts a `[posterBase64: …]` marker in the text skipped the crisis guard, the scope guard, entitlement and credit hold, and `disable-ai` (ADR-062 §2.3's "not established" was wrong). Media chat turns were **never billed** (billing defect D6) | `EnginePipelineBridge.compileContext` · `MediaTurnControlsTest`, five tests each seen failing · ADR-063 §1–§6 |
| 22 | ✅ | ~~Audio has never been billed~~ — and it was FOUR capabilities, not one: audio analysis, speech synthesis, image analysis and video analysis all carried `billable_unit NULL`. **Closed** by ADR-066: each declares a unit chosen from what its executor can measure (KILOCHAR, AUDIO_MINUTE, KILOTOKEN, AUDIO_MINUTE) and each now reports it — the transcription provider's duration travels out of `ProcessedAudioPayload`, TTS reports the characters it sent. `SeedBootstrapIT` asserts every declared unit is priced by a current pricebook row | `AudioAnalysisStrategy`, `SpeechSynthesisStrategy`, `VisionAnalysisStrategy`, `VideoAnalysisStrategy` · ADR-066 §4 |
| 23 | ✅ | ~~A user's own preferences override the pipeline's declarations~~ — **closed** by ADR-064: user-written preferences move under `preference.` at every context builder, and the pipeline merges only the `orazaka.`/`preference.` namespaces, so no preference can name `userId`, `conversationId` or `roles` whatever line order the engine keeps; identity refuses reserved keys (depth). The same shape in the metering path — an executor's report merged over the listener's `gpuSeconds` — closed with it | `DynamicPipelineExecutor.namespaced`, `Context.userPreferences` · `UserPreferencesContractTest` · ADR-064 §1 |
| 24 | ✅ | ~~Five worker tests never run~~ — **closed** by ADR-064: they run, and pass; mutation shows two pass vacuously and one pins an absolute-path hole open (#28). The runner must now collect every test its files define, and every `test_*.py` must have a build execution — which found 27 more tests in `document-validation` that never ran (#29) | `TestTheRunnerCollectsEveryDefinedTest` · ADR-064 §2 |
| 25 | ✅ | ~~The credit gate fails open on a billing error~~ — **chosen** by ADR-064: open, and accounted — an `UnmeteredTurn` is made durable in the host's outbox before the turn is served, collected into billing's `unmetered_turn`; a turn that cannot be recorded is refused. Controls now declare `failsClosed()` instead of inheriting the executor's catch-all | `EntitlementInterceptor`, `UnmeteredTurnListener` · ADR-064 §3 |
| 26 | ✅ | ~~The engine logs every prompt and response at `INFO`~~ — **closed** by ADR-064: eighteen logging sites carried content and log shape instead; [LOG-001] follows every logging argument on the operand stack and fails the build on content | `LoggedContentRules` · nine governance suites · ADR-064 §4 · the open point — guards logged the matched term — **closed** by ADR-065 §4 |
| 27 | ✅ | ~~The job plane outlives the data class~~ — **closed** by ADR-065: `data_class` travels on `JobCommand` as a typed component and is never defaulted (a command without one is refused before anything is stored; the column has no `DEFAULT`); job-service deletes a SENSITIVE/REGULATED job's directory, then its row, at terminal state, with a tombstone that deletes a directory written after the purge. The run trail no longer carries the refusal text: `studio_run_audit.detail` is gone (§2.2) | `JobRetentionService`, `JobListener`, `ProtectedJobRetentionIT`, `RunAuditService` · ADR-065 §1–§2 |
| 28 | ✅ | ~~Compose resolves an absolute path without ownership~~ — **closed** by ADR-065: an absolute path is refused, not checked — the same answer as an id that does not exist; assets resolve by id against the job's actor. `test_an_absolute_path_is_left_alone`, which asserted the path came back untouched, now asserts the refusal. No pack blueprint passes a path | `consumer._resolve_owned_asset` · ADR-065 §3 |
| 29 | 🟡 | `document-validation`'s authenticity check needs `pypdf`, declared nowhere; its 10-test suite crashes on the first. The guard excuses it by name while `pypdf` is absent | `orazaka-packs/document-validation/worker` · ADR-064 §2.2 |
| 30 | ✅ | ~~No billing consumer gives a dedup claim back on a handler exception~~ — **closed** by ADR-067: five consumers now carry the claim → try → release → rethrow shape `UnmeteredTurnListener` has had since M1.7 (billing settlement, studio job outcome, studio subscription, studio connector outcome, knowledge indexing). The five implementations always had `release`; the defect was in who called it. Two consumers deliberately keep their claim — they convert every failure into a terminal outcome, and releasing would re-run a connector action | `JobSettlementListener` and four more · ADR-067 §4 |
| 31 | ✅ | ~~`infra/initdb/30-jobs-config.sql` does not bootstrap a fresh database~~ — **closed** by ADR-066: the comma is back and `SeedBootstrapIT` loads every file of `infra/initdb` into a real Postgres from empty, on the image docker-compose runs. The deliverable was the test; the comma is what it found | `SeedBootstrapIT` · ADR-066 §2 |
| 32 | ✅ | ~~Door 1 accepts a filesystem path~~ — **closed** by ADR-066: `JobListener` drops `filePath` and `imagePath` from every incoming payload and fills them from the ids beside them, resolved against the job's actor; the producers pass ids. The `orazaka.*` key hole on the same endpoint is NOT closed — that is M3 | `JobListener.resolveAssets` · ADR-066 §3 |
| 33 | 🟠 | A protected job that never reaches terminal is never purged: a crash mid-execution leaves it `PROCESSING`, its redelivery is skipped by the unreleased claim (#30), `JobReconciliationService` recovers only jobs with a `result.json`. **M2's report** | `JobListener`, `JobReconciliationService` · ADR-065 §6.3 |
| 34 | 🟠 | A timed-out execution is not cancelled: `CompletableFuture.get(timeout)` abandons it, and it can still write its output and try to mark the job `COMPLETED` after the error was emitted and the hold released. **M2's report** | `JobListener.onMessage` · ADR-065 §6.4 |
| 35 | ✅ | ~~`realestate-studio` bills a b-roll the reel never contains~~ — **closed** by ADR-066: the step is removed and the declared cost drops 6 400 → 2 800. A rule now refuses any step passing an input its executor does not read | `realestate-reels` blueprint, `BlueprintFitnessTest` · ADR-066 §8 |
| 36 | 🟡 | Dead-letter queues nothing consumes (`orazaka.jobs.video.dlq`, `orazaka.events.job-relay.dlq`, worker-bound queues) keep payloads with no window. **M2's report** | `JobsTopologyConfig`, workers · ADR-065 §6.6 |
| 37 | 🟡 | Worker-routed Studio steps have no job row, so the class-driven purge cannot see what a worker writes; no protected step writes today. **M2's report** | ADR-065 §6.7 |
| 38 | 🟡 | STANDARD jobs have no window in the job plane, while STANDARD runs age out at 90 days. **M2's report** | `DataClass.jobRetentionAfterTerminal` · ADR-065 §6.8 |
| 39 | 🟡 | `purgeJobsByUserId` deletes rows and leaves their directories. **M2's report** | `JobPersistenceProviderImpl` · ADR-065 §6.9 |
| 40 | 🟡 | `studio_outbox` rows are never deleted once published, and since ADR-067 the table carries one row per step DISPATCH as well as per event, so it grows faster. The persistence relay's `purgePublished` is the pattern. **M3's report** | `OutboxRelay` (studio) · ADR-065 §6.10, ADR-067 §7 |
| 41 | 🟡 | `StudioRunService.dataClassOf` reads a studio with no pack row as STANDARD — the one default left on the class's path. **M2's report** | `StudioRunService` · ADR-065 §6.11 |
| 42 | 🟡 | Automation connector jobs carry payloads with no class, in a plane with no class-driven retention. **M2's report** | automation-service · ADR-065 §6.12 |
| 43 | 🟠 | `orazaka.studio.media.compose` advertises `clip`, `bRoll`, `captions`, `brandKit` and `aspect` in its `payload_template` and its worker reads `photos` and `audio`; the capability's label promises captions and a brand overlay that do not exist. **M2.5's report** | `consumer._compose` · ADR-066 §8 |
| 44 | 🟡 | `orazaka.core.media.speech` is named `chat.*`, bills as AUDIO and routes to `job.text.process`; `orazaka.core.media.audio.analysis` is analysis and routes to `job.media.generate`. Both placements are deliberate and both names lie. **M4 owns the registry** | `30-jobs-config.sql` · ADR-066 §4.3 |
| 45 | 🟡 | The `orazaka-media` bundle is written but not installed: `orazaka packs install` is a runtime action against running services, so the six Studios exist in the repository and not yet in the local database. **M2.5's report** | `orazaka-packs/orazaka-media` · ADR-066 §6 |
| 46 | ✅ | ~~A provider refusing `verbose_json` makes transcription silently unbillable~~ — **closed** by ADR-067: `JobListener` records an `UnmeteredTurn` for any completed job that held credits and reported nothing its declared unit can price, judged with `ConsumptionReport` itself, and records it before marking the job complete | `JobListener.recordIfUnmetered` · ADR-067 §5 |
| 47 | 🟡 | `IdentityNotificationListener` and `PasswordNotificationListener` cannot fail today — they "send" by logging — so their unreleased dedup claim is safe by accident. The day a real sender lands there it needs the release the other five now have. **M3's report** | automation-service · ADR-067 §7 |

---

## 7. Remediation plan

Each wave ends green (build + every `GovernanceTest`) and is an independent checkpoint. Waves 1–2
are the launch gate; nothing else matters until they are done.

### Wave 1 — Security (blocks launch)

1. Authenticated media endpoint + owner-scoped asset lookup; delete the static handler and the
   `permitAll`; BFF forwards the session JWT and no longer trusts path segments. **+ the IDOR test.**
2. `/internal/v1/**` → `hasAuthority("SCOPE_internal")` in all three services; keep the edge
   non-routing as defence in depth.
3. `job-service` → `anyRequest().authenticated()`.
4. DB passwords out of SQL into env-sourced psql variables; build check against literals.
5. **New fitness function**: an integration test per service asserting that an unauthenticated
   request to a non-permitted path returns `401`. This is what stops finding #3 recurring.

### Wave 2 — Operability (blocks launch)

6. Flyway baseline per context; seeds out of `V*`; `clean-disabled: true`;
   `SqlBoundaryRules` extended to `db/migration/**`.
7. Managed Postgres with PITR + a **performed** restore drill with written RTO/RPO.
8. RabbitMQ quorum queues.
9. Edge read timeout + circuit breaker + per-route concurrency cap.
10. `RateLimitFilter` on identity, billing and studio.
11. `PathResolver` walk-up deleted; upload root required and absolute; fail fast.

### Wave 3 — Structure (before the next feature)

12. Create `orazaka-service-kit`; migrate security, AMQP, outbox, datasource, media.
13. `JobCommand` → Tier-1 `orazaka-jobs-api`.
14. Extract repository ports/adapters out of `application/service`, starting with `RunSagaService`
    and `CreditLedgerService`.
15. Split `RunSagaService` into five collaborators.
16. New rule `assertNoPersistenceApiInApplicationLayer`, wired into all eight service governance
    suites.

### Wave 4 — Hardening (before scale)

17. Encryption at rest (volume + `pgcrypto` on `secrets`); assert `RunScope.toString()` excludes them.
18. Asset resolution by exact id; `MediaFileStore` containment check.
19. Decide the `.tsx` registry exemption and either write it into AGENTS.md or split the files.
20. Load-test the studio fan-out path against the real MLX budget; calibrate the pricebook from
    measured runs (ADR-033 says the estimates are placeholders — they still are).
21. Read replicas for the analytics queries; move `UsageAnalyticsService` and
    `StudioAnalyticsService` off the primary.

---

## 8. What this audit could not verify

Stated plainly, because an audit that hides its own gaps is worse than none.

- **The build was not run** (JDK 11 host, Java 21 target). Compilation, all 424 tests, and every
  `GovernanceTest` are unverified. Run `./mvnw verify` and `orazaka test e2e` and treat any failure
  as a finding of its own.
- **No dependency CVE scan.** Spring Boot is pinned to `4.0.7` and Spring AI to `2.0.0`, which is
  disciplined, but transitive CVEs were not checked. Run OWASP Dependency-Check / `npm audit` /
  `pip-audit` and add them to the local gate.
- **No runtime profiling.** Connection-pool sizing (`maximum-pool-size: 10`) versus virtual-thread
  concurrency is a classic mismatch — thousands of virtual threads contending for ten connections
  serialises the system silently. Measure it before assuming.
- **No penetration test.** §2.1 and §2.3 were found by reading; assume more exist and budget an
  external review before launch.
- **The Python media worker and the mobile client were reviewed only superficially.**

---

## Related documentation

- [Governance contract](../AGENTS.md) · [Vision architecture](VISION_ARCHITECTURE.md)
- [Billing architecture](BILLING_ARCHITECTURE.md) · ADR-033
- [Studio architecture](STUDIO_ARCHITECTURE.md) · ADR-034
- [Microservices target architecture](MICROSERVICES_TARGET_ARCHITECTURE.md) · ADR-032
- [Security standards](../.agent/rules/security_standards.md) · [Testing standards](../.agent/rules/testing_standards.md)
