---
title: "Sovkora — One surface: should media generation become a pack?"
description: "Architecture analysis of unifying direct media generation and analysis into the pack system: what is actually separate today, why the real seam is synchronous vs deferred rather than media vs studio, the governance bypass the direct path creates, and a phased evolution."
category: Architecture
order: 10
---

# Sovkora — One surface: should media generation become a pack?

> **The proposal.** Media generation and analysis are invoked outside the pack system. Fold them
> into a media pack under a `tool` category, so every capability reaches the user as a pack and the
> UI manages only Studios.
>
> **The verdict: yes, and for a better reason than the one given.** But three things in the framing
> need correcting first, and one of them changes where the line falls.
>
> Nothing is deployed. No migration plan is needed — which is exactly why this is the moment.

---

## 1. What is actually separate today

Less than it looks, and the part that *is* separate is not the part the proposal names.

**The substrate is already shared.** A Studio blueprint step references a `feature_key` from
`orazaka_capabilities`. So does `MediaGenerationController`. Both publish a `JobCommand` onto
`orazaka.jobs`, routed by the row's `routing_key`, executed by the same `JobExecutor` or the same
Python worker, settled through the same pricebook. There are not two engines. There is one engine
with **two front doors**.

```
                    ┌─ door 1: the feature registry ────────────────┐
                    │  orazaka_capabilities.uri_path                │
UI (chat composer) ─┤  + payload_template + icon                   ├─┐
useBootstrapFeatures│  → POST /api/v1/media/generation/image        │ │
executeFeature      └───────────────────────────────────────────────┘ │
                                                                      ├─→ orazaka.jobs
                    ┌─ door 2: the pack/Studio path ────────────────┐ │   → executor
UI (/studios) ──────┤  blueprint step → RunSagaService              ├─┘   → pricebook
                    │  → AmqpStepExecutionAdapter                   │
                    └───────────────────────────────────────────────┘
```

Door 1 is not a separate page — there is no playground route. It is the **button row in the chat
input bar**: `useBootstrapFeatures()` reads `/api/v1/features`, and `executeFeatureWithPrompt` POSTs
to whatever `uri_path` the row declares. `orazaka_capabilities` is therefore doing two jobs at once:
it is the dispatch table (`routing_key`, `handler_key`) **and** a UI manifest (`uri_path`,
`http_method`, `payload_template`, `icon`).

That double duty is the actual separation. It is not two systems; it is one table wearing two hats.

> **Status: door 1 is closed** since ADR-068 (M3). The diagnosis above is
> kept as written because it is the argument for the plan, not a description of today: the endpoints
> in that diagram are deleted, the chat bar's button row is served from Studios, and the UI-manifest
> columns it names have no consumer left — which is what makes M4 a deletion.

---

## 2. Three corrections to the framing

### 2.1 The real seam is synchronous vs deferred — not media vs studio

`orazaka.core.chat.completion` is in the same registry as image and video. But AGENTS.md §6 is
categorical: *interactive chat streaming stays synchronous (token-by-token SSE), never through the
broker.* A Studio run is a saga — DB rows, an AMQP round-trip, an advance cycle. **Interactive chat
cannot become a Studio run without breaking the latency contract that §6 exists to protect.**

Every other capability in the registry is *already* deferred: image, video, speech, vision,
audio, video-analysis, compose. They already produce a job row and already wait on the broker.
Turning those into single-step Studio runs costs one `studio_run` row, one `studio_run_step` row and
one saga advance. It changes their nature not at all.

So the line is not around *media*. It is around *deferral*:

| Capability | Today | After |
|:---|:---|:---|
| `chat.completion` (streaming) | synchronous, direct | **unchanged** — stays direct |
| `chat.speech`, `media.image`, `media.video`, `media.vision`, `media.audio`, `media.video.analysis`, `studio.media.compose` | async job via door 1 | single-step Studio in a toolkit pack |

Drawing the line at "media" would have moved chat completion and broken §6. Drawing it at
"deferred" is both correct and wider than the proposal.

### 2.2 The value is not UI simplification — it is closing a governance bypass

This is the argument the proposal is missing, and it is much stronger than the one it makes.

A direct invocation produces an `orazaka_jobs` row. It does **not** produce a `studio_run` row.
Everything built in the last two phases attaches to runs:

| Control | Applies to a Studio run | Applies to a direct invocation |
|:---|:---:|:---:|
| `data_class = SENSITIVE`, excluded from analytics and training | ✅ | ❌ |
| Shortened retention via `RetentionSweeper` | ✅ | ❌ |
| Append-only audit row per run | ✅ | ❌ |
| Scope guard (`orazaka.guard.subject`) | ✅ | ❌ |
| One metering path, aggregate settle | ✅ | ❌ — `JobMeteringService` + `EntitlementInterceptor` |

**Concretely: a user can upload a lease and run `orazaka.core.media.vision` on it from the chat
composer, and none of the SENSITIVE controls apply.** No data class, no shortened retention, no
audit row. The document sits in the general job store under the general policy.

And the second metering path is the one the double-billing lived on — two CHAT holds per inference,
found only when billing was first switched on. Collapsing door 1 does not merely tidy the UI; it
**deletes a whole metering path and a whole governance hole**.

### 2.3 `tool` is not a category — it is a different axis

`business` and `lifestyle` answer *for whom*. `tool` answers *what kind of thing this is*. Putting
them in one closed vocabulary mixes two axes, and a shelf that mixes axes reads badly: a user
browsing **Business** expects métier packs, and **Tool** sitting beside it is answering a different
question.

Keep the axis consistent and add the distinction where it belongs:

```sql
ALTER TABLE pack ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'VERTICAL';
-- VERTICAL : a métier pack. Prospection, Validation, Bien-être.
-- TOOLKIT  : platform capabilities surfaced as Studios. No profession, no vertical claim.
CONSTRAINT ck_pack_kind CHECK (kind IN ('VERTICAL','TOOLKIT'))
```

`kind` decides how the marketplace presents it — verticals on category shelves, toolkits in their own
band — and `category` keeps meaning exactly what it means. A `TOOLKIT` pack may have no
`category_key` at all, which is more honest than inventing a shelf for it.

---

## 3. Target architecture

### 3.1 One pack, one Studio per capability

```
orazaka-packs/orazaka-media/
  pack.yaml                     kind: TOOLKIT · pricing: INCLUDED · regulatoryClass: STANDARD
  i18n/{fr,en}.yaml
  studios/
    image-generation/blueprint.json     1 step → orazaka.core.media.image
    video-generation/blueprint.json     1 step → orazaka.core.media.video
    speech-synthesis/blueprint.json     1 step → orazaka.core.media.speech
    image-analysis/blueprint.json       1 step → orazaka.core.media.vision
    audio-analysis/blueprint.json       1 step → orazaka.core.media.audio.analysis
    video-analysis/blueprint.json       1 step → orazaka.core.media.video.analysis
```

Each blueprint is one `CAPABILITY` step, its `inputs` JSON Schema carrying exactly what
`payload_template` carried. The Schema is a gain, not a translation: `payload_template`'s
`${prompt}` / `${size:1024x1024}` grammar cannot express *required*, *enum*, *maxLength* or
*minItems*, so the run form becomes validated server-side where today it is not.

**`compose` is a special case.** It is `orazaka.studio.media.compose`, already pack-shaped, already
only reachable from a blueprint. It needs no Studio of its own — it stays a step other blueprints
use. Giving it a user-facing Studio would expose an assembly primitive that means nothing without a
brand kit and a script.

### 3.2 What `orazaka_capabilities` loses

Three columns become dead the day door 1 closes: `uri_path`, `http_method`, `payload_template` —
the UI-manifest hat. What remains is the dispatch table: `feature_key`, `routing_key`, `handler_key`,
`billable_unit`, `is_enabled`, `icon`, `label`.

Dropping them is the point, not a side effect. It is the same move as `worker_family`: a column that
no longer has a reader is a trap for the next person, and the endpoint invariant cost three outages
precisely because the two halves existed.

> **One consequence to state plainly.** `is_enabled` today toggles what the chat composer shows.
> After the change it toggles whether a capability is dispatchable at all, and a Studio's visibility
> is decided by its pack and its entitlement instead. That is a cleaner split — availability is a
> platform fact, visibility is a catalogue fact — but it is a *change of meaning* on a column, and
> the admin console's feature toggles have to follow it.

### 3.3 What the UI collapses to

| Today | After |
|:---|:---|
| `/chat` with a capability button row driven by the feature registry | `/chat` — conversation only, streaming, no capability buttons |
| `/studios` — installed Studios and the explorer | `/studios` — unchanged, now also holding the media Studios |
| `/packs` — marketplace | `/packs` — unchanged, now with a toolkit band |
| `/dashboard/jobs` — job history | `/studios/runs` becomes the single history; the job view stays for admin |

`useBootstrapFeatures`, `executeFeature`, `CapabilityOptions` and the composer's button row all go.
This is the simplification the proposal wants, and it arrives as a *consequence* rather than as the
goal.

### 3.4 The new failure mode, named

Today the ability to generate an image is a property of the platform. After the change it is a
property of an installed pack — and that introduces a state the current design does not have:
**a plan grants `capability.image`, and the toolkit pack is not installed.**

Three mitigations, and the third is the one I would take:

1. Auto-install the toolkit on account creation. Simple, but "auto-install" is a new concept with its
   own edge cases — what happens on uninstall, on a new toolkit version, on a fresh deployment?
2. Make the toolkit uninstallable-but-always-present. Contradicts what install means.
3. **`kind = TOOLKIT` implies the pack is always installed for any actor whose plan grants its
   entitlements** — installation is derived, not stored. No row, no auto-install job, no divergent
   state. A `VERTICAL` pack is installed by an act; a `TOOLKIT` pack is installed by being entitled.

Option 3 also gives `kind` a second reason to exist, which is how you know it is carving reality at a
real joint rather than adding a label.

---

## 4. What this costs, honestly

| Cost | Assessment |
|:---|:---|
| Saga overhead on every image generation | **Measured — [M0](measurements/M0-run-overhead.md): p95 30.5 ms, p50 14.2 ms.** 0.05 % of a median MLX image (67 s). The estimate this row used to carry — *"one `studio_run` + one `studio_run_step` insert plus an advance cycle"* — was wrong: a single-step run executes **37 SQL statements**, 22 before dispatch and 15 after the outcome. They are short, so the conclusion holds and no fast path is needed; the arithmetic was off by an order of magnitude and is corrected here rather than left to be repeated. |
| Six blueprints + one pack to author | Small, and it exercises the bundle installer on the platform's own capabilities — a better test than any of the three content packs. |
| `JobMeteringService` + `EntitlementInterceptor` hold path deleted | A *gain*. One metering path instead of two. |
| The admin console's feature toggles change meaning | Real work, §3.2. |
| Direct `/api/v1/media/**` endpoints removed | They are also the CLI's and the mobile client's surface. Both must move to the Studio run API, or keep a thin compatibility shim — and a shim reopens the bypass, so: move them. |
| `orazaka_capabilities` loses three columns | A gain. |

**The one thing that would make me say no** is the latency number. Everything else is favourable.

---

## 5. Sequence

Nothing is deployed, so there is no migration — but the order still matters, because each step
should leave the system working.

| Phase | Scope | Done when |
|:---|:---|:---|
| **M0 — measure** ✅ | Isolate the overhead against a **neutralised** executor, ≥ 30 samples, and take the model out of the comparison. *(The original spec here — "same prompt, same model, ten runs each" — could not have worked: MLX run-to-run variance is hundreds of ms, so a sub-second overhead sits inside the noise and the comparison returns "no difference" whether the overhead is 20 ms or 800 ms. Corrected by [`measure_run_overhead.md`](../.agent/workflows/measure_run_overhead.md) §4.)* | **Done — [M0: PROCEED](measurements/M0-run-overhead.md).** p95 30.5 ms on the worse of two 50-sample runs, 8× inside the 250 ms threshold declared before measuring. See §5.1 for what else it found — the headline number is not the important part of that report. |
| **M0.5 — the mechanical sweep** | `spotless:apply` repo-wide in a commit that does nothing else, then bind `spotless:check` to a phase. Then inventory what else is *declared and bound to nothing*. | The build fails on unformatted source, and the inventory exists. Blocks nothing; do it first because it is cheap and it stops drift today. |
| **M1 — `kind`** ✅ | `pack.kind` with its CHECK, `TOOLKIT` derived-installation in `StudioAccessService`, marketplace band | **Done — ADR-061.** An entitled actor runs a TOOLKIT with zero `studio_installation` rows; a version published mid-run is not the version the run executes (seen failing when the saga was planted to re-resolve). It also answered a question this row did not ask: `studio_run.installation_id` was `NOT NULL` and the only run route took an installation id — now nullable, guarded by `ck_run_uninstalled_not_regulated`, with `POST /studios/{studioKey}/runs`. |
| **M1.5 — close the inert controls** ✅ | The orchestration switch, the rule against binding defeat, rules asserting over nothing, `IMAGE_STEP`'s two causes and the quantity invariant. | **Done — ADR-062.** [CFG-001], [GOV-006] and [BILL-002] each seen failing on planted violations. Also found: a chat turn carrying inline media skips every pipeline gate, and composition bills intended duration (9.00 s billed for a 2.02 s file). Both recorded, neither fixed. |
| **M1.6 — close the media-turn bypass** ✅ | #21: every turn through the pipeline, media declared to it, guards that refuse what they cannot read. #20: the divergence contract extended to video. The inert-control principle written down. | **Done — ADR-063.** Four controls, five tests, each seen failing with the bypass restored; the branch was vestigial, the refiner and router were the defect; media chat turns had never been billed (D6); composition measured on the file. |
| **M1.7 — four trust boundaries** ✅ | #23 caller keys in trusted maps · #24 tests that never ran · #25 the billing failure posture · #26 logs as a data-class channel. The last remediation run before M2. | **Done — ADR-064.** Namespaces instead of a line order; collected-equals-defined for Python; `failsClosed()` declared and the gate open-and-accounted; [LOG-001] on the operand stack. Two live findings surfaced as decisions (#27, #28). |
| **M1.8 — the disclosure surface** ✅ | #27 governed data surviving retention in the job plane · #28 asset scope by id, not path · the matched term in guard logs. The last remediation run, with a rule: anything new goes to M2's report. | **Done — ADR-065.** `data_class` on `JobCommand`, refused when absent; a protected job's files then row purged at terminal, row and files asserted apart; the run trail records outcomes, never what was matched; absolute paths refused at the worker; guards name the rule. Twelve findings for M2's report (audit #31–#42), two of them 🔴. |
| **M2 — the pack** ✅ | `orazaka-media` bundle, six single-step blueprints, JSON Schemas carrying what `payload_template` carried | **Done — ADR-066.** The seed files have a test that loads them (#31); the job plane reads no payload path (#32); the four unbilled media capabilities measure and declare a unit (#22), with one flagged placeholder rate; the marketplace band is keyed on kind AND entitlement; six Studios run and settle exactly once, asserted; the fps forcing is reversed and audio has a divergence contract; `realestate-reels` stops paying for a b-roll it never assembles (#35). Door 1 untouched — M3 owns it. |
| **M2.5 — harden the run path** ✅ | §5.2. The outbox on step dispatch, lanes, a per-lane step ceiling. | **Done — ADR-067.** Dispatch is durable in the saga's transaction (re-measured: saga p95 19.2 ms, append p95 1.21 ms, relay poll at par with door 1); two lanes with two consumer pools keyed on a declared `latency_class` and [LANE-001] to keep it honest; a ceiling per lane, because queue wait counts against a deadline stamped at dispatch; the dedup claim released in five consumers (#30); free inference recorded as an `UnmeteredTurn` (#46). #33 and #34 are adjacent, not closed. |
| **M3 — close door 1** ✅ | §2, §3, §5. Delete `MediaGenerationController`, `MediaAnalysisController`'s analyze paths, `POST /api/v1/jobs`, `useBootstrapFeatures`, `executeFeature`; the composer keeps its buttons and they launch runs; every client follows. | **Done — ADR-068.** Seven endpoints deleted and the two services behind them; the composer's row served from Studios by a structural predicate (one step, one required input) with what that input holds *declared*; the shipped packs installed by bootstrap (#45) — which found three catalogued bundles that could not install into an empty catalogue at all; the controls asserted one at a time, and a planted controller seen reddening the surface test that the integration test could not. |
| **M4 — the registry declares what it runs** ✅ | §5. The contract FIRST (inputs and outputs, declared), then the five UI-manifest columns dropped as a consequence; `AGENTS.md` §6 made true; `#44` renamed; `#43` answered by a worker contract. | **Done — ADR-069.** The e2e gate ran for the first time on the run path and found the studio service was not starting at all (two constructors, no `@Autowired`) — [CFG-001] now covers the container as well as the binder. A capability declares `input_schema` and `output_schema`, so `BlueprintFitnessTest` reads data on both sides instead of a transcription — and caught four wrong declarations on its first run. The five columns are gone, with the three consumers M3 said did not exist: a rule, a 500 on `/api/v1/features`, and the availability filter, which now reads the one path this service actually serves. |
| **M5 — governance** ✅ | A rule that reddens if any capability becomes reachable other than through a run — the bypass must not reopen | **Done — ADR-070.** `[DOOR-001]`, in seven governance suites. Sinks **derived** from what hands the jobs exchange to something, not listed — the derivation was wrong four times, ending with a first verdict that starting a run was door 1. Subjects are Spring's web annotations, never a package, so a controller outside `adapter.rest` is still found. Against the repository as it stands it finds **nothing**, which is the result; the plant went into `orazaka-job-service`, which `DoorOneClosedTest` never covered, and reddened. Static by construction — §8's reason is the harness's own three 🔴. |

**M3 is the irreversible one** and the one that delivers the value: until door 1 is closed, the
bypass exists and both metering paths exist. M4 and M5 are what stop it growing back.

`POST /api/v1/media/upload` stays — uploading an asset is not a capability invocation, and the
authenticated asset endpoint from wave 1 is the right owner of it. So does `GET /api/v1/media/search`,
which reads an index. What left are the six `generation`/`analyze` submissions and `POST /api/v1/jobs`.

### 5.1 What M0 actually found

The latency number decided the question it was asked and is not interesting again. Three other
findings from that run are, and two of them changed this plan.

**The measurement is not an upper bound, and the argument that rescues it is a different one.** The
report claimed that measuring door 2's total bounds `(A₂+B₂) − (A₁+B₁)` from above, because door 1
does strictly positive work. That argument does not survive the report's own §7.4: the stub removed
**two billing HTTP round trips** from door 2, so what was measured is `door2_stubbed < door2_real`
and it majors nothing. What holds instead is that door 1 also takes a hold and also settles, so the
billing calls are a **term common to both doors that cancels in the difference**. Weaker, sufficient
at three orders of magnitude of margin, and true — which the first version was only by accident.

**Door 2 is not faster. It is less durable.** M0 found that door 1 publishes through
`OutboxStore.append` — transactional outbox, AGENTS.md §6 cited in its own javadoc — and waits on
`OutboxRelay`'s 500 ms `fixedDelay`, while `AmqpStepExecutionAdapter` calls
`rabbitTemplate.convertAndSend` directly. The natural reading is that the saga gets a 500 ms refund.
It does not. `RunSagaService.advance` is `@Transactional`, so the send happens inside a transaction
that can still roll back, and the hold lives in **another service** and is therefore not rolled back
with it: hold taken, step row gone, worker burning the model for a run that no longer exists,
aggregate settle never called. Same family as the 480-reserved / 5-debited defect. It has never bitten
for the usual reason — one instance, happy path — which is also why the missing
`FOR UPDATE SKIP LOCKED` never bit. **M3 is what makes it load-bearing**, and once the outbox is
added the 500 ms comes back and the two doors are at par. The decision does not move; the margin was
real.

**The document's cost estimate was wrong by 12×.** §4 above asserted "one `studio_run` + one
`studio_run_step` insert plus an advance cycle". The counted figure is **37 statements** — 22 before
dispatch, 15 after the result — the estimate having omitted the hold, both outbox rows, the status
transition, the aggregate settle and the `data_class` read. Roughly 9 of the 37 are redundant
(`advance()` computes step states twice, the blueprint is read three times, two `SELECT`s repeat).
**They are deliberately not being removed.** A path three orders of magnitude inside its budget has
no performance problem, and the only reason to re-read an immutable blueprint would be that it could
change between reads. Recorded so it is not re-discovered a fourth time; not repaired.

### 5.2 M2.5 — why the run path needs hardening before it carries everything

Three findings, one cause. The run path is today a **secondary** path carrying development traffic,
and everything tolerable at that status becomes load-bearing the day M3 makes it the only path.

**The missing outbox** — §5.1 above. Step dispatch must publish through the transactional outbox like
every other producer, or the run path is the one place in Sovkora where a rollback can leave a job
running against nothing.

**Head-of-line blocking, which is already in the repository.** `orazaka-job-service/worker.yaml`
declares `bindings: ["job.media.*", "job.text.*"]` with `concurrency: 2`. Two image generations
occupy both slots and every text step waits behind them — measured on this machine at
**p50 67 s for image against 4.9 s for vision**. The media worker is correct by contrast
(`concurrency: 1` over `job.video.*` + `job.compose.*`, two heavy loads that must serialise because
the Neural Engine serialises anyway).

> **Lanes, not priorities.** AMQP `x-max-priority` reorders *waiting* messages at fetch and preempts
> nothing. With `prefetch=1` and a 67-second job in flight, no priority frees the consumer — priority
> inside one queue solves the blocking only in the case where it does not occur. The mechanism is
> **separate queues with separate consumers**, which `worker.yaml` has supported since phase C
> (ADR-038): a lane is a binding and a queue, not a subsystem.
>
> And it must be said plainly that lanes do not create a second GPU. They move the wait onto whoever
> asked for the long work, which is the right trade — but it is a **fairness property between
> tenants**, and with one user it has no observable effect. That is an argument about *when*, not
> *whether*.

**The single step ceiling, which is the real reason to do this.** `RunSweeper` reaps on
`s.started_at < now() - make_interval(secs => ?)` against one global `run.step-timeout-seconds`, and
`started_at` is stamped when the step row is inserted — **at dispatch, before any worker takes the
message**. Queue wait therefore counts against the deadline. One number must then be large enough not
to reap a 67-second image that queued, and small enough to catch a 200 ms text step that hung. Today
that contradiction is invisible because there is no contention. After M3 the queue genuinely fills,
and a legitimately-queued image job gets reaped: run `FAILED`, hold released, worker still burning the
Neural Engine for a run that is already dead. **That is a correctness consequence, not a latency one,
and a per-lane ceiling is what dissolves it.**

Three constraints on the lane design, all of them the project's existing principles applied again:

1. **Declared, not inferred.** A `latency_class` column on the capability row — `INTERACTIVE | BATCH`,
   naming the user's relationship to the wait rather than a rank, so nobody argues for a promotion.
   Populated from the durations already recorded in `orazaka_jobs`, not guessed. **Two classes, never
   three**: a third invites arguing about the middle. And a governance rule must redden when a
   capability's declared class contradicts the queue its routing key feeds, or the column is
   documentation.
2. **A pack does not choose its own lane freely**, or everything is `INTERACTIVE`. Same principle as
   *a blueprint is untrusted input even from an admin*.
3. **No scheduler.** No fair-share, no ageing, no weighted round-robin. Two queues, two consumer
   pools. Anything more is a scheduler, and a scheduler is a component nobody asked for.

---

## 6. Verdict

**Do it.** The proposal is right that the concept should be unified, and the UI simplification is
real. But the reason to do it is that **the direct path bypasses every control built in the last two
phases** — no data class, no retention, no audit, no scope guard, and a second metering path that
has already produced a billing defect once. Unification closes a hole; the tidier UI is the
byproduct.

Three amendments to the proposal:

1. The line is **deferred vs synchronous**, not media vs studio. Interactive chat stays direct —
   AGENTS.md §6, and it is not negotiable.
2. `tool` is not a category. It is `kind = TOOLKIT`, orthogonal to `category`, and it earns its
   keep twice by also deriving installation.
3. Gate the whole thing on **M0**: measure the saga overhead on a single-step run before authoring
   anything. It is the only number that can make this a bad idea.

---

## Related documentation

- [Pack extensibility](PACK_EXTENSIBILITY_ARCHITECTURE.md) · [Pack catalogue](PACK_CATALOGUE_ARCHITECTURE.md)
- [Studio architecture](STUDIO_ARCHITECTURE.md) · ADR-034
- [Governance contract](../AGENTS.md) — §6 is what fixes the synchronous/deferred line
- [Worker protocol](WORKER_PROTOCOL.md)
