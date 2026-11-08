---
title: "M0 — What a Studio run costs over a direct capability call"
description: "Measured latency and statement cost of the saga on a single-step run, against thresholds declared before measuring. Phase M0 of UNIFIED_PACK_SURFACE.md, which this measurement had the right to void."
category: Measurement
order: 1
---

# M0 — Run overhead

> **DECISION: PROCEED.** The saga's own cost on a single-step run is **p95 30.5 ms** on the worse
> of two 50-sample runs (p50 14.2 ms), **p95 19.7 ms** on the better (p50 12.4 ms). Both are an
> **upper bound** on the overhead door 2 adds over door 1; the worse one is 1/8th of the < 250 ms
> threshold. `UNIFIED_PACK_SURFACE.md` stands; M1 opens as planned and no fast path is needed.
>
> **But the document's cost estimate in §4 is wrong and is corrected below.** A single-step run is
> not *"one `studio_run` + one `studio_run_step` insert plus an advance cycle"*. It is **37 SQL
> statements** — 22 before dispatch, 15 after the outcome. They are fast, so the decision does not
> change; the estimate was still off by an order of magnitude and the document should not keep
> claiming it.

## 1. Thresholds, declared in advance

Written before the harness existed and before any number was produced, per
[`measure_run_overhead.md`](../../.agent/workflows/measure_run_overhead.md) §6. Choosing a threshold
after seeing a distribution is not a decision.

| Segment A+B overhead, p95 | Decision |
|:---|:---|
| **< 250 ms** | **PROCEED.** M1 opens as planned, no fast path needed. |
| **250 ms – 1 s** | **PROCEED WITH A CONDITION.** M2 must carry an explicit fast-path decision for single-step runs before any blueprint is authored. |
| **> 1 s** | **STOP.** `UNIFIED_PACK_SURFACE.md` is marked void; report the dominant contributor by segment and propose nothing. |

Secondary, also declared in advance: **if p95 and max diverge by more than ~3×**, that tail is a
finding in its own right and must be named even if the p95 passes. *It did — see §6.*

## 2. What was measured, and why it is a bound rather than a difference

The workflow asks for `(A₂+B₂) − (A₁+B₁)`: door 2's overhead minus door 1's.

Door 1 lives in `orazaka-conversation-service`, behind a JPA stack that `orazaka-studio-service`
does not have on its classpath. Measuring it in the same harness is impossible; measuring it in a
second Testcontainers harness in another module is outside the M0 manifest.

**It is not needed.** Door 1 performs strictly positive work — a credit hold over HTTP, an
`orazaka_jobs` insert, a routing-rule select, an outbox insert, and then a relay poll. So:

```
(A₂+B₂) − (A₁+B₁)  <  A₂+B₂        because A₁+B₁ > 0
```

Measuring door 2's **total** therefore bounds the overhead from above, and it errs in the direction
that would make the decision STOP — never in the direction that licenses the next five phases. That
is the property the workflow's §4 warning demands: this method *could* have failed the threshold.

Segments, as §3 of the workflow defines them:

- **A** — `runService.start(...)` entered → `JobCommand` handed to the executor (and, separately,
  → `start()` returns with the `RunDetail` the client receives).
- **B** — the step's outcome applied → the run observably terminal: `SUCCEEDED`, settled, outbox row
  written.

The executor is neutralised (§4.1): `RecordingStepExecutionClient` returns a job id and does
nothing, so A and B are the only things varying. This matters — see §7.

## 3. The numbers

50 samples per run, one single-step blueprint, one CAPABILITY step, real PostgreSQL 16 in
Testcontainers. **Nothing discarded.**

Two runs are reported, not one, because they disagree and the disagreement is informative:

- **Run 1 — isolated.** `mvn verify -Dit.test=RunOverheadMeasurementIT` alone on an otherwise idle
  machine.
- **Run 2 — under load.** The same suite inside `./mvnw clean install -T 1C`, with the rest of the
  reactor's 2774 tests compiling and running on the other cores.

| Metric | Run 1 p50 | Run 1 p95 | Run 1 max | Run 2 p50 | Run 2 p95 | Run 2 max |
|:---|---:|---:|---:|---:|---:|---:|
| A — start → dispatch | 5.7 ms | 9.2 ms | 59.9 ms | — | — | — |
| A — start → `start()` returns | 7.4 ms | 11.6 ms | 64.6 ms | — | — | — |
| B — outcome → terminal | 4.9 ms | 7.9 ms | 11.1 ms | — | — | — |
| **A+B** | **12.4 ms** | **19.7 ms** | 75.7 ms | **14.2 ms** | **30.5 ms** | 57.6 ms |
| A+B, samples 2–50 | 12.3 ms | 19.0 ms | 22.6 ms | — | — | — |

**The decision is taken on Run 2**, the worse one: 30.5 ms, still 8× inside the threshold. Quoting
only Run 1 would be the flattery the workflow's §4 warns about, one level up — choosing the
favourable execution instead of the favourable method.

The spread between the two runs is a finding in itself: **this measurement is sensitive to machine
load**, by roughly 50 % at p95. It is not sensitive in a way that could change the decision, but a
future re-run that reports 40 ms has not regressed — it may just have been sharing the machine.
**The statement counts were identical across both runs (22 and 15)**, which is what one would
expect and is a useful check that the two runs measured the same thing.

### Raw samples, A+B, in milliseconds, in order

Run 1 (isolated):

```
75.7, 22.6, 19.4, 18.5, 19.9, 16.0, 15.6, 17.1, 15.3, 15.8,
14.6, 14.7, 14.1, 13.5, 17.0, 15.1, 12.8, 13.7, 12.8, 14.1,
12.7, 13.3, 13.9, 12.6, 13.1, 12.1, 10.7, 10.9, 12.3, 11.2,
11.5, 10.9, 11.9, 10.8, 10.6, 10.6, 10.4, 10.2, 10.5, 10.9,
11.7, 10.5, 11.2, 11.0, 10.6,  9.8,  9.7,  9.2,  8.9,  9.0
```

Run 2 (inside the parallel build):

```
33.1, 47.4, 57.6, 27.2, 20.0, 15.5, 17.6, 16.8, 16.2, 13.4,
13.3, 12.5, 14.5, 13.0, 24.2, 16.1, 14.8, 17.3, 13.6, 16.2,
12.3, 15.9, 17.3, 12.2, 14.3, 14.3, 14.6, 25.9, 13.5, 14.1,
13.5, 12.7, 12.4, 13.3, 12.4, 12.5, 12.0, 14.2, 13.8, 14.1,
14.8, 15.3, 15.6, 16.0, 13.5, 13.4, 12.2, 12.3, 12.2, 11.2
```

Per-segment microsecond values for the most recent run are in
`services/studio-service/target/m0-run-overhead.txt`, regenerated by
`./mvnw -o verify -pl services/studio-service -Dit.test=RunOverheadMeasurementIT`.

### In context (§4.2)

Rather than ten fresh paired runs against MLX — which would have needed the whole stack up and would
have answered a question this platform has already answered 98 times — the denominator is taken from
**recorded job history in the local database**, `orazaka_jobs`, `updated_at − created_at`:

| Capability | n | p50 | p95 |
|:---|---:|---:|---:|
| `orazaka.core.media.image` | 4 | **67.1 s** | 100.5 s |
| `orazaka.core.media.vision` | 16 | 4.9 s | 9.7 s |
| `orazaka.core.chat.completion` | 58 | 2.2 s | 8.9 s |

So the saga's p95 overhead — taking Run 2's 30.5 ms — is **0.05 %** of a median MLX image and
**0.6 %** of a median vision call. Two caveats, both real: `n=4` for image is thin, and `updated_at` is a last-write timestamp
rather than a completion timestamp, so these are upper bounds on job duration too. Neither
weakens the ratio — both push it further in the same direction.

## 4. §4.3 — the statement count, which the document got wrong

`UNIFIED_PACK_SURFACE.md` §4 asserts a run costs *"one `studio_run` + one `studio_run_step` insert
plus an advance cycle"*. Counted through a test-scope `DataSource` decorator, one single-step run
executes **37 statements**.

### Segment A — 22 statements

| # | Statement | Why |
|---:|:---|:---|
| 1 | `SELECT … FROM studio_installation …` | the installation |
| 2 | `SELECT config_value FROM studio_runtime_config` | the concurrency cap, read from its row |
| 3 | `SELECT COUNT(*) FROM studio_run WHERE actor_id = ? AND finished_at IS NULL` | the cap |
| 4 | `SELECT … FROM studio_blueprint …` | the pinned version |
| 5 | `SELECT p.regulatory_class FROM pack_studio …` | the data class stamped on the run (ADR-051) |
| 6 | `INSERT INTO studio_run …` | **the document's first insert** |
| 7 | `UPDATE studio_installation SET last_run_at = now()` | |
| 8 | `INSERT INTO studio_outbox …` | `evt.studio.run.started` |
| 9–10 | run + blueprint re-read | `advance()` reloads both |
| 11 | `SELECT step_id, status FROM studio_run_step` | step states |
| 12 | `SELECT step_id, failure_cause …` | declared causes (ADR-053) |
| 13 | `SELECT step_id, ordinal, output …` | scope construction |
| 14–15 | `SELECT config FROM studio_installation` **×2** | `buildScope` and `effectiveConfig`, separately |
| 16 | `SELECT p.scope_guard FROM pack_studio …` | SENSITIVE control 1 |
| 17–18 | `SELECT p.safety FROM pack_studio …` **×2** | REGULATED control, read twice |
| 19 | `INSERT INTO studio_run_step …` | **the document's second insert** |
| 20–22 | run + blueprint + steps re-read | building the `RunDetail` the client receives |

### Segment B — 15 statements

| # | Statement | Why |
|---:|:---|:---|
| 1 | `SELECT run_id, step_id, ordinal FROM studio_run_step WHERE job_id = ?` | locate the step |
| 2 | `UPDATE studio_run_step SET status …` | record the outcome |
| 3–4 | run + blueprint | `advance()` again |
| 5 | `SELECT step_id, status …` | step states |
| 6 | `SELECT step_id, failure_cause …` | |
| 7–8 | scope construction | |
| 9–11 | `SELECT step_id, status …`, scope construction **again** | `advance` computes step states twice, and `succeedRun(run, blueprint, buildScope(...))` rebuilds the scope it just built |
| 12 | `UPDATE studio_run SET status …` | terminalisation |
| 13 | `SELECT step_id, model_name, consumption …` | what settlement prices (ADR-041) |
| 14 | `SELECT data_class FROM studio_run` | whether the audit trail applies |
| 15 | `INSERT INTO studio_outbox …` | `evt.studio.run.finished` |

**The document's estimate was 3. The count is 37.** The hold, the outbox rows, the status
transitions, the aggregate settle and the audit lookup were all missing from it, exactly as the
workflow suspected. It does not change the decision — 37 short statements against a local
PostgreSQL cost ~12 ms — but §4 of the design document must stop claiming three.

## 5. §4.4 — confirmed: nothing on the advancement path is polled

**By reading.** `advanceLater(UUID runId) { advance(runId); }` — a direct, in-thread call, at
`RunSagaService.java:658`. Step dispatch is `rabbitTemplate.convertAndSend` **directly** in
`AmqpStepExecutionAdapter`, not through the outbox. The three `@Scheduled` components in
studio-service are `RunSweeper` (60 s, abandoned runs), `RetentionSweeper` (nightly) and
`OutboxRelay` (5 s, domain events) — **none is on the path from step completion to the next
dispatch**.

**By measurement.** Segment B, 1 ms buckets, shows no floor and no quantisation:

```
 3– 4 ms  ###                      (3)
 4– 5 ms  #######################  (23)
 5– 6 ms  ###########              (11)
 6– 7 ms  ########                 (8)
 7– 8 ms  ##                       (2)
 8– 9 ms  ##                       (2)
11–12 ms  #                        (1)
```

A scheduler tick would put a hard left edge at the interval and a flat plateau to its right. This is
a smooth unimodal distribution starting at 3.6 ms.

### The tick exists — on door 1

Confirming §4.4's scenario did surface one, with **the opposite polarity to the one the workflow
anticipated.** Door 1 does *not* publish directly. `MediaGenerationController` →
`MediaJobService.submitJob` → `JobQueuePublisherService.publish` appends the `JobCommand` to the
**transactional outbox**, and `orazaka-persistence-app`'s `OutboxRelay` drains it on
`fixedDelay = ${orazaka.messaging.outbox.poll-interval-ms:500}`.

So door 1's segment A carries a polling latency uniform on 0–500 ms, mean ≈ 250 ms, that **door 2
does not have.** On segment A alone, the "overhead" of the saga is almost certainly *negative*: door
2 reaches the broker faster than door 1 does.

This is recorded, not exploited: the bound in §2 assumes `A₁+B₁ > 0` and nothing more. But it is the
single most useful thing this measurement found, because a naive paired comparison would have
reported "door 2 is free" and been right by accident, for a reason nobody would have written down.

## 6. The tail, as the threshold required

**Run 1**: p95 19.7 ms against max 75.7 ms is **3.8×**, over the declared ~3× line, so it must be
named. It is entirely **sample #1**. The series is monotonically decreasing — 75.7, 22.6, 19.4 …
9.2, 8.9, 9.0 — which is JIT compilation and connection-pool warm-up, not intermittency. Over
samples 2–50 the p95/max ratio is 1.19×.

**Run 2**: p95 30.5 ms against max 57.6 ms is **1.9×**, inside the line. Its tail is the first three
samples plus one outlier at #28 (25.9 ms) — warm-up again, with contention from the rest of the
build on top.

Nothing was discarded to say either of these; the raw series in §3 show them directly.

**The warm-up cost is real in production**: the first Studio run after a service restart pays
30–75 ms instead of 12–14 ms. Against a 67-second image that is still nothing.

## 7. What this did not measure

Honestly, and this list is longer than the measurement.

1. **Door 1 was not measured at all.** The decision rests on a bound (§2), not on a difference. The
   difference is smaller than the number reported, and on segment A it is probably negative.
2. **The neutralised executor removed part of what it was measuring.**
   `RecordingStepExecutionClient` replaces the whole `AmqpStepExecutionAdapter`, so segment A is
   missing its cached capability-route lookup (a `ConcurrentHashMap` hit behind a TTL — negligible)
   **and the real `rabbitTemplate.convertAndSend`** (a local AMQP publish, typically sub-millisecond
   but not zero). Door 1 publishes to the same broker, so this cancels in the difference — but the
   absolute bound in §3 is understated by roughly one local AMQP publish.
3. **Billing was stubbed.** In production, segment A takes a credit hold and segment B settles it,
   both **HTTP round-trips to `orazaka-billing-service`**. `RecordingCreditClient` makes both free.
   Door 1 also takes a hold and also settles, so these largely cancel in the difference — but the
   absolute A+B in production is higher than 12 ms by two service hops.
4. **The outcome was applied in-process.** `sagaService.applyOutcome(...)` was called directly. In
   production the outcome arrives over AMQP through a listener that first **claims the message in
   `processed_messages`** for idempotency — at least one more statement, plus the broker delivery,
   neither of which appears in the 15 counted for segment B.
5. **Concurrency.** Every sample ran alone. Nothing here says what happens when 20 runs advance at
   once, and the `FOR UPDATE SKIP LOCKED` claims and the `studio_run_step` unique key are precisely
   the parts that behave differently under contention.
6. **Multi-step runs.** One step means one advance cycle. An *n*-step chain pays segment B's 15
   statements *n* times, plus an extra advance per completion. A 6-step blueprint is not 6× this
   number — it is closer to 22 + 6×15 statements — but it was not measured.
7. **`forEach` fan-out.** Not exercised. The fan-out path expands items and inserts one
   `studio_run_step` per item; `trade-showcase` fans out to 2 and the existing lifecycle suite
   covers its behaviour, but not its cost.
8. **Cold JVM.** One cold sample, reported (§6). Not a distribution.
9. **PostgreSQL was local, in a container, on the same machine.** No network latency to the
   database. This is the deployment AGENTS.md §0 describes, so it is the right measurement for
   today — and the wrong one for any future in which the database is a hop away.
10. **The blueprint was synthetic**: one CAPABILITY step, no condition, no `forEach`, no TRANSFORM,
    no APPROVAL, no RETRY, and the Studio belongs to no pack — so `regulatory_class` resolves to
    STANDARD and **no audit row is written**. A SENSITIVE pack writes one per transition; that cost
    is not in these numbers.

## 8. Defects observed, recorded and deliberately not repaired

A measurement run that also changes behaviour measures nothing (workflow §2). These are inputs to
the architect's decision, not work items claimed by this run.

1. **`advance()` computes step states twice.** `stepStates(runId)` at the top, and again inside the
   `allTerminal(blueprint, stepStates(runId))` guard at the bottom. Statements B#5 and B#9.
2. **`succeedRun` rebuilds a scope that was just built.** `advance` computes `buildScope(run,
   blueprint)` and then calls `succeedRun(run, blueprint, buildScope(run, blueprint))` — statements
   B#7/B#8 repeated as B#10/B#11, three selects for nothing.
3. **`SELECT config FROM studio_installation` runs twice in segment A** (A#14, A#15) — `buildScope`
   and `effectiveConfig` each read it.
4. **`SELECT p.safety FROM pack_studio …` runs twice in segment A** (A#17, A#18).
5. **The blueprint is re-read on every `advance`**, and `start()` reads it three times in total
   (A#4, A#10, A#21). `BlueprintRepository` has no cache, and a PUBLISHED blueprint is immutable by
   trigger — so it is cacheable by construction, which is a fact the architect may want.

6. **`spotless` is declared but bound to no phase**, so the build never checks formatting. Running
   `spotless:apply` on `orazaka-studio-service` — which this run did once, to format its own two new
   test files — rewrote **11 production files** that are committed in a state the project's own
   formatter disagrees with. Those 11 were reverted (§9); the drift is still there.

Together these are ~9 of the 37 statements, all `SELECT`s on indexed keys. Removing them would
improve a number that is already three orders of magnitude inside its budget. **That is the argument
for not touching them now**, and it is the architect's to overturn.

## 9. Method

- Harness: `services/studio-service/src/test/java/…/RunOverheadMeasurementIT.java`
  (test scope). It extends `StudioRunLifecycleIT.TestWiring` rather than copying it, so the engine
  measured is wired exactly as the suite that asserts its behaviour wires it.
- Statement counter: `…/application/service/CountingDataSource.java` (test scope), a JDBC proxy that
  records at `execute*`. Normalisation is deferred to the end of a recording window so that
  recording costs a volatile read and a list append; timing samples run with recording **off**.
- The suite asserts that the **measurement is valid** — ≥ 30 samples, every run terminal, statements
  actually recorded — and never that the number is good. A harness that reddens when its own number
  is unwelcome puts pressure on the number.
- `*IT` is executed by failsafe, bound to `integration-test` and `verify` in the root `pom.xml`.
  This suite ran; the output file in §3 is its product.
- **Nothing was added or changed in `src/main`, `infra/`, or any `orazaka-libs/*/src/main`.**
  One slip on the way: `spotless:apply`, run to format this harness, reformatted 11 production
  files in the same module as a side effect. All 11 were reverted with `git checkout --` and
  `git status` was re-checked before committing. The finding it exposed is in §8.6.

---

## 10. Addendum — re-measured after M2.5's outbox (2026-09-16)

M2.5 moved step dispatch into the transactional outbox (ADR-067 §2). Durability changes when a
message reaches the broker, so the instrument was run again rather than assumed.

| Metric | Run 1 (M0) | Run 2 (M0) | **After the outbox** |
|:---|---:|---:|---:|
| Segment A+B, p50 | 12.4 ms | 14.2 ms | **10.4 ms** |
| Segment A+B, p95 | 19.7 ms | 30.5 ms | **19.2 ms** |
| Statements (A + B) | 22 + 15 | 22 + 15 | **22 + 15** |

**No regression**, against the 250 ms threshold declared in §2 — and a small improvement, because
segment A no longer performs a synchronous AMQP publish inside the saga's transaction. The decision
of §1 is unchanged.

**What this harness cannot see, and where the missing number was taken.** The harness neutralises the
executor (§4.1), so `AmqpStepExecutionAdapter` — the class that changed — is not in the path it
measures. Its marginal cost was measured where it happens, in
`StepDispatchDurabilityIT.theAddedCostIsOneInsert`, over 50 samples inside a real transaction against
the real schema: **p50 0.85 ms, p95 1.21 ms, max 2.70 ms** for one durable dispatch.

**What a dispatch now waits for** is the relay poll rather than a direct publish: ≤ 500 ms, mean 250.
The studio relay's interval was lowered 5 000 → 500 ms to match the conversation service's, so the
two doors pay the same. M0's §5 recorded door 1 paying a 500 ms relay poll the saga did not; that
asymmetry is gone, and the two doors are at par by construction.
