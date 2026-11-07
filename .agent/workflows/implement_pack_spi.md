---
description: PACK SPI — PHASED IMPLEMENTATION (phases A+B = routing as data, and the purity rules that keep it that way)
---

# Workflow: Open the pack extension seams

Design intent: [`docs/PACK_EXTENSIBILITY_ARCHITECTURE.md`](../../docs/PACK_EXTENSIBILITY_ARCHITECTURE.md).
Prior context: [`docs/PACK_CATALOGUE_ARCHITECTURE.md`](../../docs/PACK_CATALOGUE_ARCHITECTURE.md),
[ADR-036](https://github.com/krizaka/krizaka-internal/blob/main/docs/adr/sovkora/ADR-036-pack-catalogue-and-regulatory-class.md).
**The design document is normative for *what* and *why*. This workflow is normative for *how* and
*in what order*. Where they disagree, the design document wins and this file is corrected.**

## §0 Init

1. Load `AGENTS.md` — §0, §2, §3, §4 (config vs data), §5, §6 (messaging), §9, §11 bind this work.
2. Load `.agent/rules/messaging_standards.md`, `configuration_standards.md`, `naming_conventions.md`,
   `testing_standards.md`.
3. Read `docs/PACK_EXTENSIBILITY_ARCHITECTURE.md` §2 (the three seams, with evidence), §3.3 (S1),
   §4.2 (the purity rules), §6 (the plan), §7 (manifest).
4. Read, in this order, **before writing anything**:
   - `services/studio/sovkora-studio-service/…/AmqpStepExecutionAdapter.java` — the
     `routingKeyFor` substring chain you are deleting, and everything else in that class you are not
   - `infra/initdb/30-jobs-config.sql` — `orazaka_capabilities` and every seeded row
   - `krizaka/krizaka-billing/krizaka-billing-api/` — the Tier-1 module shape to mirror
   - `build/sovkora-test-support/.../architecture/{GovernanceRules,SourceFileScanner}.java`
     — the scanner you will reuse, never re-write
   **Mirror these. Do not invent a new structure.**

## §1 Scope of THIS run — phases A + B

**Phase A — S1: capability routing becomes data.** Phase B — the purity rules that stop the seam
closing again. They ship together because B's rules will fail on the violations A removes: separating
them means committing a red build.

**In scope** — exactly the file manifest of §3.

**Out of scope, do NOT start** — un-sealing `JobExecutionStrategy` (phase C), `worker.yaml` /
`worker_registry` / `WORKER_PROTOCOL.md` (C), `pack.yaml` / `PackInstaller` / the `pack` CLI verb
(D), any of the three content packs (E, H, J), capability availability in `StudioAccessService` (F),
the `orazaka-packs/` layout (G), `regulatory_class` behaviour (I). If A+B are green and you have
budget left, **stop and report**.

> **One deliberate exception.** Phase A creates the Tier-1 module `contracts/sovkora-jobs-api`
> and moves `JobCommand` into it (production-readiness audit finding #17 — a wire contract currently
> duplicated between conversation-service and job-service). Phase C needs that module anyway, and
> creating it twice is worse than creating it once. Move `JobCommand`; do **not** touch
> `JobExecutionStrategy`.

## §2 Non-negotiable constraints

| Rule | Applies here as |
|:---|:---|
| AGENTS.md §0 | 100% local. No CI, no cloud, no new network dependency. |
| AGENTS.md §4 — config vs data | Routing keys are **rows** in `orazaka_capabilities`. Not a `switch`, not a `Map` literal, not a `application.yml` block. That is the entire point of phase A. |
| AGENTS.md §6 | Exchange and routing-key conventions unchanged: `orazaka.jobs`, `job.{capability}.{action}`. You are changing *where the mapping is stored*, never the wire format. |
| AGENTS.md §2 — tier purity | `orazaka-jobs-api` is Tier-1: pure JDK + JUnit. No Spring, no Jackson, no JPA. It must not import `com.krizaka.orazaka.business.*`, `com.krizaka.orazaka.studio.*` or `com.krizaka.billing.*`. |
| AGENTS.md §3 | `CapabilityRoutingClient` is a Tier-1 port (`<Thing>Client`). Its HTTP implementation is an `*Adapter`. No `*Manager`, `*Resolver` doing I/O, no `*Util`. |
| ERR-103 | One top-level type per `.java` file **+ one mirroring test file**. |
| ERR-106/116 | Records validate in the compact constructor. |
| ERR-127 | No static helper holding the mapping. The routing decision belongs to the capability record it describes. |
| **Fail loudly** | An unresolvable capability must throw, at publish **and** at dispatch. **No default branch, no fallback routing key.** A silent misroute is the defect being removed; re-introducing it as a `catch` is the same defect wearing a hat. |
| **Behaviour preservation** | Every capability that works today must work identically after the backfill. The six seeded capabilities are the regression suite. |

## §3 File manifest — phases A + B

```
# ── phase A: routing as data ───────────────────────────────────────────────
MODIFY  infra/initdb/30-jobs-config.sql
          + routing_key   VARCHAR(120) NOT NULL
          + worker_family VARCHAR(60)  NOT NULL
          + billable_unit VARCHAR(30)
          + backfill for the six seeded rows (see §4.1 — copy from the code you delete)
          + CHECK (routing_key LIKE 'job.%')
CREATE  contracts/sovkora-jobs-api/pom.xml
CREATE  .../src/main/java/com/orazaka/jobs/domain/model/CapabilityRoute.java
CREATE  .../domain/model/JobCommand.java                    (moved — audit #17)
CREATE  .../domain/exception/UnroutableCapabilityException.java
CREATE  .../domain/port/CapabilityRoutingClient.java
CREATE  .../src/test/java/.../{CapabilityRoute,JobCommand}Test.java
MODIFY  pom.xml                                             (+ module, next to orazaka-studio-api)

CREATE  engine/sovkora-engine-persistence/.../CapabilityRoutingAdapter.java   (reads the rows)
MODIFY  contracts/sovkora-persistence-api/.../CapabilityDto.java (+ the 3 fields)
MODIFY  engine/sovkora-engine-persistence/.../<the capability entity + mapper>

# CORRECTION (applied in phase A): the studio service owns orazaka_studio_db and may not read
# another context's table (SEAM-001/002), so it CANNOT inject the persistence adapter above.
# The job service — which owns orazaka_capabilities — exposes the read; the studio service caches it.
CREATE  services/job/sovkora-job-service/.../adapter/rest/CapabilityController.java
MODIFY  services/job/sovkora-job-service/.../config/SecurityConfig.java  (/internal/v1 = SERVICE)
CREATE  services/studio/sovkora-studio-service/.../adapter/jobs/HttpCapabilityRoutingAdapter.java
CREATE  services/studio/sovkora-studio-service/.../config/{CapabilityRoutingProperties,JobPlaneConfig}.java
CREATE  services/studio/sovkora-studio-service/.../support/ServiceTokenProvider.java
MODIFY  services/studio/sovkora-studio-service/.../application/service/RunSagaService.java
          (+ fail the run when a step cannot be routed — §5 gate step 7 needs the run to END)

MODIFY  services/studio/sovkora-studio-service/.../AmqpStepExecutionAdapter.java
          − routingKeyFor and the four *_ROUTING_KEY constants
          + CapabilityRoutingClient injection
MODIFY  .../application/service/BlueprintPublishService.java   (+ every capability resolves, or refuse)
MODIFY  services/conversation/sovkora-conversation-service/.../<JobCommand users>  (import move)
MODIFY  services/job/sovkora-job-service/.../<JobCommand users>           (import move)
DELETE  orazaka-apps/services/orazaka-{conversation,job}-service/.../amqp/dto/JobCommand.java

# ── phase B: purity ────────────────────────────────────────────────────────
CREATE  build/sovkora-test-support/.../architecture/PackPurityRules.java
MODIFY  build/sovkora-test-support/.../architecture/GovernanceRules.java   (expose P1/P2)
MODIFY  <every service + interceptors + business> <Module>GovernanceTest.java
MODIFY  workers/media/app/consumer.py   (− COMPOSE_FEATURE_KEY branch)
MODIFY  workers/media/test_main.py

# ── record ─────────────────────────────────────────────────────────────────
CREATE  krizaka-internal docs/adr/sovkora/ADR-037-pack-spi-and-open-core.md
```

## §4 The work, specified

### §4.1 The backfill is a transcription, not a redesign

The six seeded capabilities get exactly the routing they have today. Take the values **from the code
you are deleting**, so the change is provably behaviour-preserving:

| `feature_key` | `routing_key` | `worker_family` |
|:---|:---|:---|
| `orazaka.core.media.video` | `job.video.generate` | `media` |
| `orazaka.studio.media.compose` | `job.compose.assemble` | `media` |
| `orazaka.core.media.image` | `job.media.generate` | `media` |
| `orazaka.core.media.vision` | `job.media.generate` | `media` |
| `orazaka.core.media.audio` · `…audio.analysis` · `…chat.speech` | *(what `routingKeyFor` returns today — verify each against the `contains()` chain before writing it down)* | |
| everything else currently falling through to the default | `job.text.process` | `text` |

**Verify, do not assume.** Run each seeded `feature_key` through the current `contains()` chain by
hand and write the result. `orazaka.core.media.audio` contains `"media"` — so today it routes to
`job.media.generate`, not to a text or audio queue. If that is wrong as *behaviour*, it is a separate
bug: **record it in the report, do not fix it in this run.** A refactor that also changes behaviour
is unreviewable.

`worker_family` is descriptive metadata for phases C/F. Nothing reads it yet; it exists so that
adding it later to a populated table is not a migration.

### §4.2 The routing read path

`CapabilityRoute` is a Tier-1 record: `(featureKey, routingKey, workerFamily, billableUnit, enabled)`,
self-validating — `routingKey` non-blank and starting with `job.`.

`CapabilityRoutingClient.route(String featureKey)` returns `Optional<CapabilityRoute>`. The studio
service resolves through it and throws `UnroutableCapabilityException` on empty. **No default.**

**Caching**: this is on the dispatch hot path and the table changes at admin speed. Cache with a
bounded TTL and invalidate on the config-change event the platform already publishes for
`orazaka_capabilities`. Copy the discipline of `HttpEntitlementProvider` — same shape, same reasons.
Do not add a new caching library.

**Degradation is not allowed here** — unlike pricing (`PackPricingClient`), where an absent price
renders "—". A step with no route cannot be dispatched anywhere. Fail the run with a message naming
the capability. The user gets an error instead of a wrong answer, which is the trade this whole phase
is about.

### §4.3 Publish-time validation

`BlueprintPublishService` already refuses a blueprint whose capabilities do not exist. Extend it: every
`CAPABILITY` step's `featureKey` must resolve to an **enabled** `CapabilityRoute`. The error names the
step id and the capability — an admin must be able to fix it without reading logs.

This is what makes the seam safe: a pack declaring a capability whose worker was never deployed fails
at publish, in the console, in front of the person who can act — not at 2 a.m. in a saga.

### §4.4 The purity rules (P1/P2)

`PackPurityRules`, modelled on `SqlBoundaryRules` and reusing `SourceFileScanner`:

- **P1** — no literal matching `orazaka\.(core|studio)\.[a-z.]+` (a capability key) and no known pack
  or studio key (`realestate-reels`, `trade-showcase`, `outbound-prospection`, `realestate-studio`,
  `document-validation`, `wellbeing`, `prospection`) in production sources under `sovkora-libs/**`,
  `orazaka-apps/services/**`, `orazaka-apps/workers/**`.
- **P2** — no conditional (`if`, `switch`, `contains`, `startsWith`, `equals`) on those identifiers.
- **Allowlist**: `infra/initdb/**`, `orazaka-packs/**` (does not exist yet — allow it now so phase D
  needs no rule change), test sources, and this list itself.

**Expect these to fail on the current tree.** That is the acceptance signal. The known violations to
clear:

1. `AmqpStepExecutionAdapter` — the four `*_ROUTING_KEY` constants and `routingKeyFor` (removed by §4.2).
2. `orazaka-worker-media/app/consumer.py` — `COMPOSE_FEATURE_KEY` and the branch on it. Replace with a
   payload-driven decision: the dispatcher already puts everything the worker needs in the message.
   The worker must decide from the **message**, never from a capability name it recognises.

If a third violation appears that §4.4 does not list, **stop and report it** before "fixing" it —
an unexpected hit means either the rule is too broad or the coupling is deeper than the design knew.

## §5 Acceptance gate — run these, in order

```bash
# 1. Everything compiles, all tests pass, including the moved JobCommand's users
./mvnw -q verify

# 2. Every governance suite, including the new PackPurityRules wiring
./mvnw -q test -Dtest='*GovernanceTest'
./mvnw -q -pl engine/sovkora-engine-persistence -Dtest=SqlBoundaryTest test

# 3. The stack comes up on the migrated capability table
orazaka stop --purge && orazaka start && orazaka dev

# 4. Every seeded capability carries a route
psql -h localhost -U orazaka_app -d orazaka_db \
  -c "SELECT feature_key, routing_key, worker_family FROM orazaka_capabilities ORDER BY feature_key;"
# expect: no NULL, every routing_key starting with 'job.'

# 5. BEHAVIOUR PRESERVED — the existing Studios still run end to end
orazaka test e2e

# 6. An unroutable capability fails LOUDLY, at publish.
#    Temporarily insert a capability with is_enabled=true and no route (or disable one a
#    published blueprint uses), then attempt to publish that blueprint:
#    MUST return 4xx naming the step id and the capability. MUST NOT publish.

# 7. An unroutable capability fails LOUDLY at dispatch too (belt and braces):
#    the run fails with the capability named; NO message is published to job.text.process.
#    Assert on the RabbitMQ queue, not on the log.

# 8. Purity bites. Re-introduce a capability-key literal in a core class, then:
./mvnw -q test -Dtest='*GovernanceTest'
#    MUST FAIL naming the file and the literal. Revert afterwards.

# 9. The media worker no longer recognises a capability by name
grep -rn "COMPOSE_FEATURE_KEY\|orazaka\.studio\.media" workers/media/app/
#    expect no match
```

Phases A+B are done when **all nine** pass. Steps 6, 7 and 8 failing to *fail* is the failure — if a
misrouted capability still reaches a queue, or a pack literal still compiles in core, the seam is not
open and the rule is decorative.

## §6 Do NOT

- **Do not** un-seal `JobExecutionStrategy`, add `worker.yaml`, `worker_registry`, `pack.yaml`,
  `PackInstaller`, the `pack` CLI verb, or any content pack. Those are phases C–J.
- **Do not** keep a fallback routing key "just in case". The fallback *is* the bug.
- **Do not** move the mapping from a `switch` into a `Map` literal, a YAML file, or an enum. It goes
  in the database — AGENTS.md §4.
- **Do not** change any capability's effective routing while backfilling. Transcribe, then report
  anything that looks wrong.
- **Do not** widen `PackPurityRules`' allowlist to make a violation pass. Fix the violation or report it.
- **Do not** let the media worker branch on a capability name. It decides from the payload.
- **Do not** touch the user's uncommitted working-tree changes.
- **Do not** commit or push — the user reviews the diff and commits.
- **Do not** modify `docs/_generated/*` by hand.

## §7 Before declaring done

1. Run the `.agent/workflows/review_architect.md` gate.
2. `initdb` and interfaces changed ⇒ run `.agent/workflows/sync_documentation.md`.
3. Write **`krizaka-internal docs/adr/sovkora/ADR-037-pack-spi-and-open-core.md`**: routing as data and why a fallback is
   forbidden; `orazaka-jobs-api` as the Tier-1 home of the job wire contract (absorbing audit #17);
   the purity rules and what they cost; and — stated now even though it lands in phases C–G — the
   decision that **the worker SPI is the AMQP contract, not a Java interface**, with the two-repository
   open-core split it enables. Name the rejected options: a `Map` in code, a YAML routing table, a
   `distribution:` flag in a single public repo.
4. Update `docs/PACK_EXTENSIBILITY_ARCHITECTURE.md` §2: mark 2.1 and the §4.4 violations closed with
   the commit ref. Keep the rows — a closed finding is evidence.
5. Report: files created/modified, the nine gate results verbatim, **the backfill table you actually
   wrote with the today-behaviour you verified for each row**, any routing that looked wrong but was
   preserved, and any place the design turned out to be wrong.

## §8 Next phases (do not start without an explicit go)

Per design §6.

- **C — worker SPI.** Un-seal `JobExecutionStrategy` → `JobExecutor` discovered by
  `AutoConfiguration.imports` (the [ERR-122] pattern interceptors already use); `worker.yaml`,
  `worker_registry` + heartbeat; publish `docs/WORKER_PROTOCOL.md` as the versioned wire spec.
- **D — pack bundle.** `pack.yaml` JSON Schema, transactional `PackInstallerService`,
  `orazaka pack validate|install|publish`, and migrate the three existing packs out of the core seeds.
- **E — Prospection pack** (Tier D) — the acceptance fixture for D: zero new capability.
- **F — availability.** Capability health feeds `StudioAccessService`: no healthy worker ⇒ the Studio
  shows unavailable and no run is submitted, so no credits are held for a doomed run.
- **G — open-core split.** `orazaka-packs/` layout, licences, `orazaka.packs.sources`, one Tier-D and
  one Tier-W reference pack in the open, `CONTRIBUTING-PACKS.md`.
- **H — Validation pack** (Tier W) — `orazaka-worker-docs`, extraction + versioned rulesets,
  `RULESET` step kind. Conformity ships as verdicts; **authenticity ships as signals with confidence,
  never as a verdict**.
- **I — `regulatory_class` enforcement** — the seven controls.
- **J — Wellbeing pack** (Tier D, `REGULATED`) — gated on I and on audit finding #13.
