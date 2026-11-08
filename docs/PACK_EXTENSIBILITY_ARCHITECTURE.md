---
title: "Sovkora — Pack extensibility: workflows, workers, open-core"
description: "Target architecture turning a Pack into a distributable unit: the pack manifest, the four extension seams that must be opened, the AMQP worker SPI, feature independence, and the open-source / managed-cloud split."
category: Architecture
order: 9
---

# Sovkora — Pack extensibility: workflows, workers, open-core

> **What this document answers.** Packs exist and are categorised (ADR-036). This is the next
> question: how does a Pack bring **its own workflows and its own workers**, how do its features stay
> **independent**, and how does Sovkora stay a clean open-source core while you run a managed cloud
> with packs that are not in the open repository?
>
> **The finding.** The current implementation is good and the model is right — but **three hardcoded
> seams make the promise "a new pack is data" false the moment a pack needs anything new.** §2 names
> them with evidence. Opening them is the whole of phase A, and it is small.
>
> Prerequisite reading: [`PACK_CATALOGUE_ARCHITECTURE.md`](PACK_CATALOGUE_ARCHITECTURE.md),
> [`STUDIO_ARCHITECTURE.md`](STUDIO_ARCHITECTURE.md), ADR-034,
> ADR-036.

---

## 1. What was delivered — verified

Phases 1 and 2 of the catalogue plan are in the code, and they were done properly.

| Delivered | Evidence |
|:---|:---|
| Catalogue moved to the studio context | `pack`, `pack_i18n`, `pack_category`, `pack_category_i18n`, `pack_studio` in `80-studio.sql` |
| `billing_pack` narrowed to a price tag | `70-billing.sql` — `label`/`category`/`profession` gone |
| Localised read path | `PackCatalogController`, `PackCatalogService`, `JdbcPackRepositoryAdapter`, `PackRepository` port |
| Tier-1 contract | `Pack`, `PackCategory`, `PackSummary`, `PackStatus`, `RegulatoryClass`, `PackCatalogClient` |
| `regulatory_class` placed, unread | `pack` DDL + `CHECK`, with the comment saying why it is there and empty |
| Cross-context invariant enforced | `PackCoherenceRules` [PACK-001] over the seed files |
| ADR written | `ADR-036` |

Two corrections were pushed back into the design rather than absorbed silently — `pack` has no base
label so the read path falls back to the lowest-sorting translation, and `pack_studio` must be
declared *after* `studio` because it references it. Both are right, and the fact that they came back
as document amendments rather than silent deviations is the process working.

**So the catalogue is done.** What follows is a different problem.

---

## 2. The wall: three hardcoded seams

The architecture promises *"a new pack is a row, never a deploy"*. That holds only while a pack reuses
capabilities that already exist. The moment a pack needs a new one — and the Validation pack does,
because nothing in Sovkora reads a PDF today — the promise breaks in three places.

### 2.1 ✅ CLOSED (phase A) — Step routing was a substring heuristic

`orazaka-studio-service/…/AmqpStepExecutionAdapter.java`:

```java
private static String routingKeyFor(String featureKey) {
  String key = featureKey.toLowerCase(Locale.ROOT);
  if (key.contains("compose")) return COMPOSE_ROUTING_KEY;
  if (key.contains("video"))   return VIDEO_ROUTING_KEY;
  if (key.contains("image") || key.contains("media")) return MEDIA_ROUTING_KEY;
  return TEXT_ROUTING_KEY;                       // ← everything else lands here
}
```

A new capability `orazaka.doc.validate` matches none of the branches and is **silently routed to
`job.text.process`**, where a worker that cannot execute it will fail it — or worse, an LLM will
cheerfully answer something. This is not a compile error and not a test failure; it is a wrong answer
in production. A substring match on a business identifier is a routing table pretending to be a
heuristic.

> **Closed by phase A** (ADR-037). `orazaka_capabilities` carries `routing_key`, `worker_family` and
> `billable_unit`; `routingKeyFor` and its four constants are deleted; producers resolve through the
> Tier-1 `CapabilityRoutingClient` with **no default branch**, and an unresolved capability fails at
> publish and at dispatch. The finding is kept rather than deleted — a closed finding is evidence.
>
> Two *other* substring chains on `featureKey` were found during the work and deliberately left
> alone, because correcting behaviour inside a refactor makes it unreviewable:
> `JobQueuePublisherService.getDefaultRoutingKey` (the conversation service's own routing default,
> which never had a `compose` branch — so the two copies had already diverged) and
> `JobMeteringService.capabilityOf` (feature key → billable capability). Both are phase C work.

### 2.2 ✅ CLOSED (phase C) — The executor SPI was sealed

`orazaka-job-service/…/JobExecutionStrategy.java`:

```java
sealed interface JobExecutionStrategy
    permits VideoAnalysisStrategy, VisionAnalysisStrategy, AudioAnalysisStrategy,
            ImageGenerationStrategy, SpeechSynthesisStrategy, ChatGenerationStrategy { … }
```

Package-private **and** `sealed`. Adding an executor requires editing a file in the core repository
and recompiling the core. A cloud-only pack cannot add one. A third party cannot add one. An
open-core model is structurally impossible against a sealed interface.

The irony is that Sovkora already solved this exact problem once: `orazaka-interceptors` ships an
`AutoConfiguration.imports` SPI with seven registrations, and AGENTS.md §2 makes it a rule
(*"All implementations live in `orazaka-interceptors/` (AutoConfiguration SPI) [ERR-122]"*). The job
plane never got the same treatment.

> **Closed by phase C** (ADR-038). The sealed interface is gone; `JobExecutor` is a Tier-1 port in
> `orazaka-jobs-api`, discovered through `AutoConfiguration.imports` exactly as the interceptors are.
> An out-of-tree jar contributes an executor with no edit to this repository — verified by dropping
> one on the classpath and finding it registered. The port carries no engine type and does no file
> I/O, so an executor can be written outside a repository that ships the engine.
>
> The finding is kept rather than deleted: a closed finding is evidence.

### 2.3 ✅ CLOSED (phase C) — Workers hardcoded their own bindings and knew pack content

`orazaka-worker-media/app/consumer.py` declares `VIDEO_BINDING = "job.video.*"`,
`COMPOSE_BINDING = "job.compose.*"`, and branches on
`job.get("featureKey") == COMPOSE_FEATURE_KEY`. A worker that names a specific capability is a worker
coupled to a pack. Add a fourth media capability and you edit Python.

> **The capability-name coupling was closed by phase B** (ADR-037): `COMPOSE_FEATURE_KEY` is gone
> and the worker decides from the routing key the broker delivered under — the dispatcher's
> decision, taken from the capability's row.
>
> **The bindings are closed by phase C** (ADR-038): they live in `worker.yaml`, are read at boot,
> and are used both to bind the queues and to register with the platform. Adding a binding is a
> config change. The worker also registers and heartbeats — without either being a startup
> dependency, so an unreachable platform never stops it consuming.

### 2.4 What these three cost you, concretely

| Goal you stated | Blocked by |
|:---|:---|
| "des workers adaptés au besoin" | §2.2 — you cannot add a worker without forking the core |
| "flexibilité des workflows" | §2.1 — a new capability misroutes silently |
| "ne pas polluer Sovkora" | §2.3 — pack-specific keys already live in core worker code |
| "la version cloud managée par moi" | §2.2 + §2.3 — cloud packs would be core patches, i.e. a fork |

None of this is expensive to fix. It is roughly one week, and it must happen **before** the three
content packs, because each pack written against the current seams adds another special case to
unwind.

---

## 3. Target architecture — a Pack is a distributable unit

### 3.1 Three tiers of pack, by what they need

Not every pack needs the same machinery, and pretending they do is how the model gets heavy.

| Tier | Needs | Ships as | Your packs |
|:---|:---|:---|:---|
| **D — Data pack** | nothing new; blueprints over existing capabilities | SQL/JSON bundle | **Prospection**, **Wellbeing** |
| **C — Capability pack** | a new capability executed by an *existing* worker family | bundle + capability rows + (optionally) a Java executor jar | — |
| **W — Worker pack** | its own worker process | bundle + an OCI image + queue bindings | **Validation** (PDF/OCR/authenticity) |

**The tier is a property of the pack, not a new concept in the code.** A Tier-D pack installs on an
untouched Sovkora. A Tier-W pack declares a worker and the platform refuses to publish it until that
worker is registered and healthy. Same manifest, different sections filled.

### 3.2 The Pack Manifest — the unit of distribution

Today a pack is scattered: rows in `80-studio.sql`, rows in `70-billing.sql`, prompt files in
`orazaka-business/resources`. That is fine for packs written by you, in this repo, forever. It does
not survive a cloud-only pack or a partner pack.

Make the pack a **directory with a manifest**:

```
orazaka-packs/document-validation/
  pack.yaml                     ← the manifest, below
  i18n/{fr,en}.yaml             ← labels, taglines, descriptions
  studios/
    lease-validation/blueprint.json
    lease-validation/prompts/*.md
    vehicle-rental-check/blueprint.json
  capabilities/*.yaml           ← new capability declarations (Tier C/W)
  rulesets/*.yaml               ← pack-specific reference data
  worker/                       ← Tier W only: Dockerfile + source
  README.md · LICENSE
```

```yaml
# pack.yaml
apiVersion: orazaka.dev/v1
key: document-validation
category: business
regulatoryClass: SENSITIVE
tier: WORKER
distribution: CLOUD          # OSS | CLOUD | PARTNER
version: 1.0.0
requires:
  orazakaVersion: ">=1.4.0"
  capabilities:
    - key: orazaka.doc.extract
      routingKey: job.doc.extract
      handlerKey: doc.extract
      unit: DOCUMENT_PAGE
    - key: orazaka.doc.validate
      routingKey: job.doc.validate
      handlerKey: doc.validate
      unit: DOCUMENT_PAGE
  workers:
    - name: orazaka-worker-docs
      image: ghcr.io/orazaka/worker-docs:1.0.0
      bindings: [job.doc.*]
      concurrency: 2
      resources: { memory: 2Gi, cpu: "1" }
  connectors: []
studios:
  - key: lease-validation
    entitlementKey: studio.lease-validation
  - key: vehicle-rental-check
    entitlementKey: studio.vehicle-rental-check
pricing:
  mode: PAID
  priceCents: 3900
  includedCredits: 3000
```

The manifest is what makes every later capability cheap: install-time validation, dependency
checking, the CLI (`orazaka pack validate|install|publish`), the registry, per-pack worker
autoscaling, and the OSS/cloud split all read the same file. **Design it now even if the registry is
a year away** — retrofitting a manifest onto packs that already shipped is the expensive path.

> **✅ CLOSED (phase D)** — implemented in ADR-039, with two departures from the sketch above
> worth recording. **`catalog` is optional**: two of the three Studios shipping today belong to no
> pack, and a format that could not express the existing catalogue would have failed its first test.
> And the install is a **compensated sequence across three databases**, not the single transaction
> the S4 row below implies — `orazaka_capabilities`, `billing_pack` and the studio catalogue are
> owned by three services, and SEAM-001 forbids the cross-context foreign keys that would make them
> one schema. See ADR-039 §2 for why two-phase commit was rejected rather than adopted.

### 3.3 The four seams to open

| # | Seam | Change | Unlocks |
|:--:|:---|:---|:---|
| **S1** | Capability → routing | Add `routing_key`, `worker_family`, `billable_unit` to `orazaka_capabilities`. `routingKeyFor` reads the row via a Tier-1 `CapabilityRoutingClient`, with **no fallback** — an unknown capability fails loudly at publish and at dispatch | A new capability is a row |
| **S2** | Executor SPI | Un-seal `JobExecutionStrategy` → public `JobExecutor` interface in a Tier-1 contract (`orazaka-jobs-api`), discovered by `AutoConfiguration.imports` exactly as interceptors are [ERR-122] | A pack can ship a Java executor without touching core |
| **S3** | Worker registration | A worker declares its bindings in a `worker.yaml` read at boot; the platform holds a `worker_registry` table with heartbeat. Nothing in core names a pack's capability | A worker in any language; health-gated publish |
| **S4** | Pack bundle loader | `PackInstaller` reads a manifest directory and applies it transactionally: categories, packs, studios, blueprints, capabilities, entitlements, i18n | A pack ships outside this repo |

**S1 is the keystone.** Do it first and alone: it is a column, a read, and the deletion of a
`contains()` chain, and it converts a silent-misroute failure mode into a loud one.

### 3.4 The worker SPI is the AMQP contract, not a Java interface

This is the design decision that makes "workers adaptés au besoin" real, and it is already true —
it just is not written down.

`orazaka-worker-media` is **Python**. It participates fully: it consumes `job.video.*` on
`orazaka.jobs` and answers `job.{jobId}.done|error` on `orazaka.events`. It implements no Java
interface and links no Sovkora library.

**Therefore: a worker is anything that honours the message contract.** Publish that contract as a
versioned specification (`docs/WORKER_PROTOCOL.md`) — envelope, correlation, progress events,
consumption reporting for billing, DLQ and idempotency expectations — and a pack can bring a worker
in Python, Rust, Go or a shell script. S2 (the Java SPI) then becomes a *convenience* for
in-process executors, not the extension mechanism. That inversion is the point: **the broker is the
plugin boundary**, which is also what keeps a badly-behaved third-party worker from taking the
platform down with it.

### 3.5 Feature independence

Your requirement: *"les features de chaque pack doivent être indépendantes"*. Four properties, three
already hold:

| Property | State |
|:---|:---|
| Independent unlock — each Studio has its own `entitlement_key`; a pack grants N | ✅ already |
| Independent install — a user installs the Studios they want, not the whole pack | ✅ already |
| Independent failure — one run's failure touches no other run | ✅ already |
| **Independent availability** — a Studio whose capability has no healthy worker must show as unavailable, not fail at run time | ❌ **missing** |

The fourth needs S1 + S3: `StudioAccessService` already computes a lock reason from entitlements; it
gains a second input — *is every capability this blueprint declares currently served?* A user must
never pay credits for a run that was doomed before it started, and the person on call must not learn
about a dead worker from a support ticket.

---

## 4. Open-source core and managed cloud

### 4.1 The split

Sovkora is Apache-2.0. Apache-2.0 permits anyone to run and resell it, so the moat is **not** the
licence — it is the managed service and the packs you keep. Which means the split must be clean
enough that "cloud pack" never means "core patch".

| Layer | Home | Licence |
|:---|:---|:---|
| Engine: studio-service, blueprint DSL, job plane, billing, identity, edge, interceptors | `orazaka` (this repo) | Apache-2.0 |
| **Pack SPI**: manifest schema, `orazaka-jobs-api`, worker protocol, `PackInstaller`, CLI | `orazaka` | Apache-2.0 |
| **Reference packs** — one Tier-D, one Tier-W | `orazaka/orazaka-packs/` | Apache-2.0 |
| Commercial packs (validation, wellbeing, verticals) | `orazaka-packs-cloud` (private) | proprietary |
| Hosted registry, managed workers, control plane, SLA | private | proprietary |

**Ship at least one Tier-W reference pack in the open.** An extension point with no working example
of its hardest case is not an extension point — nobody will believe a worker pack is possible, and
you will discover the SPI's gaps only when a paying pack hits them.

**Recommendation: two repositories, one manifest format.** Not a `distribution:` flag inside a single
public repo — a cloud-only pack sitting in a public repository is readable by everyone, which defeats
the purpose. The `distribution` field in the manifest stays, but it drives *catalogue visibility per
deployment*, not secrecy.

### 4.2 Not polluting the core — four rules, each enforced

The concern is right, and it is already violated: `COMPOSE_ROUTING_KEY = "job.compose.assemble"` and
the Python worker's `COMPOSE_FEATURE_KEY` comparison are pack knowledge inside core code.

| Rule | Enforcement |
|:---|:---|
| **P1** — no pack, studio or capability key as a literal in core code | ✅ **[PACK-002]** `PackPurityRules`, run by every `<Module>GovernanceTest`. Scans `engine/**`, `services/**`, `workers/**` for `orazaka\.(doc\|studio)\.` and the known pack keys; `orazaka-packs/**` and `infra/initdb/**` are outside the scanned roots |
| **P2** — no `if (packKey == …)` / `switch (featureKey)` in core | ✅ **[PACK-003]** same rule, extended to conditionals — **including through a constant declared in the same file**, which is the shape both violations actually took |
| **P3** — core seeds contain reference data only; pack content ships with the pack | `PackCoherenceRules` extended: a `studio` row in `80-studio.sql` whose `pack_key` names a non-reference pack fails the build |
| **P4** — a worker declares its bindings; it never names a capability | `worker.yaml` schema check in the worker's own CI-equivalent local gate |

P1 and P2 failed on the tree when they were written. That was the point — they are the regression
tests for §2. Both violations named above are closed; the rules now hold, and two refinements were
needed to make them hold *honestly* rather than by allowlist (ADR-037 §4): `orazaka.core.*` is the
engine's own namespace and is not pack knowledge, and a capability key never ends in a separator, so
the `orazaka.studio.brand.` preference prefix (ADR-034 §9.1) is not a capability.

### 4.3 What the cloud deployment adds

The cloud is *the same artifact plus a control plane*, never a fork:

- **Pack registry** — signed manifests, versioned, per-tenant entitlement to install.
- **Per-pack worker scaling** — the manifest already declares `bindings` and `concurrency`, so queue
  depth per `job.<family>.*` drives autoscaling with no extra config.
- **Tenant isolation** — `actor_id` is opaque today, which is what makes an org-level tenant a later
  addition rather than a migration. Confirm it stays opaque in every new table.
- **Deployment profile** — `orazaka.packs.sources` lists local directories (OSS) and/or the registry
  (cloud). One property decides which world a deployment lives in.

---

## 5. The three packs, re-read through the tiers

### 5.1 Prospection — Tier D — ship first

Blueprints over existing capabilities plus `KNOWLEDGE` and `CONNECTOR` steps. `outbound-prospection`
already exists and is published. **Zero new capability, zero worker.** It is the acceptance fixture
for the pack bundle loader (S4): if a Tier-D pack cannot install from a manifest, nothing else will.

### 5.2 Validation — Tier W — the design driver

*"Valider des documents que le user a sélectionnés — authenticité, baux d'appartement, location de
voiture."* This is the pack that forces every seam open, which is why it should be **designed** early
and **built** after the seams.

Two distinct things hide under "validation", and conflating them is the trap:

- **Conformity** — does this lease contain the mandatory clauses, are the dates coherent, does the
  deposit respect the legal ceiling? Deterministic rules over extracted fields. Auditable. This is
  the product.
- **Authenticity** — is this document a forgery? Metadata analysis, font/rendering inconsistencies,
  tampering signals. **Probabilistic, adversarial, and legally loaded.** A false "authentic" verdict
  on a fraudulent lease is a liability; a false "forged" verdict on an honest tenant is worse.

**Recommendation**: ship conformity first, and express authenticity as *signals with confidence*,
never a verdict — "3 indicators of possible modification: PDF producer inconsistent with the issuer,
a text layer added after creation" and let the human decide. Never render "AUTHENTIC ✓".

Structure: a new worker (`orazaka-worker-docs`) doing extraction (PDF text layer, OCR fallback,
metadata) plus deterministic rule evaluation; the model used only for judgement calls, always with
the quoted evidence span. Findings are `(rule_id, severity, verdict, evidence_span, explanation)` —
never prose.

Ruleset per document type (`lease-qc`, `lease-fr`, `vehicle-rental`), versioned like a blueprint,
because rental law changes and a validation run must be reproducible against the rules in force at
the time. Jurisdiction is a first-class input, not a prompt hint.

### 5.3 Wellbeing — Tier D + `REGULATED` — ship last

Architecturally the simplest: chat, structured prompts, retrieval. **Regulatorily the heaviest** —
consent gate, non-bypassable safety interceptor, sensitive data class, shortened retention, audit
trail, publish gate (`PACK_CATALOGUE_ARCHITECTURE.md` §6). Gated on encryption at rest
(audit finding #13), which for this pack is a blocker rather than a hardening item.

Naming stands: **Bien-être**, never *thérapie*. The pack a user brings *to* their therapist.

---

## 6. Plan

Each phase ends green (build + every `GovernanceTest`) and is an independent checkpoint.

| Phase | Scope | Done when |
|:---|:---|:---|
| ✅ **A — S1: routing as data** | `routing_key` · `worker_family` · `billable_unit` on `orazaka_capabilities`; Tier-1 `CapabilityRoutingClient`; `routingKeyFor` deleted; publish-time validation that every blueprint capability resolves | **Done** (ADR-037). Also absorbed audit #17: `JobCommand` moved into the new Tier-1 `orazaka-jobs-api` |
| ✅ **B — P1/P2 governance** | The anti-pollution rules, applied to the existing violations (`COMPOSE_ROUTING_KEY`, the worker's `COMPOSE_FEATURE_KEY`) | **Done** (ADR-037). A pack key literal, or a branch on one, fails the build — in Java and in Python |
| ✅ **C — S2/S3: worker SPI** | `orazaka-jobs-api` (Tier-1, holds `JobCommand` too — audit #17); `JobExecutionStrategy` un-sealed → `JobExecutor` via `AutoConfiguration.imports`; `worker.yaml` + `worker_registry` + heartbeat; `docs/WORKER_PROTOCOL.md` | **Done** (ADR-038). Also closed the four phase-A/B carry-overs and added the [EXEC-001]/[EXEC-002] coherence rules |
| ✅ **D — S4: pack bundle** | `pack.yaml` JSON Schema; `PackInstallerService` (compensated across three contexts); `orazaka pack list\|validate\|install\|publish`; **the three existing Studios migrated** to bundles | **Done** (ADR-039). `orazaka pack install --all ./orazaka-packs` fills an empty catalogue from a purged DB and its Studios run |
| **E — Prospection pack** | Tier D, authored as a bundle | It installs through D, and its Studios run |
| **F — Availability** | Capability availability feeds `StudioAccessService`; a Studio with no healthy worker shows unavailable, never charges | Stop `orazaka-worker-media` ⇒ the media Studios grey out; no run is submitted |
| **G — Open-core split** | `orazaka-packs/` layout, licences, `orazaka.packs.sources`, one Tier-D + one Tier-W reference pack, `CONTRIBUTING-PACKS.md` | A pack from a directory outside this repo installs and runs |
| **H — Validation pack** | Tier W: `orazaka-worker-docs`, extraction + rulesets, `RULESET` step kind, conformity findings, authenticity as signals | A lease is validated; every finding cites a rule and an evidence span |
| **I — `regulatory_class`** | The seven controls (`PACK_CATALOGUE_ARCHITECTURE.md` §6.2) | A `REGULATED` pack cannot publish with safety interceptors inactive |
| **J — Wellbeing pack** | Tier D, `REGULATED` | Adversarial safety suite passes; encryption at rest on for `SENSITIVE` |

**The order is load-bearing.** A → B → C → D are the seams; E validates them with the cheapest pack;
F closes the availability gap; G makes the split real; H, I, J are content. Authoring any content pack
before D means writing it twice.

**Fastest path to something demonstrable**: A + B + D + E — one week or so, and it ends with a real
pack installing from a manifest on the existing engine.

### 6.1 ✅ CLOSED — Studio runs are effectively unbilled

**Status:** closed 2026-08-31 by ADR-041 · **Raised:** 2026-08-31
(phase D preliminaries) · **Was blocking:** phase E.

**Measured outcome: a `realestate-reels` run reserved 480 credits and was debited 5. It now reserves
480 and is debited 480** — `b-roll` 5 output-seconds × 90 = 450, `assemble` 15 output-seconds × 2.0 =
30. What follows is the gap as it was found; the resolution of each item is marked inline.

A reel's most expensive step is free, and so is most of the rest of the run. Two defects compound,
found from opposite directions and recorded together because either one alone would hide the other.

**1 — `compose` has no price.** `orazaka.studio.media.compose` carries `billable_unit NULL`
(`30-jobs-config.sql`) and `credit_pricebook` has no row that prices an ffmpeg/MLX assembly. The
hygiene pass corrected its `billable_capability` to `VIDEO`, which is the right *category* and still
prices nothing: the worker reports no duration, so there is no quantity to multiply. Composition is
the heaviest accelerator step in a `realestate-reels` run and it is currently the one step that could
not be charged even if everything else worked.

**2 — a run settles once, for 5 credits, whatever it did.** Found while correcting
[STUDIO_ARCHITECTURE §7.1](STUDIO_ARCHITECTURE.md) and ADR-034 §4,
both of which described a `settleMeasured` over the sum of a run's steps that no code performs. There
is one hold per run (`StudioRunService:114`) and no per-step hold: `RunSagaService` passes
`run.holdId()` into every `StepDispatch`, so each step's outcome event carries the *run's* hold. The
first step to reach a terminal outcome settles it — at the hold's own capability, `AGENT`/`CALL`, and
`ConsumptionReport.quantityFor(CALL)` is a hardcoded `1`. Every later step finds the hold closed and
releases nothing. A `realestate-reels` run therefore **holds 480 credits and is debited 5**, or 0 when
the first terminal step reports nothing measurable.

**Why this blocks phase E rather than following it.** Phases E, H and J author packs whose value
proposition is priced work. Authoring them against a settlement path that has never charged a
multi-step run correctly means the price model stays untested end-to-end while three packs are built
on top of it — and every one of them would have to be re-priced once it is fixed. The cost of closing
it now is one worker field, one pricebook row and one settlement change; the cost of closing it after
E is those three plus a re-pricing of everything authored in between.

**What closing it requires** — the smallest set that makes one reel's cost correct end to end:

| # | Change | Resolution |
|:--:|:---|:---|
| 1 | The media worker reports its output duration | **Already true.** `app/composer.py` has always returned `frames` and `fps`; the duration was on the wire and only the unit was missing |
| 2 | A `credit_pricebook` row prices composition | **Done** — `(1, 'VIDEO', 'orazaka-compose', 'OUTPUT_SECOND', 2.0, 2, 30)`. Its own row, not VIDEO's default: 90 credits/second is what it costs to *invent* pixels, and an ffmpeg concat invents none. The worker reports `orazaka-compose` as the engine that ran |
| 3 | `billable_unit = 'OUTPUT_SECOND'` on the compose row | **Done** (`30-jobs-config.sql`) |
| 4 | A step settles against **its own** capability and quantity, not the run hold's | **Done, and not the way this row proposed.** Neither a per-step hold (ADR-034 §4 rejects it, and the reason holds) nor a per-step debit against the run hold. Steps stopped carrying the hold at all — `StepDispatch.holdId` is removed — and the saga settles once at the end through the new `settleAggregate`, pricing each step against its own capability's rate inside billing |

Item 4 was correctly identified as the one that makes the other three observable: doing 1–3 without
it would have changed nothing a user is charged.

**What this exposed, and what closed it.** `describe` and `script` contributed **0**:
`VisionAnalysisStrategy` and `ChatGenerationStrategy` reported no priceable measurement. That was
the compose trap one size up — chat and vision are the two most frequent step kinds, and the phase-E
pack is chat-only, so shipping it would have made the whole pack free.

Closed the same day by ADR-041's amendment:
`TokenUsage` now crosses the `AiClient` port (it already existed, and the synchronous path already
used it — the asynchronous one simply could not reach it), and **vision is priced per model in
`KILOTOKEN`, not `IMAGE_STEP`** — an analysis is a VLM completion and has no denoising steps for
`IMAGE_STEP` to count. Measured: a `trade-showcase` run's `caption` step bills **1 credit** on 237
real tokens, against 0 before.

**Still open, and named rather than implied:**

| # | Gap | Why it is not closed here |
|:--:|:---|:---|
| a | **Image generation** reports nothing priceable | `ImageResponse` carries no dimensions or steps; closing it means plumbing them out of the provider — the chat fix's shape, on another port. Outside the chat-and-vision scope |
| b | ✅ **Vision cannot run inside a Studio at all** | **Closed** by ADR-042: the job service resolves `assetId` → `filePath` at receipt, scoped to the acting user's own directory. Measured: `trade-showcase` now reaches SUCCEEDED on four steps out of four, debiting 3 credits, with vision billing 787 and 744 tokens |
| c | **Blueprint estimates are ~5× conservative** | `trade-showcase` reserves 100 for a run that fully metered would cost 10–25. Over-reserving is the safe direction; recalibration belongs with the phase-0 pricebook pass |

**A fourth item, found by closing (b).** The seeded vision default `llama3.2-vision:latest` does not
load against the pinned `llama-server` — *"unknown model architecture: 'mllama'"*. The `llava`
family loads. The seed is unchanged, because which model ships as default is a product decision; it
is recorded here because with the seed as it stands, vision fails on this machine for a reason that
has nothing to do with asset resolution.

**The gate that hid all of it.** No failsafe plugin was bound outside the e2e module's profile, and
surefire's includes never match `*IT` — **12 integration suites, 120 tests, invisible to
`./mvnw clean install`**. That is how two suites stayed red for a whole phase after this document's
own §3.2 migration. Failsafe is now bound in the parent, `verify` included.

---

## 7. Manifest

```
# Phase A
MODIFY  infra/initdb/30-jobs-config.sql          (+ routing_key, worker_family, billable_unit + backfill)
CREATE  contracts/sovkora-jobs-api/**            (Tier-1: CapabilityRouting, JobCommand — audit #17)
MODIFY  services/studio/sovkora-studio-service/.../AmqpStepExecutionAdapter.java   (− routingKeyFor)
MODIFY  .../application/service/BlueprintPublishService.java  (+ capability resolution check)

# Phase B
MODIFY  build/sovkora-test-support/.../architecture/GovernanceRules.java   (+ P1/P2)
CREATE  build/sovkora-test-support/.../architecture/PackPurityRules.java
MODIFY  <all> <Module>GovernanceTest.java

# Phase C
MODIFY  services/job/sovkora-job-service/.../JobExecutionStrategy.java  (un-seal → JobExecutor)
CREATE  services/job/sovkora-job-service/src/main/resources/META-INF/spring/…AutoConfiguration.imports
CREATE  infra/initdb/30-jobs-config.sql          (+ worker_registry + heartbeat)
CREATE  docs/WORKER_PROTOCOL.md
MODIFY  workers/media/app/{consumer,main}.py  (+ worker.yaml, − COMPOSE_FEATURE_KEY)

# Phase D — done (ADR-039)
CREATE  orazaka-packs/{README.md, pack.schema.json}
CREATE  orazaka-packs/{realestate-studio,trade-showcase,outbound-prospection}/**
CREATE  services/studio/sovkora-studio-api/.../model/PackBundle.java + 8 records
CREATE  krizaka/krizaka-billing/krizaka-billing-api/.../{PackProvision,EntitlementGrant,PackProvisioningClient}
CREATE  contracts/sovkora-jobs-api/.../{CapabilityDeclaration,CapabilityRegistrationClient}
CREATE  krizaka/krizaka-billing/krizaka-billing-client/.../{Http,NoOp}PackProvisioningClient.java
CREATE  services/studio/sovkora-studio-service/.../application/service/PackInstallerService.java
CREATE  services/studio/sovkora-studio-service/.../{PackInstallRepository,JdbcPackInstallRepositoryAdapter,PackBundleController}
CREATE  services/job/sovkora-job-service/.../application/service/CapabilityRegistryService.java
CREATE  krizaka/krizaka-billing/krizaka-billing-service/.../rest/PackProvisioningController.java
CREATE  apps/cli/src/{commands/pack.command.ts,services/pack.{api,bundle}.ts}
MODIFY  infra/initdb/80-studio.sql               (− 288 lines of catalogue content, → bundles)
MODIFY  infra/initdb/70-billing.sql              (− billing_pack seed, → bundles)

# Phases E–J
CREATE  orazaka-packs/{prospection,document-validation,wellbeing}/**
CREATE  workers/orazaka-worker-docs/**
CREATE  ADR-037 (internal)
```

---

## Related documentation

- [Pack catalogue architecture](PACK_CATALOGUE_ARCHITECTURE.md) · ADR-036
- [Studio architecture](STUDIO_ARCHITECTURE.md) · ADR-034
- ADR-037 — phases A and B: routing as data, the purity rules, and the AMQP worker contract
- ADR-038 · [Worker protocol](WORKER_PROTOCOL.md) — phase C: the un-sealed SPI, worker registration, and the wire spec
- ADR-039 · [Pack bundles](../orazaka-packs/README.md) — phase D: the manifest, its schema, and the compensated three-context install
- [Production readiness audit](PRODUCTION_READINESS_AUDIT.md) — #13 gates phase J, #17 was absorbed by phase A
- [Governance contract](../AGENTS.md) · [Messaging standards](../.agent/rules/messaging_standards.md)
