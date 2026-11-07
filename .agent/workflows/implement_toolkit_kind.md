---
description: UNIFIED SURFACE — M0.5 (the mechanical sweep, alone, first) then M1 (pack.kind and derived TOOLKIT installation)
---

# Workflow: M0.5 + M1 — the sweep, then `kind`

Design intent: [`docs/UNIFIED_PACK_SURFACE.md`](../../docs/UNIFIED_PACK_SURFACE.md) §3.3 (`tool` is
not a category), §5 (the sequence, rewritten after M0), §5.1 (what M0 found), §5.2 (why M2.5 exists).
Prior run: [`measure_run_overhead.md`](measure_run_overhead.md) and
[`docs/measurements/M0-run-overhead.md`](../../docs/measurements/M0-run-overhead.md).
**The design document is normative for *what* and *why*. This workflow is normative for *how* and
*in what order*. Where they disagree, the design document wins and this file is corrected.**

## §0 Init

1. Load `AGENTS.md` — §0, §2, §3, §4 (config vs data), §9, §11, §12.
2. Load `.agent/rules/naming_conventions.md`, `testing_standards.md`, `configuration_standards.md`.
3. Read `docs/UNIFIED_PACK_SURFACE.md` §3.3, §5, §5.1, §5.2.
4. Read, **before writing anything**:
   - `infra/initdb/80-studio.sql` — `pack`, `studio_installation`, `pack_studio`, `studio`
   - `services/studio/sovkora-studio-api/.../model/{Pack,PackSummary,PackCatalogEntry,PackTier,RegulatoryClass}.java`
   - `services/studio/sovkora-studio-service/.../application/service/StudioAccessService.java`
     — `evaluate`, `evaluateAll`, `requireEntitled`. **This is the class M1 changes.**
   - `…/application/service/PackCatalogService.java` and `…/adapter/rest/PackCatalogController.java`
   - `…/adapter/rest/StudioInstallationController.java` — what installing means today
   - `…/application/service/RunSagaService.java` — where a run resolves its blueprint version

## §1 Scope of THIS run

**Two pieces, in this order, as two separate commits.** M0.5 is mechanical and must not be mixed
into M1's diff — that is the same rule phase C applied to the repo-wide reformat, and for the same
reason: a diff that contains both is unreviewable.

**Out of scope, do NOT start** — the `orazaka-media` bundle or any blueprint (M2); the outbox on step
dispatch, lanes, `latency_class`, the per-lane ceiling (M2.5 — §5.2 of the design doc, and it is a
prerequisite to M3, not to M1); deleting any controller or UI hook (M3); dropping `uri_path` /
`http_method` / `payload_template` (M4); the bypass rule (M5). If M0.5 and M1 are green and you have
budget left, **stop and report**.

---

## §2 M0.5 — the mechanical sweep (commit 1, alone)

M0's run found `spotless` declared in the root `pom.xml` with configuration and **no `<executions>`
block**. It is bound to no phase, so `./mvnw verify` never checks formatting, and files are committed
in a state the project's own formatter disavows. Twenty lines below it, the failsafe plugin carries a
nine-line comment explaining that a suite nothing runs is a suite that is already broken. The lesson
was written, in the same file, and did not travel.

This is the **third** control found declared-and-inert — after the `disable-ai` kill-switch
(`SecurityProperties` had a no-arg constructor Spring chose, so the property was never read and every
startup log truthfully said "inactive") and `*IT` never matching surefire's includes. Three found by
accident; none by a failing test.

1. `./mvnw spotless:apply` across the repository. **Commit that alone, touching nothing else.** State
   the file count in the commit message.
2. Bind `spotless:check` to a phase so the build fails on unformatted source. `validate` is the
   conventional choice; if a faster phase serves better, say which and why.
3. **The inventory.** Sweep the build and the Spring configuration for anything else *declared and
   bound to nothing*: plugins without executions, `@ConfigurationProperties` classes whose properties
   are never read, `@Scheduled`/`@ConditionalOn*` beans that cannot activate, profiles nothing
   selects, properties in `application.yml` with no reader, ArchUnit rules registered in no suite.
   Write it to `docs/measurements/inert-controls-inventory.md` — **findings only, no repairs**, each
   with the evidence line and a one-line "what it would have caught".
4. Anything the inventory finds is **reported, not fixed**, except the spotless binding itself.

> The inventory is the point, not the formatting. Three instances of one pattern found by accident
> is the signal; the fourth should be found on purpose.

---

## §3 M1 — `pack.kind` and derived installation (commit 2)

### §3.1 What `kind` is, and what it is not

`category` answers **for whom** (`business`, `lifestyle`). `kind` answers **what sort of thing**:

- `VERTICAL` — a pack a user chooses and installs. Prospection, Validation, Bien-être.
- `TOOLKIT` — platform capability that an entitled actor simply *has*. The media pack of M2.

`kind` earns its keep twice: it removes `tool` from being a category it was never a peer of, **and**
it makes installation for a TOOLKIT **derived rather than stored** — which deletes the state
"the plan grants image generation, but the pack is not installed", a state with no correct behaviour.

> **Three orthogonal classifications now live on `pack`**: `PackTier` (D/C/W — what it contributes),
> `kind` (VERTICAL/TOOLKIT — how it reaches the user), `regulatory_class`
> (STANDARD/SENSITIVE/REGULATED — what it forces). **Do not conflate them, do not derive one from
> another**, and say so in the DDL comment. A future reader will assume TOOLKIT implies Tier-C or
> implies STANDARD; neither is true.

### §3.2 The two questions M1 must answer rather than fudge

**1. What version does a derived installation pin?** `studio_installation.pinned_version` exists
because *"upgrades are explicit, never silent"*. A derived install has no row, so it has no pin: a
TOOLKIT run must resolve the latest `PUBLISHED` blueprint version instead. That is a deliberate
semantic split — **VERTICAL pins, TOOLKIT floats** — and it is defensible only because a TOOLKIT is
platform capability the user did not choose a version of.

It carries one hard requirement: **resolve once, at run start, and record the resolved version on
`studio_run`.** Never re-resolve per step. A publish landing mid-run must not produce a run that
executed two versions of its own blueprint. If `studio_run` has no column for the resolved version,
adding one is in scope for M1.

**2. Which regulatory classes may be TOOLKIT?** Consent is recorded on `studio_installation`
(`consent_version`, `consent_recorded_at`, `age_attested_at`, `region`). A derived install has no row
to record it on. Therefore:

- `STANDARD` — TOOLKIT allowed.
- `SENSITIVE` — TOOLKIT allowed. Its four controls (data class, shortened retention, append-only
  audit, scope guard) all hang off the pack and the run, not the installation.
- `REGULATED` — **TOOLKIT forbidden.** Its consent gate and `region` have nowhere to live, and a
  consent gate with nowhere to live is not a consent gate.

Enforce it in the DDL with a `CHECK`, not in a service. A control that only exists in Java is one
`INSERT` away from not existing.

### §3.3 File manifest — M1

```
MODIFY  infra/initdb/80-studio.sql
          + pack.kind VARCHAR(20) NOT NULL DEFAULT 'VERTICAL'
          + CONSTRAINT ck_pack_kind CHECK (kind IN ('VERTICAL','TOOLKIT'))
          + CONSTRAINT ck_pack_toolkit_not_regulated
              CHECK (kind <> 'TOOLKIT' OR regulatory_class <> 'REGULATED')
          + DDL comment: kind / tier / regulatory_class are orthogonal (§3.1)
          + studio_run: the resolved blueprint version, if no column already carries it
          existing seeded packs stay VERTICAL — the default is the migration

CREATE  services/studio/sovkora-studio-api/.../model/PackKind.java      (+ its test)
MODIFY  .../model/{Pack,PackSummary,PackCatalogEntry}.java                     (+ kind, + tests)

MODIFY  services/studio/sovkora-studio-service/.../application/service/StudioAccessService.java
          derived installation for TOOLKIT: entitlement alone = installed, no row read
MODIFY  .../application/service/PackCatalogService.java                        (kind on the read path)
MODIFY  .../application/service/RunSagaService.java                            (resolve-once, §3.2.1)
MODIFY  .../infrastructure/adapter/persistence/JdbcPackRepositoryAdapter.java
MODIFY  .../adapter/rest/StudioInstallationController.java
          installing a TOOLKIT is not an error and not a no-op — decide which, state why,
          and make the two refusals (unentitled TOOLKIT / uninstalled VERTICAL) the SAME shape

MODIFY  <ui>  the marketplace: a TOOLKIT band that reads "included", never an Install button

MODIFY  build/sovkora-test-support/.../architecture/PackCoherenceRules.java
          [PACK-001] extended: seeded kind must satisfy the same invariant the CHECK does
CREATE  krizaka-internal docs/adr/sovkora/ADR-0NN-pack-kind-and-derived-installation.md
MODIFY  docs/PACK_CATALOGUE_ARCHITECTURE.md, docs/UNIFIED_PACK_SURFACE.md §5   (M1 row → done)
```

## §4 Non-negotiable constraints

| Rule | Applies here as |
|:---|:---|
| AGENTS.md §0 | 100% local. No CI, no cloud. |
| AGENTS.md §4 | `kind` is a **row**, like `regulatory_class`. Not a property, not an enum switch in a service. |
| AGENTS.md §2 | `PackKind` is Tier-1: pure JDK. No Spring, no Jackson annotations. |
| ERR-103 | One top-level type per file **+ one mirroring test file**. |
| **Invariants in the DDL** | The TOOLKIT/REGULATED exclusion is a `CHECK`. A service-only guard is not a guard. |
| **Same refusal shape** | An entitled-but-unavailable TOOLKIT and an uninstalled VERTICAL must fail identically from the caller's side — carrying the pack to buy, as `StudioNotEntitledException` already does. Two refusal shapes is how a UI ends up with two code paths and one of them wrong. |
| **Behaviour preservation** | The three content packs are the regression suite. A VERTICAL pack must behave byte-identically: same install, same pin, same consent, same refusal. |
| **No repairs in passing** | The ~9 redundant statements M0 counted stay. Argued in §5.1 of the design doc; do not reopen it here. |

## §5 Acceptance gate

1. A `TOOLKIT` pack shows as available to an **entitled** actor with **zero** `studio_installation`
   rows for it — proven by a test that asserts the row count is zero.
2. An **unentitled** actor gets the same refusal shape as an uninstalled VERTICAL, carrying the pack.
3. A run against a TOOLKIT studio resolves its blueprint version **once**, records it on the run, and
   **publishing a new version mid-run does not change what that run executes** — this needs a test
   that actually publishes between two steps, not an assertion about intent.
4. `INSERT` of `kind='TOOLKIT', regulatory_class='REGULATED'` is refused **by the database**.
5. The three existing packs are unchanged in behaviour; their suites pass untouched.
6. `[PACK-001]` reddens on a seed that violates the kind invariant — **prove it by planting one**,
   watching it fail, and reverting. A rule nobody has seen fail is a rule nobody has tested.
7. `./mvnw -q verify` green, including the failsafe `*IT` suites.

## §6 Do NOT

- Do **not** author the media pack or any blueprint. That is M2 and it is the pleasant part; starting
  it is how M3 never happens.
- Do **not** touch step dispatch, the outbox, worker bindings, `latency_class` or the sweeper. All of
  that is M2.5 and it has its own reasoning in §5.2.
- Do **not** delete `MediaGenerationController`, `useBootstrapFeatures` or the composer button row.
  That is M3, and it is irreversible.
- Do **not** derive `kind` from `tier`, from `regulatory_class`, from the category, or from the pack
  key's spelling. Substring inference on a business identifier is the defect phase A removed.
- Do **not** mix M0.5's reformat into M1's commit.

## §7 Before declaring done

1. Two commits, in order, each green on its own. State both hashes.
2. The planted `[PACK-001]` violation: paste the failure output and confirm the revert.
3. The inert-controls inventory exists and lists evidence lines, not impressions.
4. Report anything you found and did not fix, with where it is.
5. **Commit if green. Never push.**

## §8 Next phases — context only, do not start

M2 the `orazaka-media` bundle and its six single-step blueprints → **M2.5 harden the run path**
(outbox on dispatch, lanes not priorities, per-lane ceiling — design doc §5.2, prerequisite to M3) →
M3 close door 1 → M4 drop the UI-manifest columns → M5 the rule that stops the bypass reopening.
