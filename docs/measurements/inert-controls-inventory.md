---
title: "Inert controls — what is declared and bound to nothing"
description: "Phase M0.5 inventory. Three controls were found declared-and-inert by accident; this sweep looked for the fourth on purpose and found the second pipeline kill-switch dead by the same mechanism as the first, and the image meter ignoring the deployment it prices. Findings only — no repairs except the spotless binding."
category: Measurement
order: 2
---

# Inert controls — inventory

> **The fourth was found on purpose, and it is the same defect as the first.**
> `orazaka.core.orchestration.enabled` — the yaml calls it *"Master pipeline kill-switch only"* —
> **cannot be switched off.** Setting it to `false` yields a bean that says `true`, in both services
> that carry it. The mechanism is the `disable-ai` mechanism exactly: a config record with a second
> constructor, which Spring's binder cannot choose between, so the value is never bound and a
> fail-open default takes its place. The kill-switch and the full bypass named side by side in
> AGENTS.md §7 were both dead, for the same reason, and only one had been found.
>
> **The fifth decides money.** The `IMAGE_STEP` meter ignores `IMAGE_GEN_STEPS`, which the job
> service's own yaml says it must read to price a generation (ADR-047). It bills 20 steps whatever the
> deployment runs — 5× over at 4 steps, a third under at 30.
>
> Everything below is **recorded, not repaired**, except the one binding this phase exists to add.

## 1. Method

The workflow (§2.3) named six categories. Each was swept by a script over source, then **every
candidate was verified by hand before being written down**, and the two that decide behaviour were
**proven by a probe** — a throwaway test that bound the real service yaml, printed what the bean
received, and was deleted (`git status` clean after each). Five candidates did not survive
verification; they are listed in §4 so nobody re-finds them.

| Category | Swept how | Found |
|:---|:---|:---|
| Maven plugins with no `<executions>` | root `pom.xml`, active `<plugins>` | spotless *(bound in this phase)*, sonar |
| `@ConfigurationProperties` never read | record components vs every accessor call and in-record use | 2 real (§2.3, §2.8), 3 retracted |
| Binding defeated by a second constructor | constructor count per config record | **0 of 38 annotated types — the defect was in a type the sweep did not cover** (§2.1) |
| `@Scheduled` / `@ConditionalOn*` that cannot activate | property set nowhere with `matchIfMissing=false`; scheduling hosts | 2 (§2.9); scheduling clean |
| Profiles nothing selects | `application-*.yml`, `@Profile`, every activation site | a mirror pair (§2.4) |
| `orazaka.*` yaml keys with no reader | flattened yaml vs `@Value`, prefixes, components | 16 candidates → 2 real families |
| Rules registered in no suite | every `assert*` in `orazaka-test-support` vs every caller | 6 (§2.5) |
| *(added)* compiler output nobody reads | `-Xlint:all` count on a full build | 92 (§2.6) |

## 2. Findings, by consequence

### 2.1 The pipeline master kill-switch cannot be switched off

> **Closed by M1.5 (ADR-062 §2)** — bound, proven against both services' real yaml, and made to *refuse* rather than bypass: once alive, `false` would have skipped every gate and still called the model.

- **Declared** — `orazaka-conversation-service/…/application.yml:191` and the job service's:
  `enabled: ${ORCHESTRATION_ENABLED:true}`, commented *"Master pipeline kill-switch only"*.
- **Why it is inert** — `CoreProperties.OrchestrationConfig` is a record with its canonical
  constructor **and** a three-argument convenience constructor (`CoreProperties.java`, the
  `this(enabled, refiner, router, new RoutingConfig(null, null))` delegate). With two constructors
  and no `@ConstructorBinding` on either, the binder creates no `OrchestrationConfig` at all.
  `CoreConfiguration.java:84` then reads
  `(temp == null || temp.orchestration() == null) || temp.orchestration().enabled()` — **true**.
- **Proof** — bound against the real job-service `application.yml`: `rag=RagConfig[enabled=true,
  storeType=pgvector, topK=3]` (single constructor, binds) and `orchestration=null` (two
  constructors, does not). With `orazaka.core.orchestration.enabled=false` on top of each service's
  real yaml: **bean `orchestration.enabled=true`**, conversation service and job service alike.
- **Same cause, second casualty** — `routing` is read only from `temp.orchestration().routing()`, so
  `orazaka.core.orchestration.routing.mode` (AGENTS.md §7, `DETERMINISTIC | AGENTIC`) is always
  `DETERMINISTIC`.
- **And the contract names a key that does not exist** — AGENTS.md §7 documents the full bypass as
  `orazaka.core.orchestration.pipeline.enabled=false`. There is no `pipeline` component; the key an
  operator following the contract would set binds to nothing even once the constructor is fixed.
- **Why the sweep in §1 missed it** — `CoreProperties` carries no `@ConfigurationProperties`; it is
  bound by hand with `Binder.get(env).bind("orazaka.core", …)` inside a `try` that logs at **debug**
  and continues with defaults. The constructor sweep was scoped to annotated types. A defect class
  should be swept by its mechanism, not by the annotation it happened to wear the first time.
- **What it would have caught** — an operator stopping the pipeline. It stops nothing.

### 2.2 The image meter ignores the deployment's step count

> **Closed by M1.5 (ADR-062 §5)** — both causes fixed and tested apart; the dimensions, which came from the request, are now measured on the returned image.

- **Declared** — `orazaka-job-service/…/application.yml:150` `steps: ${IMAGE_GEN_STEPS:20}`, commented
  *"the CLI that starts it and this meter must read the same env var — it is what IMAGE_STEP prices a
  generation on (ADR-047)"*. The CLI does its half: `orazaka-cli/src/commands/start.command.ts:338`
  launches `sd-server --steps ${IMAGE_GEN_STEPS ?? "20"}`.
- **Why it is inert — two independent causes**, so repairing either alone repairs nothing:
  1. `ImageGenerationConfig` has a second, nine-argument constructor (no `steps`): the binder leaves
     `image` null (probe: `image=null` against the real yaml).
  2. `CoreConfiguration.coreProperties` never reads `temp.image()` at all — it constructs
     `new ImageGenerationConfig("localai-image", "http://localhost:8086", …, 20, …)` from literals.
     Video, vision and audio are built the same way; `AudioConfig` also has a second constructor.
- **Reader** — `ImageGeneratorClientImpl.resolveSteps()` returns `properties.image().generation().steps()`
  into `ImageUsage`, which the ledger prices as `IMAGE_STEP = images × steps × megapixels`.
- **Proof** — `orazaka.core.image.generation.steps=30` bound → **bean `steps=20`**.
- **Consequence** — `IMAGE_GEN_STEPS=4` (a turbo model): sd-server runs 4, the user is billed 20.
  `IMAGE_GEN_STEPS=30`: runs 30, billed 20. Silent either way. The same literal construction makes
  `ImageGeneratorClientImpl.java:139`'s advice — *"Configure via orazaka.core.image.generation.base-url"*
  — advice to set a key that is overwritten.
- **What it would have caught** — ADR-047's own premise: that the meter and the launcher agree.

### 2.3 M2M JWT claim validation is declared and performed nowhere

- **Declared, three times** — `M2mJwtProperties` javadoc: *"Strict claim validation: iss, sub, aud,
  scopes"*; `SecurityConfig.java:160`: *"M2M JWT intent routing — claim validation via
  M2mJwtProperties"*; `IntentEnvelope` javadoc: *"verified by M2M JWT"*.
- **Inert** — the claims had no reader and the route took its scopes from the request body; nothing
  verified the token it signed.
- **Closed** — the route, its gate types and its signer were removed (no caller);
  `IntentRouteSecurityTest` holds it (PRODUCTION_READINESS_AUDIT §2.6).
- **What it would have caught** — a caller asserting its own authentication.

### 2.4 Two profiles, each the other's mirror

- `orazaka-cli/src/commands/dev.command.ts:181` starts the conversation service with
  `-Dspring-boot.run.profiles=e2e`. **No yaml document, `@Profile`, or bean is keyed on `e2e`.**
- The only profile-gated bean in the repository, `MockAuthenticationFilter` (`@Profile("dev")`,
  `conversation-service/…/config/filter/MockAuthenticationFilter.java:29`), turns
  `Authorization: Bearer user-mock` into the dev admin (default UUID `…440001`). **Nothing
  activates `dev`** — not the CLI, not `.env`, not `infra/docker-compose.yml`, not a test.
- **What it would have caught** — which of the two was meant. Recorded without a guess. The
  mock-admin filter is one `SPRING_PROFILES_ACTIVE=dev` away from running.

### 2.5 Six governance rules enforce nothing — and the registry already said so

> **Decided by M1.5 (ADR-062 §4)** — two wired where their subjects exist (ERR-109, ERR-113), three deleted (HEX-003, HEX-004, HEX-005), one made private.

`docs/_generated/GOVERNANCE.md` lists each with enforcing suite **none**. AGENTS.md §10 built that
column so a rule with no suite would be visible without reading the test tree. It was visible. This
is the one instance where the project's own visibility mechanism did its job, and visibility was
not enough.

| Rule | Claim | Note |
|:---|:---|:---|
| `GovernanceRules.assertPersistencePackageHygiene` (`:222`) | JPA components in the correct sub-packs | |
| `…assertApplicationInputsAreRecords` [HEX-003] (`:540`) | application-layer DTOs are records | targets `.application.dto` — **0 such packages** in the repo |
| `…assertAdaptersImplementPorts` [HEX-004] (`:577`) | every adapter implements a port from `.application.ports` | **0 such packages**; the repo's ports live in `domain/port(s)` (11 directories) |
| `…assertNoUnapprovedUtilsInProduction` [HEX-005] (`:645`) | no test-scope utility libraries in production | |
| `SourceFileScanner.assertNoBannedPatterns` [GOV-003] | banned source patterns | |
| `SourceFileScanner.assertNoEnvironmentInjection` [ERR-113] | no `Environment` outside `@Configuration` | |

HEX-003 and HEX-004 were written for a layout this repository does not have: registered as they are,
the first would pass vacuously and the second would fail on every adapter. **None was run by this
inventory.**

### 2.6 `-Xlint:all` with nothing listening

`maven-compiler-plugin` passes `-Xlint:all` and no `-Werror`. A full build emits **92 distinct
warnings**: 24 unchecked conversions, 21 unchecked `query` invocations, 14 raw `RestClient` types,
11 never-referenced mocked auto-closeables, 9 deprecated `intercept(PromptContext)` overrides. The
warnings are declared on; nothing reads them.
*What it would have caught* — the unchecked conversions sit on the `Map`/JSON boundaries ERR-127 is
about.

### 2.7 A cloud quality gate in a local-only repository

`sonar-maven-plugin` has no `<executions>`, and its properties point at `sonar.host.url =
https://sonarcloud.io` (`pom.xml:42`). AGENTS.md §0 forbids hosted CI; nothing local can run it.

### 2.8 Configuration nobody reads

- `orazaka.billing.engine.{enabled, base-url, api-key, sync-batch-size, connect-timeout, read-timeout}`
  in the billing service's yaml — no Java source anywhere references `billing.engine`.
- `ToolsProperties.configs` (`orazaka-tools/…/config/ToolsProperties.java:12`) — bound by the
  conversation service's `@ConfigurationPropertiesScan("com.orazaka")`, injected nowhere, set by no
  yaml.

### 2.9 Conditions nothing satisfies, tests included

- `TimeoutSimulationHook` (`orazaka-job-service/…/amqp/TimeoutSimulationHook.java:19`) — a test hook in
  `src/main`, active only on `orazaka.jobs.enable-test-simulations=true`, which **no yaml and no test**
  sets. Dead in production and unused by the tests it exists for.
- `orazaka.interceptor.validation.closed-loop.tdr-enabled` (`ValidationAutoConfiguration.java:62`) —
  gates `SpringAiTestShaperAdapter` and the `TDR_D` validation tier; set nowhere, tests included.
  Tier D has never been constructed.

## 3. Swept, and clean

Negative results are results; they say where not to look next.

- **Every `@Scheduled` component has a host that enables scheduling** — including the four in
  libraries (`OutboxRelay` ×2, `InfrastructureProber`, `SandboxEvictionScheduler`).
- **No `@ConfigurationProperties`-annotated type of the 38 has a second constructor.** The constructor
  regex was checked against a known constructor before its empty result was believed. §2.1 shows why
  this clean result was the wrong scope rather than good news.
- **`maven-compiler-plugin`** has no `<executions>` by design — the default lifecycle binds it.

## 4. Candidates that did not survive verification

| Candidate | Why it was flagged | Why it is not a finding |
|:---|:---|:---|
| `AssetEncryptionProperties.masterKeyFile` | no accessor call outside the record | read by a derived method inside the record |
| `AssetStoreProperties.encryptedAtRest`, `.deployment` | same | read by the record's own gate method; the fifth SENSITIVE control has a planted test (ADR-051) |
| `orazaka.identity.rate-limit.enabled` | no `@Value` or prefix match | read by `@ConditionalOnProperty` on `RateLimitConfig` |
| `orazaka.core.rag.*` | no accessor call | read through method references (`RagConfig::enabled`) in `RagInterceptor` — and `RagConfig` binds |
| `orazaka.core.orchestration.enabled` as *"no reader"* | the sweep's first reading | it has a reader; the defect is that the reader never receives the value (§2.1) |

## 5. Adjacent: bound, and guarding less than they claim

A different category — these rules *run* — recorded here because the audit that found them was
interrupted on 2026-09-12 and never reported. Each line is a violation that was **planted, observed,
and reverted**.

| Rule | Planted | Result |
|:---|:---|:---|
| [SEAM-001] | cross-context FK in `infra/initdb/80-studio.sql` | **red** — the rule works on what it reads |
| [SEAM-001] | the same FK in `infra/migrations/*.sql` | **green** — the rule reads `infra/initdb` only; six migration files are unscanned |
| [SEAM-001] | the same FK with lowercase `references` | **green** — its patterns are uppercase with no `CASE_INSENSITIVE` |
| [SEAM-001] | a cross-context `INSERT` seed, no FK | **green** — AGENTS.md §5 bans cross-context seeds; nothing checks |
| [SEAM-002] | a foreign Tier-3 **type** in job-service | **red** |
| [SEAM-002] | *(not planted — already in production)* | **green on a live violation**: `CapabilityRegistryService`, `JobEventPublisher` and `JobListener` import `com.orazaka.persistence.infrastructure.config.MessagingContract`, a banned package. Its members are compile-time constants, inlined by `javac`; `javap` finds **0** references in the bytecode ArchUnit reads |
| [SEAM-002] | coverage | `orazaka-identity-service` has no governance suite; `com.orazaka.persistence.identity..` is missing from the banned-package list |
| [BILL-001] | a job producer with no marker | **red** |
| [BILL-001] | the marker only inside a javadoc comment | **green** — the whole file is matched, prose included |
| [BILL-001] | a no-marker producer under `infrastructure/config/` | **green** — the topology exclusion is by package |

### 5.1 Addendum — found during M1 (ADR-061)

- **Correction to the §5 table's reading of [PACK-001].** Its seed checks read zero rows, and the
  rule's own javadoc already says so and why it is kept. The rule is honest; what is stale is
  `pack.schema.json`'s first `allOf` description, which still says `PackCoherenceRules` fails the
  build on a missing grant. [PACK-001] now also reads the manifests for `kind`, and that half was
  seen failing on a planted `wellbeing/pack.yaml`.
- **A formatter declared with no configuration.** The UI workspaces carry `prettier` and a `format`
  script and no Prettier config file. Run, it would reformat to an 80-column default the code is not
  written in: 11 of the 12 UI files M1 touched were already non-conforming at `HEAD`. The TS twin of
  the spotless finding, one step worse — not inert, but pointed the wrong way. Not repaired.

### 5.2 Addendum — found during M1.5 (ADR-062)

A third form of inertness, which the sweep above could not have found because it looked for *not
bound* and *not invoked*: **invoked over the empty set**. [GOV-006] now fails any governance rule
whose subjects are empty. Applied, it turned **eleven invocations** red, not one — and ArchUnit's own
empty-set guard had been switched off by hand on **thirteen** rules (`allowEmptyShould(true)`).

| Finding | Where | Handled |
|:---|:---|:---|
| A third binding-defeat instance, found by [CFG-001] on its first run | `CoreProperties.AudioConfig` | constructor removed; its values are still literals in `CoreConfiguration` — recorded, not changed |
| `disable-ai` gates AI-dependent *interceptors*, not the model call | `DynamicPipelineExecutor.enforceSecurityGate`, its only read | AGENTS.md §7 now says so |
| A prompt carrying inline media skips the pipeline — every gate — and still calls the model | `EnginePipelineBridge.compileContext` | recorded; exposure mis-stated as not established — it is the chat API. **Closed by ADR-063** (audit #21) |
| Three rules returned green for a path that did not exist | `assertNoPermitAllOnInternalOrUploads`, `assertInternalSurfaceRequiresServiceAuthority`, `SourceFileScanner` | now fail through [GOV-006] |
| [SAGA-003] silently skipped any reader it could no longer find | `GovernanceRules.assertSagaReadersDoNotWrite` | now a violation by name |
| ERR-113 had two authors: an inline copy in core, and the shared rule nobody called | `core/…/GovernanceTest`, `SourceFileScanner` | one author, invoked in every suite |
| Composition bills the intended duration — **9.00 output-seconds billed for a 2.02 s file**, measured | `composer.py:122-127` | recorded, not fixed (audit #20). **Closed by ADR-063** — measured on the file, contract extended to video |
| `orazaka-identity-service` had no governance suite | — | created for [CFG-001] only; the other rules are still absent |

### 5.3 Addendum — found during M1.6 (ADR-063)

The media-turn branch was a fourth shape of the same thing: not a control bound to nothing, but a
**bypass kept for a reason that no longer existed** — the pipeline it protected had stopped receiving
base64 before this repository began. Removing it made reachable two interceptors that had never run
on an image, and they were what had to change. Tracing before reaching is now AGENTS.md §12.

| Finding | Where | Handled |
|:---|:---|:---|
| A `MediaInterceptor` seeded enabled and routed to, with no class in any commit | `30-jobs-config.sql:387`, `PipelineRegistry.DEFAULT_ROUTES`, `docs/CORE.md` | recorded |
| Media lifted out of the pipeline's *output* and attached behind every guard | `MessageCompiler` | removed — part of closing #21 |
| Five worker tests after `unittest.main()`, never run by the build | `test_main.py`, `AssetIdResolutionTest` | recorded (audit #24) |
| Audio holds settle to nothing — no strategy reports `characters` or `audioSeconds` | `SpeechSynthesisStrategy`, `AudioAnalysisStrategy` | recorded (audit #22) |
| A user's preferences override `userId` and can declare `orazaka.metering.deferred` | `DynamicPipelineExecutor.process` | recorded (audit #23) |

### 5.4 Addendum — found during M1.7 (ADR-064)

Two more mechanisms, both counted now. **The seventh**: tests below `unittest.main()`, which exits —
five, the owner-confinement tests among them, never ran. **The eighth**: test files no build runs at
all — twenty-seven in `document-validation`. A test that never ran never passed either; of the five,
two pass without the control they name, and one asserts a hole.

And a control inert by posture rather than by binding: every governance interceptor inherited the
executor's catch-all, so a crisis guard that threw passed the turn. Controls now declare `failsClosed()`.

| Finding | Where | Handled |
|:---|:---|:---|
| `roles` safe only because its line sits below a raw merge; `userId` restored by a second line order | `DynamicPipelineExecutor`, `UserContextResolver` | namespaced merge (audit #23) |
| A guard subject read from a key only the user could write, tested on hand-built metadata | `SafetyInterceptor`, `ScopeGuardInterceptor` | removed |
| Tests below `unittest.main()`; test files with no build execution | worker-media, document-validation | collected-equals-defined guard (audit #24, #29) |
| Two security tests pass vacuously; the containment check has no test | `AssetIdResolutionTest` | recorded (audit #24) |
| `MessageDedupService.release` written and called by nothing | billing consumers | recorded (audit #30) |

### 5.5 Addendum — found during M1.8 (ADR-065)

A **ninth** mechanism, the inverse of a control bound to nothing: a declaration bound to the wrong
thing. `studio_run_audit.detail` was declared *"a reason code, never user content"* and filled with a
step's error message — on a REGULATED pack, the crisis response, in an append-only table. The comment
was the control, and nothing checked it.

And a **tenth**: a bootstrap file nothing executes. `30-jobs-config.sql` stopped parsing on 2026-09-09
and every build since has been green, because the only readers of that file are regexes.

| Finding | Where | Handled |
|:---|:---|:---|
| A column documented as a reason code, holding crisis responses | `studio_run_audit.detail`, `RunSagaService.failRun` | removed (ADR-065 §2) |
| A test written in the same commit as the branch it asserts, pinning an absolute-path read open | `test_an_absolute_path_is_left_alone` | rewritten to assert the refusal (audit #28) |
| A run's retention window declared per class and stopping at the studio's edge | `RetentionSweeper`, `orazaka_jobs` | the class travels on the job (audit #27) |
| An initdb file that does not parse, green in every build | `30-jobs-config.sql:155-157` | recorded (audit #31) |
| A timeout that abandons its execution instead of stopping it | `JobListener.onMessage` | recorded (audit #34) |

## 6. The one repair

`spotless:check` is bound to `validate` in the root `pom.xml`, after an 82-file `spotless:apply` in a
commit of its own. `validate` rather than `verify`: it is the first phase, so the failure arrives
before anything compiles, and `-DskipTests` cannot route around it.

**Proven by planting** — three blank lines and a misindented comment added to `FailureCause.java`:

```
[ERROR] Failed to execute goal com.diffplug.spotless:spotless-maven-plugin:2.43.0:check (spotless-check)
        on project orazaka-jobs-api: The following files had format violations:
[ERROR]     src/main/java/com/orazaka/jobs/domain/model/FailureCause.java
[ERROR] Run 'mvn spotless:apply' to fix these violations.
```

Reverted; `validate` green again; the full build ran the check on 28 modules.

## 7. What the pattern says

Five findings in this document share one shape — **a value that is declared, bound to nothing, and
replaced by a default that fails open**: the two kill-switches, the image meter, the M2M claims, and
the intent-token key (since removed with its route). AGENTS.md §12 already names the principle — *"a default is a declaration
nobody made"* — and applies it to data crossing between components. It has not yet been applied to
configuration crossing into a component. That is an observation for the architect, not a rule this
phase adds.
