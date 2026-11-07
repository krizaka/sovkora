---
description: UNIFIED SURFACE — PHASE M0 (measure the saga overhead on a single-step run; this phase has the right to void the rest of the plan)
---

# Workflow: Phase M0 — measure before you unify

Design intent: [`docs/UNIFIED_PACK_SURFACE.md`](../../docs/UNIFIED_PACK_SURFACE.md) — §1 (two front
doors, one engine), §4 (the cost table), §5 (the M0–M5 sequence).
**The design document is normative for *what* and *why*. This workflow is normative for *how*. Where
they disagree, the design document wins and this file is corrected — except on §4 below, where this
workflow deliberately corrects the design document and says so.**

> **This phase writes no production code.** It produces two numbers and a decision. If the numbers
> are bad, the correct output of this run is *"stop, here is why"* and `UNIFIED_PACK_SURFACE.md` is
> marked void. That is a success, not a failure.

## §0 Init

1. Load `AGENTS.md` — §0 (100% local), §2 (tiers), §6 (messaging: the synchronous/deferred line),
   §9, §11.
2. Load `.agent/rules/testing_standards.md`, `messaging_standards.md`.
3. Read `docs/UNIFIED_PACK_SURFACE.md` §1, §4, §5 in full.
4. Read, **before writing anything**:
   - `services/studio/sovkora-studio-service/…/application/service/RunSagaService.java` —
     specifically `advance` (line ~292), `advanceLater` (~658), and every `jdbcTemplate` call on the
     path from run creation to first dispatch
   - `services/studio/sovkora-studio-service/…/adapter/rest/StudioRunController.java` —
     `POST /installations/{installationId}/runs` is door 2's entry
   - `services/conversation/sovkora-conversation-service/…/adapter/rest/MediaGenerationController.java`
     — `POST /api/v1/media/generation/{image,speech,video}` is door 1's entry
   - `services/studio/sovkora-studio-service/src/test/java/…/StudioRunLifecycleIT.java` —
     **this is the harness you extend. Do not build a second one.**

## §1 Scope of THIS run — M0 only

Measure the latency and the statement cost that door 2 adds over door 1 for **one single-step job**,
and decide.

**In scope** — exactly the file manifest of §5.

**Out of scope, do NOT start** — `pack.kind` or `TOOLKIT` derived installation (M1), the
`orazaka-media` bundle or any blueprint intended to ship (M2), deleting `MediaGenerationController`
or `useBootstrapFeatures` (M3), dropping `uri_path` / `http_method` / `payload_template` (M4), the
bypass governance rule (M5). If M0 is measured and reported and you have budget left, **stop and
report**.

## §2 Non-negotiable constraints

| Rule | Applies here as |
|:---|:---|
| AGENTS.md §0 | 100% local. No CI, no cloud, no new network dependency, no benchmarking service. |
| AGENTS.md §2 | Instrumentation lives in **test scope**. Nothing you add for measurement ships in `src/main`. If you cannot measure something without touching `src/main`, say so and stop — do not add a permanent timer to buy a number. |
| AGENTS.md §6 | The wire format does not change. You are timing it, not redesigning it. |
| **No fixing** | You will find defects. **Record them in the report and do not repair them.** A measurement run that also changes behaviour measures nothing, and this phase's output is trusted only if the code under it is the code that exists today. |
| **No benchmark that flatters** | See §4. A number produced by a method that cannot detect the failure it is screening for is worse than no number — it licenses the next five phases on a false premise. |
| **Thresholds declared before measuring** | §6. Write them down, then measure. Choosing the threshold after seeing the distribution is not a decision, it is a rationalisation. |

## §3 What is actually being compared

One job, two front doors, same capability, same model, same payload.

```
door 1  POST /api/v1/media/generation/image
          → MediaGenerationController
          → publish JobCommand on orazaka.jobs
          ⋯ worker ⋯
          → job result persisted                              ← user can see it here

door 2  POST /api/v1/studios/installations/{id}/runs
          → RunSagaService: credit hold, studio_run insert,
            studio_run_step insert, outbox row, advance
          → AmqpStepExecutionAdapter: publish JobCommand on orazaka.jobs
          ⋯ same worker, same executor, same model ⋯
          → step completion → advance → terminal transition
            → aggregate settle → audit row
          → run SUCCEEDED                                     ← user can see it here
```

**The overhead has two segments, and both count**, because the user waits through both:

- **A — pre-dispatch**: HTTP request received → `JobCommand` handed to the broker.
- **B — post-completion**: worker reply consumed → run observably terminal (status `SUCCEEDED`,
  settle done, audit row written).

Door 1 has an A and a B too; they are just smaller. **The number that decides this is
`(A₂+B₂) − (A₁+B₁)`,** not door 2's total.

## §4 The correction to the design document — read this before designing the test

`UNIFIED_PACK_SURFACE.md` §5 specifies M0 as *"same prompt, same model, ten runs each"* — i.e. an
end-to-end wall-clock comparison against the real model. **That method cannot detect the failure it
exists to screen for**, and following it literally would be the defect this project has already
found seven times: a green signal that means less than it appears.

The reason: MLX image generation takes seconds and its run-to-run variance is itself on the order of
hundreds of milliseconds. An 800 ms saga overhead sits *inside* that variance. Ten samples of a noisy
multi-second measurement cannot resolve a sub-second difference, so the comparison would come back
"no meaningful difference" whether the overhead is 20 ms or 800 ms — and it would be believed.

So measure in two parts:

**4.1 — The overhead, isolated (this is the number that decides).**
Neutralise the model. Use a capability whose execution is effectively constant and near-zero — a
stub executor, a test-profile fake worker, or an existing fast capability — so that A and B are the
*only* things varying. Reuse whatever `StudioRunLifecycleIT` already does to avoid real inference;
**if it already has a stub, that is your instrument.** Target ≥ 30 samples per door, report the full
distribution, discard nothing.

**4.2 — The ratio, in context (this is the number that makes it meaningful).**
Then, and only then, run both doors against the real MLX path — the design document's ten runs each
is enough here, because this measurement answers *"what fraction of the wait is overhead?"*, not
*"how big is the overhead?"*. Report the overhead from 4.1 as a percentage of the median total from
4.2.

**4.3 — Statement count (the claim the document makes without evidence).**
`UNIFIED_PACK_SURFACE.md` §4 asserts door 2 costs *"one `studio_run` + one `studio_run_step` insert
plus an advance cycle"*. **Verify it.** Count the actual SQL statements executed per single-step run,
end to end, both doors. A `DataSource` proxy or `JdbcTemplate` wrapper in test scope is the cheap way;
`pg_stat_statements` deltas are acceptable. If the real count is materially higher than three — and
the hold, the outbox row, the status transitions, the settle and the audit row suggest it is — then
the document's cost estimate is wrong and the report must say so with the actual list.

**4.4 — One specific thing to confirm, because it is the only scenario that changes the answer.**
`advanceLater` appears to call `advance` directly, in-thread, so there is no scheduled-poll latency
between step completion and the next dispatch. **Confirm this by reading, then confirm it in the
measurement** (segment B should show no quantised floor at a scheduler interval). If it turns out any
part of advancement is polled or delayed by a fixed tick, that tick is the overhead, it dominates
everything else, and it must be the headline of the report.

## §5 File manifest — M0

```
# ── the measurement harness (test scope only) ──────────────────────────────
CREATE  services/studio/sovkora-studio-service/src/test/java/.../RunOverheadMeasurementIT.java
          segments A and B, both doors, ≥30 samples against a neutralised executor (§4.1)
          + ≥10 samples against the real path (§4.2)
CREATE  <test scope> a statement-counting DataSource/JdbcTemplate decorator      (§4.3)
          — reuse an existing test utility if one exists; search before creating
MODIFY  services/studio/sovkora-studio-service/src/test/java/.../StudioRunLifecycleIT.java
          only if its fixtures must be extracted to be shared. Prefer extracting to a
          test fixture class over duplicating, and do not change what it asserts.

# ── the output ─────────────────────────────────────────────────────────────
CREATE  docs/measurements/M0-run-overhead.md
          the raw numbers, the method, the thresholds as declared in advance,
          the decision, and §7.4 (what was NOT measured)
MODIFY  docs/UNIFIED_PACK_SURFACE.md
          §5: M0 row → outcome + link. If the decision is STOP, add a banner at the
          top of the document marking it void and saying which number voided it.
          Also correct §5's M0 method to §4 of this workflow — the document's own
          spec was too weak, and leaving it uncorrected invites a re-run of the
          wrong measurement.
```

**Nothing in `src/main`. Nothing in `infra/initdb/**`. No new Maven module.** If the manifest needs
to grow past this, stop and report why — a measurement that needs production changes is telling you
something, and that something belongs in the report, not in the diff.

## §6 Decision thresholds — write these into the report BEFORE you run anything

Baseline: the shortest realistic job on this platform is multi-second. Overhead is judged against a
user waiting for a generated image, not against a microbenchmark.

| Segment A+B overhead, p95 | Decision |
|:---|:---|
| **< 250 ms** | **PROCEED.** Under ~10% of the shortest real job. M1 opens as planned, no fast path needed. |
| **250 ms – 1 s** | **PROCEED WITH A CONDITION.** Report it, and M2 must carry an explicit fast-path decision for single-step runs before any blueprint is authored. Do not design the fast path now. |
| **> 1 s** | **STOP.** Mark `UNIFIED_PACK_SURFACE.md` void, report the dominant contributor by segment, and propose nothing. The next decision is the architect's, not this run's. |

Report the **p50, p95 and max** of each distribution. If p95 and max diverge by more than ~3×,
something is intermittent — that tail is a finding in its own right and must be named, even if the
p95 passes.

## §7 Before declaring done

1. `./mvnw -q verify` green for the modules you touched. The harness must actually **execute** —
   `*IT` matches failsafe, not surefire (see the root `pom.xml` comment at ~line 481). A benchmark
   that silently never ran is precisely the class of defect this project keeps finding.
2. `git status` shows **no change under `src/main`, `infra/`, or `sovkora-libs/*/src/main`.** Prove
   it by pasting the output.
3. The report contains the **raw sample values**, not only summary statistics. Someone must be able
   to recompute your percentiles.
4. The report has a section **"what this did not measure"**, and it is honest. At minimum it should
   consider: concurrency (all measurements are single-run), a multi-step run, `forEach` fan-out, a
   cold JVM vs a warm one, and whether the neutralised executor of §4.1 changed the code path in a
   way that removed part of the overhead being measured.
5. The report states the decision in one sentence at the top, before the method.
6. **Do not commit** unless the build is green. If green: commit. **Never push.**

## §8 Do NOT

- Do **not** implement M1–M5, or any part of them, "since we are in here anyway".
- Do **not** optimise the saga. If you find something slow and obviously fixable, that is a finding
  for the report and an input to the architect's decision. Fixing it changes the number you were
  asked to produce.
- Do **not** repair defects found on the way. Record them.
- Do **not** add a permanent metric, timer, or log line to `src/main` to obtain a number.
- Do **not** touch `orazaka.core.chat.completion` or any synchronous streaming path. AGENTS.md §6
  keeps interactive completion off the broker and out of the run saga; it is not in scope, not being
  measured, and not negotiable.
- Do **not** report a conclusion the samples do not support. "The overhead is negligible" requires a
  distribution that could have shown otherwise.

## §9 Next phases — for context only, do not start

M1 `pack.kind` + `TOOLKIT` derived installation (on the critical path: closing door 1 without it
removes image generation from anyone who has not installed the pack) → M2 the `orazaka-media` bundle
→ **M3 close door 1** (the irreversible, value-delivering step) → M4 drop the UI-manifest columns →
M5 the governance rule that stops the bypass growing back.
