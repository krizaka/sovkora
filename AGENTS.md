# Sovkora — Governance Contract (agent-neutral)

> **Single source of truth** for architecture, code and agent-behaviour rules in **the Sovkora repository** (one repository since
> ADR-029, 2026-11-08). This file is **agent-neutral**: `CLAUDE.md` (and any other agent file) only **imports** it (`@AGENTS.md`).
> Every generation, review, or refactor **must** enforce these constraints without exception.
>
> Architectural detail (non-normative): [`docs/VISION_ARCHITECTURE.md`](docs/VISION_ARCHITECTURE.md), [`docs/INTERFACES.md`](docs/INTERFACES.md), [`docs/DEVEX_LIFECYCLE.md`](docs/DEVEX_LIFECYCLE.md); how Sovkora is packaged, signed, installed and updated on a client's premises: [`docs/DELIVERY.md`](docs/DELIVERY.md). Code-derived docs (architecture map, module map, ports, use-cases, business layer, tools, jobs & schedules, packs, administration, interceptors, API, CLI, models) are generated under [`docs/_generated/`](docs/_generated/) by `node scripts/generate-docs.mjs`. ADRs are internal (krizaka-internal `docs/adr/`).

---

## 0. Current phase — **LOCAL runtime, one GitHub repository**

- The platform is **one GitHub repository**, [`krizaka/sovkora`](https://github.com/krizaka/sovkora) (ADR-029): every module is a folder (§2.1). The runtime cycle (run, e2e, docs) still runs **on the dev machine (macOS 64 GB)**.
- **Allowed on GitHub**: GitHub Actions that **build and test** (one CI, `.github/workflows/ci.yml`), and that **publish on a tag**: the Mac app (`desktop-v*`), the mobile build (`mobile-v*`), the CUDA image of the media worker (`ghcr.io/krizaka/sovkora-media-worker`), the CLI on npm (`@krizaka/orazaka-cli`, provenance). Sovkora's Java artifacts are **not published**: only images ship (ADR-029). The Krizaka building blocks `com.krizaka:*` come from **Maven Central** (krizaka-java).
- **Allowed in the cloud (decision 2026-10-14)**: the **dev** environment only — `dev.sovkora.com` and `admin.sovkora.com` (the same `sovkora-dev` app, told apart by the `Host`) on DigitalOcean App Platform, built from this repository (the web app from `apps/web`, the `core` image from `infra/deploy/Dockerfile`), the shared dev cluster `krizaka-dev-pg`, an hourly GPU host started and stopped by hand. How: [`docs/DEPLOY_DEV.md`](docs/DEPLOY_DEV.md); its infrastructure as code is krizaka-infra `stacks/dev/sovkora`. Secrets live in the maintainer's local vault and in App Platform (encrypted), never in a repository.
- **Forbidden for now**: any other cloud deployment (staging, production), cloud provisioning, remote Terraform `apply`, remote secrets beyond `GITHUB_TOKEN` and the organisation's.
- **Allowed locally**: `docker-compose` (stateful infra), **native macOS** AI runtimes (Metal), running apps via the `orazaka` CLI, **hermetic** tests (Testcontainers), docs generation.
- The code stays **ready** for staging/prod (hexagonal + config-per-profile), but those environments are **deferred**: do not implement them until explicitly requested.

---

## 1. Execution & CLI

- **Single orchestrator**: the `orazaka` CLI (`apps/cli`, on npm as `@krizaka/orazaka-cli`). **No shell scripts** (`.sh`/`.bat`) for setup or operations **[ERR-125]**.
- The loop: `./mvnw verify` (the whole reactor) → `pnpm install` (the clients) → `orazaka install` → `orazaka start` (Docker infra) → `orazaka models pull` → `orazaka dev` → `orazaka onboard`. The Krizaka services `dev` runs beside Sovkora's (users, billing, notifications) come from a krizaka-java checkout at the release `build/sovkora-parent` imports, in `.krizaka-java/`.
- Tests: `orazaka test [unit|it|e2e]`, or the module's own command (README « Run it »). Docs: `node scripts/generate-docs.mjs`.
- **Non-negotiable execution boundary**: stateful infra (PostgreSQL+pgvector, Redis, RabbitMQ) in **Docker**; AI inference (Ollama, image, video) **native macOS**. Never run inference in Docker (CPU fallback = destroyed DevX).

---

## 2. Architecture & module boundaries

Hexagonal (Ports & Adapters), enforced by **ArchUnit** at build time. Dependencies point **toward the core**; `orazaka-core` depends on no outer layer.

### Layers & responsibilities

| Layer | Module | Role (single) |
|:---|:---|:---|
| Ingress | `orazaka-conversation-service` (interactive host, ex-`orazaka-router`) | Headless Ingress: translates transport (HTTP/SSE/GraphQL) → `Intention`, enforces security. **No business logic.** The async executor is now `orazaka-job-service`; the transparent facade is `orazaka-edge`. |
| Orchestration (macro) | `orazaka-business` | App Factory: resolves `Intention` → `UseCase`, deterministic workflow (DAG) **or** saga. Delegates dynamic planning to the core. |
| Cognition (micro) | `orazaka-core` | Cognitive engine: `AiClient`, interceptor pipeline, `agent` capability, Provider Mesh. Web/DB-agnostic. |
| Pipeline | `orazaka-interceptors` | Cross-cutting filters (one module, packs by concern). |
| Outbound capabilities | `orazaka-tools` | MCP · RAG · sandbox. |
| Security (cross-cutting) | `krizaka-users-core` | RBAC · OAuth2 · crypto · **user profile**. |
| State | `orazaka-persistence` | CQRS: write+outbox / read models / cache. |
| Async | `workers/` | Consume RabbitMQ, build projections, heavy jobs (native media). |

### Absolute invariants

- `business` **never** imports `com.krizaka.orazaka.core.*` as internal state; it invokes the core **inbound ports** (`AiClient`, etc.).
- `core` contains **no** business logic or product-domain rules.
- `interceptors`: `orazaka-core/.../application/interceptor/` contains **only** the `PromptContextInterceptor` interface. All implementations live in `orazaka-interceptors/` (AutoConfiguration SPI) **[ERR-122]**.
- A **port** is defined in the module that needs it (the core) and **implemented** in an outer module (package-private adapter).
- **Router = sole translation boundary** between transport and `Intention`.

### Entry concept — `Intention` (not "query")

The entry unit is an immutable `Intention` typed `COMMAND | QUERY` (the **CQRS** pivot) with `capability` (`CHAT|IMAGE|AUDIO|VIDEO|AGENT|ADMIN`) and `mode` (`SYNC|ASYNC`). Contracts: see `docs/INTERFACES.md`.

### Orchestration — three scopes (do not conflate)

1. **Pipeline** (1 inference) → `core`. Mode `DETERMINISTIC` (DB order) | `AGENTIC` (classification).
2. **Agent loop** (plan→act→observe) → `core`, `agent` capability. **The intelligence lives in the core.**
3. **Workflow / use-case** (macro, saga) → `business`. Deterministic (DAG) or dynamic (**delegates** the plan to the core). `business` stays thin coordination — it hosts **no** LLM logic.

### 2.1 One repository, folders by role (ADR-029)

Every module is a **folder of this repository**. [`modules.json`](modules.json) is the **single list** of modules, their folder, their toolchain, their dependencies and the bootstrap SQL they own, **in build order**; the docs generator, the CI plan, the dev tools and the governance rules that read the whole tree all read it.

| Role folder | Modules |
|:---|:---|
| `build/` | `sovkora-parent` (inherits `krizaka-parent` from Maven Central; the Spring Boot / Spring AI BOMs, the Sovkora BOM, the naming guards) · `sovkora-test-support` (the governance kit) |
| `contracts/` | `sovkora-jobs-api` · `sovkora-persistence-api` · `sovkora-crypto` — Tier-1, pure interfaces and records |
| `engine/` | `sovkora-engine-{core,interceptors,tools,springai,business,persistence,persistence-bridge,assets,architecture}` — the engine, imported, never run |
| `services/<context>/` | one Spring Boot deployable per bounded context: `edge`, `conversation`, `job`, `knowledge`, `automation`, `studio` (`-api`, `-client`, `-service`) |
| `e2e/` | `sovkora-e2e`, the hermetic journeys |
| `workers/` | `media`, the Python worker (uv) — an external process speaking only the wire contract |
| `packs/` | the packs (Apache-2.0) and `pack.schema.json` |
| `apps/` | `web`, `mobile`, `desktop`, `cli` — one pnpm workspace, each app with its own lockfile |

**Rules** (build-enforced where a rule can see them):

- **The Java naming convention (ADR-029 N1 to N6)**: one groupId `com.krizaka.sovkora`; artifactIds `sovkora-<module>[-<part>]`, never `-common`, `-util(s)`, `-impl`, `-misc`; one package root per module, `com.krizaka.sovkora.<module>[.<part>]`, no split package; a module folder's leaf name is its artifactId, under its role folder. Held by the Maven enforcer (`sovkora-parent`) and `scripts/check-java-layout.mjs` (CI).
- A module depends only on modules listed **before** it in `modules.json` — no cycle, ever.
- A **domain module** holds its contract, its client and its service together (`services/studio`: `sovkora-studio-api` + `-client` + `-service`). Other modules depend on the **contract or client**, never on the implementation ([SEAM-002]).
- A module owns **its context's bootstrap SQL** (`<module>/infra/initdb/NN-*.sql`, §5) and its tests read it locally.
- **Rules that read the whole tree** (pack purity, executor coherence, one settlement author…) run in this repository and are **skipped — never passed** — in a copy of one module (`Workspace.require`, sovkora-test-support).
- Versions move together: every module is at the version of `sovkora-parent`; every Krizaka artifact at `krizaka.version`, which `scripts/check-krizaka-versions.mjs` holds to what Maven Central carries.
- **Cross-cutting code has one author** (ADR-073): the security baseline, session JWT, service tokens, message dedup and the outbox relay come from krizaka-java's service kit; `[KIT-001]`…`[KIT-004]` fail on a local copy.
- **The retired code names only fall** (krizaka-internal#2684): `scripts/check-code-names.mjs` (CI) counts them per top-level folder against `scripts/code-names.ratchet.json`; a rise fails. After a rename, `node scripts/check-code-names.mjs --update` lowers the ratchet (never raises it); what is kept on purpose is in `docs/NAMING.md`.
- **The licence boundary is the folder**: FSL-1.1-ALv2 at the root, Apache-2.0 in `build/`, `contracts/`, `services/edge/`, `apps/cli/` and `packs/` (NOTICE).

`business`/`core`/`interceptors`/`tools` stay **libraries** (*layer ≠ process*): the same `business → core` path is packaged into every service and every worker. A worker is a **host**, not an orchestrator — downstream it is the same path.

### Sharing tiers — what may cross a bounded-context boundary

`contracts/` and `engine/` hold **three tiers with different sharing rules**. A deployable service may depend on Tier-1 + Tier-2 + **its own** Tier-3 — **never another context's Tier-3**. Build-enforced by `<Module>GovernanceTest` **[SEAM-002]**.

| Tier | Content | Sharing rule |
|:---|:---|:---|
| **1 — Contracts** (`*-api`: platform ones in `orazaka-contracts`, domain ones in their domain repository) | pure interfaces + DTOs, zero impl deps | shared **freely**, semver'd; the *only* way one context calls another |
| **2 — Platform SDK** | `core` (AiClient, provider mesh, agent), `interceptors`, `tools`, `test-support` | shared with **SDK discipline** — stable, backward-compatible, changes rarely |
| **3 — Owned domain** | `business`, `identity`, `persistence` | owned by **one** service (or one service-family); each owns its schema + DB |

**Exemplar**: every service depends on `krizaka-users-api` (Tier-1 contract), never on `krizaka-users-core` (Tier-3 impl, owned by `krizaka-users-service`). An `engine/` that **shrinks** as domain code migrates into its owning service is healthy; one that **grows** is a distributed monolith forming — treat that as the primary health gauge.

---

## 3. Naming conventions

- Module: `sovkora-<module>[-<part>]`. Package: `com.krizaka.sovkora.<module>[.<part>]`, then by feature (§2.1, ADR-029).
- **Folder taxonomy — the `sovkora-` prefix is for artifacts, not role folders.** The role folders are bare (`build/`, `contracts/`, `engine/`, `services/<context>/`, `e2e/`, `workers/`, `packs/`, `apps/`) because they namespace nothing publishable; only Maven modules carry the prefix (`services/edge/sovkora-edge-service`). Never nest the prefix on a container.
- **Zero product prefix** on classes **[ERR-104]** (`Engine`, not `SovkoraEngine`).
- Suffixes: `*Service` (interface) / `*ServiceImpl` (package-private), `*Interceptor`, `*Resolver`, `*Controller`, `*Mapper` (final, static, package-private), `*Repository`, `*Config`, `*Properties`, `*Adapter`.
- **Controllers are resource-oriented [ERR-128]**: name a controller `<Resource>Controller` after the noun it manages (`FeatureController`, `JobController`), **never** after an actor or flow — no `Admin*`, no `Bootstrap*`. Access control is a security concern (method-level `@PreAuthorize` + SecurityConfig URL rules), not the class name. One controller per resource with a single `@RequestMapping("/api/v1/<resource>")` base; split only by genuine sub-resource (`<Resource><SubResource>Controller`, e.g. `MediaGenerationController` vs `MediaAnalysisController`). Do not scatter one resource across several controllers.
- **Application services are capability-oriented [ERR-129]**: name an application service `<Capability>Service` after what it serves (`MediaJobService`, `JobStreamService`), **never** after a design-pattern role — no `*Orchestrator`, `*Evaluator`, `*Manager`, `*Handler`, `*Processor`, `*Coordinator`. Like controllers ([ERR-128]), the pattern is an implementation detail, not the name. `application/service` holds **only** services (`*Service`) — a mapper lives with the code it maps for (inlined into its single consumer, or beside its adapter), never here. A core **outbound-port** implementation is an `*Adapter` in `infrastructure/adapter/persistence` (§ outbound ports below), never `*ServiceImpl`/`*ProviderImpl`/`Jpa*Repository`; config is `*Properties`. (Library modules that use the port/impl split instead hold package-private `*ServiceImpl`/`*ProviderImpl`/`*ManagerImpl` in `application/service`, each beside the entity `*Mapper` it uses.) Build-enforced **per module** by `<Module>GovernanceTest` reusing the shared `GovernanceRules` (orazaka-test-support).
- **One pack, one component kind [ERR-130]**: locate a class by *what it is*. `domain/model` = pure domain value objects (**no** `*Request`/`*Response` — transport DTOs go in `adapter/rest/dto` or `adapter/amqp/dto`); `application/service` = `*Service` only; `infrastructure/adapter/<rest|amqp|persistence|workflow>` = that transport/concern's adapters; `infrastructure/config` = `@Configuration` + `*Properties`, package-private filters in `config/filter` (exposed as qualified `Filter` beans); `infrastructure/support` = cross-cutting infrastructure shared across adapters — stateless utilities (`*Resolver`/`*Util`), shared `@Component` plumbing (`*Store`/`*Registry`/`*Gateway`), shared `*Exception`s; no business logic, never a `*Service`/adapter/config. Build-enforced **per module** by `<Module>GovernanceTest` (router, business, interceptors, identity, tools, persistence × 2 — core has its own), each reusing the shared `GovernanceRules` (orazaka-test-support) so one contract holds framework-wide.
- Outbound ports: `<Capability>Client` · `<Thing>Provider` · `<Thing>Repository`. Adapters: `<Tech><Port>Adapter`.
- I/O records: `*Command`, `*Query`, `*Request`, `*Response`, `*Event`, `*Descriptor`.
- Use cases: `<Domain><Action>UseCase`. Interceptors: `<Concern>Interceptor` (in the `<concern>` package).
- **Pipeline module = `orazaka-interceptors`** (decided), **not** `orazaka-hooks`: it is an ordered, transforming, short-circuitable pipeline, not lifecycle callbacks; and "hooks" collides with React hooks.
- **Functional naming, not marketing** (de-marketing decided): `ClosedLoopValidationInterceptor` (ex-`QuantumValidationAdvisor`), `SemanticRouterInterceptor` (ex-`SimDagRouterInterceptor`), `PolicyConfigCache` (ex-`ConfigMeshCache`).
- **Never `gateway`**: fix any `gateway` drift found. The former `orazaka-router` app dissolved in the microservices split — its interactive surface is `orazaka-conversation-service` (`com.krizaka.orazaka.conversationservice`, still :8080 behind the edge), its async executor is `orazaka-job-service` (`com.krizaka.orazaka.jobservice`, :8090), and the transport facade is `orazaka-edge` (:8088). Config identifiers `orazaka.router.*` / `ROUTER_*` env / `ROUTER_INTERNAL_URL` are retained as the conversation-service's wiring keys (renaming them is deferred cosmetic churn).

---

## 4. Backend standards

- **Java 21**, **Spring MVC + virtual threads (Loom)** — **not WebFlux**, **not Spring Cloud Gateway** (rationale: `docs/VISION_ARCHITECTURE.md` §8). Enable `spring.threads.virtual.enabled=true`.
- 1 top-level type per `.java` file + 1 mirroring test file **[ERR-103]**.
- **Self-validating records** for DTO/domain: validation in the **compact constructor**. The field owner validates it **[ERR-106/116]** — ban `if (dto.field() != null)` guards in services.
- **Mapper isolation**: mapping logic in `final class *Mapper`, static, package-private; ban setter chains > 5 lines **[ERR-107]**.
- **Concurrency**: blocking I/O, remote calls, and DB on **virtual threads**. Ban `synchronized` around I/O; use `ConcurrentHashMap`/`AtomicReference`. Propagate `SecurityContext` manually (MVC+Loom model).
- **Outbound HTTP**: `RestClient` or `@HttpExchange`. Ban manual `java.net.http.HttpClient`.
- Segregated ingress **[ERR-112]**: GraphQL/REST/AMQP handlers in separate sub-packs (`adapter.graphql/`, `adapter.rest/`, `adapter.amqp/`).
- Outbound ports = **public** interfaces; adapters = **package-private** beans in `infrastructure`.
- **Zero dead code**: no unused class/method/export/dependency, no commented-out code left in place, no orphan file. Delete **aggressively** (verifiable: IDE inspections + compiler on the Java side; `depcheck` / `knip` / `ts-prune` on the TS side). During the refactor, remove anything that does not serve the target.
- **Config vs data**: `application.yaml` = infrastructure wiring only (env-driven, each value declared once, typed `@ConfigurationProperties`); domain data & behavior (feature/capability registry, model catalog & default model, interceptor order/enable, rate-limit tiers) live in the **DB** and are never duplicated in yaml.

### No static helpers / primitive obsession **[ERR-127]**

`*Helper` / `*Util` classes (and `*Hook` when it is not an actual hook) holding **static methods** for data manipulation or orchestration are anti-patterns.

- **Validation at the boundary (Anti-Corruption Layer):** never let generic payloads (e.g. `Map<String, Object>`) leak into business logic. Convert infrastructure messages into validated **Records / Value Objects** at the entry point, through a Mapper or Adapter.
- **Smart payloads:** data extraction and validation are carried by the objects themselves (encapsulation), not by external utility methods.
- **Domain services:** rules that need dependency injection (e.g. model resolution via Spring beans) live in injectable services (`@Service` or domain components) — never in static methods that take services as parameters.
- **What this rule targets:** static methods that take **service beans** as parameters, that **leak or hand-roll `Map<String, Object>`** inside business logic, or that perform **stateful orchestration / I/O** better expressed as an injectable component.
- **Sanctioned exceptions** (not generic helpers): (a) the `final class *Mapper` (static, package-private, dependency-free) of [ERR-107]; (b) **dependency-free pure functions** — stateless math, string/formatting, or extraction over a third-party type you cannot extend — which carry no coupling worth mocking and would only be duplicated by inlining; (c) a **reflective decoupling adapter** that deliberately avoids a compile-time dependency (e.g. on the security starter).

---

## 5. Persistence (CQRS)

Two bounded contexts, **no JPA entity outside `orazaka-persistence`**:

- `persistence-app`: AI state (chat, jobs, models, configs, **outbox**), sub-packs `command/` `query/` `outbox/` `cache/`.
- `persistence-identity`: identity **and profile** (`UserProfile`, preferences, RBAC). `app` references the user only by an opaque `ActorId` — **no cross-context FK**.

DB invariants **[ERR-109]**: no raw SQL (parameterized Repos or `@Query`); no read-before-write (catch `DataIntegrityViolationException`); hash/crypto **outside** `@Transactional`; write methods return domain records; no N+1 (`LEFT JOIN FETCH` / `@EntityGraph`).

**DB bootstrap (local phase)**: **one file per bounded context, in the repository that owns the context** — `krizaka-users/infra/initdb/10-identity.sql`, `orazaka-conversation-service/…/20-conversation.sql`, `orazaka-job-service/…/30-jobs-config.sql`, `orazaka-knowledge-service/…/40-knowledge.sql`, `orazaka-automation-service/…/50-automation.sql`, `orazaka-ai-engine/…/60-governance.sql`, `krizaka-billing/…/70-billing.sql`, `orazaka-studio/…/80-studio.sql`; this workspace keeps only `infra/initdb/00-reset.sql`; dev data is the dev-reset dataset (`scripts/dev-reset.mjs`, `tools/seed-dev.sh`), never an initdb fixture (the tables are created by each service's Flyway baseline, krizaka-internal#2255). Schema + tables + **dev seed data**, applied alphabetically via psql: `infra/docker-compose.yml` mounts each file into the ONE Postgres container. Contexts already cut over run in their **own database with their own role**, created by their initdb file (`10-identity.sql` → `krizaka_users_db` / role `krizaka_users`); the rest still share `orazaka_db` until their phase. **Cross-context FKs are banned** (opaque ids only), enforced on every build by `SqlBoundaryRules` [SEAM-001]; cross-context seeds are banned too (each context seeds only its own database). `infra/init-prod.sql` is **deferred** (nothing in prod for now) — we do it once the product is finished.

---

## 6. Messaging (RabbitMQ)

RabbitMQ is the **decoupling buffer**, not a database appendage.

> This section described the plane as it was two phases ago: it named the router as the producer of
> async jobs (deleted in ADR-068), `business`/`core` as producers (neither has ever touched the
> broker), and three consumers by class names that do not exist (`JobEventRelay`,
> `WorkflowEventConsumer`). It was the **fifth** text in this repository to assert something false
> about a control, and the first that is normative — every run loads this file first. Rewritten
> against the code (ADR-069); keep it that way by reading `docs/_generated/architecture.json`, which
> is derived from the code and is where the lists below come from.

- **Every job command has a run behind it** (ADR-068). The studio service's **transactional outbox**
  is the only producer of `job.*` work: `AmqpStepExecutionAdapter` appends the command in the saga's
  own transaction and `OutboxRelay` publishes it. There is no HTTP endpoint that submits a job.
- **Other producers**, each with the one thing it publishes: the **automation service** dispatches
  `job.agent.dispatch.{userId}` to a user's own CLI agent and emits `evt.automation.telemetry`; the
  **conversation service** releases `job.automation.approved` when a human approves an automation
  job that already exists, and emits `evt.agent.presence` / `evt.agent.result.{…}`; the **job
  service** emits a job's own lifecycle and `evt.capability.changed`; the **studio service** emits
  `evt.studio.run.{started,succeeded,failed}`; **billing** emits entitlement invalidations. The
  write-side `EventPublisher` publishes through the **persistence outbox**, which carries the
  exchange and routing key on the row rather than choosing them (AGENTS.md §5).
- **Consumers**: the **job service** drains the two job lanes and their DLQs; the **studio service**
  consumes job outcomes, connector outcomes, capability and subscription changes; **billing**
  consumes settlements and unmetered turns; the **conversation service** relays job events to the
  client over **SSE** (`JobEventListener` on `orazaka.events.job-relay`, bound `job.#`) and consumes
  wallet events; **automation** consumes its own queue; **notifications** consumes the two identity
  notification queues (`evt.user.*`, `evt.password.*`) and `krizaka.notifications.requests`
  (`evt.notification.requested`) and delivers per channel (e-mail, SMS, webhook);
  **knowledge** consumes RAG indexing. **No one calls a worker directly.**
- **Two lanes, not priorities** (ADR-067): `orazaka.jobs.interactive` (`job.text.*`,
  `job.media.analyze`) and `orazaka.jobs.batch` (`job.media.generate`), each with its own consumer
  pool and its own ceiling. A capability declares which lane it belongs to with `latency_class`
  (`INTERACTIVE | BATCH`, default BATCH), and [LANE-001] fails the build when the declaration
  contradicts the queue its routing key feeds. Lanes do not create a second accelerator; they stop a
  67-second image from sitting in front of a 200 ms text step.
- Conventions: exchanges `orazaka.jobs` and `orazaka.events` (topic) plus `orazaka.dlx` (direct, the
  dead-letter target). Routing keys are `job.{capability}.{action}` and `evt.{aggregate}.{type}`,
  with a **trailing segment where the key addresses one subject** (`job.agent.dispatch.{userId}`,
  `evt.agent.result.{jobId}`). A job's own progress travels on `orazaka.events` as
  `job.{jobId}.progress|done|error` — the events exchange carries both grammars, which is why the
  relay queue binds `job.#` there and not on the jobs exchange. **DLQ** `<queue>.dlq`, exponential
  backoff (`multiplier: 2.0`), **idempotency** by `messageId` — claimed before the work and
  **released when the handler throws**, so a redelivery after a failure is not silently dropped.
- **Sync vs async rule**: *broker = heavy / deferred / event-driven*; *synchronous = low-latency
  interactive*. **Interactive chat streaming stays synchronous** (token-by-token SSE), **never**
  through the broker — `orazaka.core.chat.completion` is in the same registry as image and video and
  is not a run.

---

## 7. Interceptor pipeline (`orazaka-interceptors`)

One module, packs by concern: `security/` `token/` `context/` `translation/` `enrichment/` `reformulation/` `tooling/` `validation/` `governance/`.

- Single interface: `PromptContextInterceptor` (enriches / routes / may **short-circuit**). `governance/` drives conditional activation (predicates).
- **Kill-switches**: `orazaka.security.disable-ai=true` fails any interceptor with `isAiDependent()==true` — it gates interceptors, not the model call itself. `orazaka.core.orchestration.enabled=false` (`ORCHESTRATION_ENABLED`) **refuses** every engine turn (`PipelineDisabledException` → `503` / `PLATFORM_UNAVAILABLE`). It never bypasses the pipeline: a bypass would skip the crisis guard, the scope guard, the credit hold and the `disable-ai` gate and still call the model (ADR-062).
- **Failure posture is declared** (ADR-064): an interceptor that is a control returns `failsClosed() == true`, and its unhandled failure stops the turn; an enrichment's degrades it. A control that chooses to survive a dependency's failure handles it inside itself and says so on the class — the credit gate fails open and records every turn it serves without a hold.
- **Caller data never shares a key space with trusted context** (ADR-064): a `Context` carries platform declarations under `orazaka.` and what the user wrote under `preference.`; the pipeline merges only those two namespaces. Reordering statements is not a fix.
- Modes: `orazaka.core.orchestration.routing.mode = DETERMINISTIC | AGENTIC`.

---

## 8. Frontend · Mobile · CLI

All clients live under `apps/` — one folder each, one pnpm workspace (`pnpm-workspace.yaml`), each app with its own lockfile so a bump in one never moves another's pins. **No client outside this folder.** Shared packages are `@krizaka/*` on npm (public, with provenance). The Krizaka brand layer (marks, motion signature) is [`@krizaka/ui`](https://github.com/krizaka/krizaka-ui); the Sovkora design system and shared types (`@krizaka/orazaka-design-system`, `@krizaka/orazaka-shared`) move into krizaka-ui (ADR-029).

| App | Role | Port |
|:---|:---|:---:|
| `apps/web` | Next.js 16 client (App Router), with the operator console on the `admin.*` host | 3000 |
| `apps/mobile` | Expo SDK 57 | 8081 |
| `apps/desktop` | Sovkora for Mac (Tauri 2) | — |
| `apps/cli` | dev CLI + offline SQLite queue | — |

- **Design system**: web components shared by `web-client` (its operator console included) in `orazaka-design-system`. **Banned**: duplicating components between the console and the rest of web-client, or putting React components in `orazaka-shared` (which stays pure contracts + tokens). **Mobile** has its own RN components but imports the **same tokens** from `shared`.
- **BFF mandatory for the browser**: a web page **never** hits the router (`:8080`) or Ollama (`:11434`) directly. All browser traffic proxies through Next.js server API routes.
- **Native clients go to the edge**, not through the BFF: `orazaka-mobile-client` calls `:8088` with `Authorization: Bearer`. The BFF authenticates with a **next-auth session cookie**, which a native app can neither obtain nor carry, so routing it there would mean building a second, native-only auth path inside a component that exists to serve the browser. The edge already exchanges keys for JWTs (`ApiKeyExchangeFilter`) and every service behind it enforces its own security — the guarantee the BFF rule protects (no client reaches a service unauthenticated) is upheld by the edge, not weakened. The rule for the browser is unchanged and non-negotiable.
- Types: always via `@krizaka/orazaka-shared` (`"@krizaka/orazaka-shared": "*"`). Ban interface duplication and cross-imports between client packs.
- Design: cinematic dark-mode; no inline hex or hardcoded Tailwind color classes. Max **250 lines/file** for `.tsx`. Dates via `date-fns` only **[ERR-108]**. Input-blocking when `isSending || isGenerating` **[ERR-126]**. Centralized Lucide icons (`Icon.tsx`).
- **The 250-line cap is a component rule, and `*.registry.tsx` is exempt from it.** A registry of 84 icon paths is not a component, and splitting one into four files of 175 lines satisfies the letter while making a reader guess which file holds the icon they want. The exemption is narrow **by construction**: a `*.registry` file may hold any amount of data and **no component** — `no-restricted-syntax` fails on a function declaration there, so a registry that grows behaviour stops being exempt. Enforced by `max-lines` in each workspace's ESLint config.
- **Every UI workspace must run ESLint**, not only a type-check. `orazaka-design-system` ran `tsc --noEmit` as its `lint`, which counts nothing — which is why `icon.tsx` reached **841 lines** against this very cap without one warning. A rule written in three configs and absent from the fourth is enforced in three places.

---

## 9. Testing

- **No test, not done.** Every feature and every fix ships with its tests in the same pull request, in every
  module: a unit test for logic, a Testcontainers integration test for an adapter or an endpoint,
  a hermetic E2E journey for a flow, a render test for a UI component (web: Jest + Testing Library, mobile: `jest-expo`).
- **A fix starts with a failing test** that reproduces the bug; the fix turns it green and the test stays as the guard.
- **CI runs them on every pull request** (the one CI: the changed modules and their dependents; the merge queue runs
  everything) and must be green before merge. A test is never skipped, `@Disabled`, weakened or deleted to get there.
- **Pyramid**: unit (per module) → integration (**Testcontainers**, `AbstractContainerIntegrationTest`, singleton per JVM, ports via `@DynamicPropertySource`) → **hermetic E2E** (`e2e/sovkora-e2e`).
- **ArchUnit**: `GovernanceTest` validates the ring rules on **every build**. Before a push: `./mvnw test -pl engine/sovkora-engine-core -Dtest=GovernanceTest`.
- In the local phase, **hermetic E2E is the quality gate** (no staging/prod). `orazaka test e2e`.
-

---

## 10. Documentation generation (local, automated, from code)

- **Docs come from code.** **Full** automation, wired into the **build**: no hand-maintained architecture doc that can drift.
- `orazaka docs build`: generates, from the code, **(a)** markdown (catalog of `UseCaseDescriptor`, ADR ledger, interceptor registry, interface contracts, **API reference, governance-rule registry**, CLI reference, model catalog, and the guides to the business layer, tools & MCP, jobs/schedules/automation, packs and administration) **and (b)** a **structured** architecture model (`docs/_generated/architecture.json`: modules, ports, dependencies, pipeline, messaging) extracted via ArchUnit/annotation scan. The build fails if the model is stale (`docs build --check`).
- **The API reference carries the authorisation rule, not only the path.** Access is resolved per endpoint from each service's `SecurityConfig` in **declaration order** — Spring Security is first-match-wins, and ranking matchers by specificity inverts the answer. An endpoint no matcher covers is rendered **⚠ no rule**, which is a finding rather than a formatting gap: it means the service has no security on that path at all.
- **The governance registry lists which suite enforces each rule.** A rule with no suite enforces nothing, and that must be visible without reading the test tree. Every `assert*` in `orazaka-test-support` appears, summarised by its own javadoc — so a rule is documented by whoever writes it.
- **Docstrings are the generator's input, not decoration.** A controller method's first javadoc sentence becomes its API summary; a rule's becomes its registry entry. An undocumented public surface generates an empty cell.
- **Schemas = structured components fed by the model, never Mermaid.** On the Krizaka site every architecture schema is a 2D component of `krizaka-com` driven by `architecture.json` (module map with its build/runtime toggle and journeys, interceptor pipeline, messaging topology, the Krizaka building-blocks map from `buildingBlocks`). A doc that needs a schema writes a fenced block the site draws: ` ```architecture ` (`product`, `focus`, `mode`, `journey`), ` ```pipeline `, ` ```sequence ` (participants, ideally `@module` ids checked against the model, then numbered steps) or ` ```states `. A ` ```mermaid ` fence in a published doc fails the site build.
- `.github/workflows/docs-sync.yml` keeps `docs/_generated` in step daily; `apps/web/scripts/sync-docs.mjs` copies the docs the site serves.
- When changing interfaces, config keys, `infra/initdb/*.sql`, env vars: run `node scripts/generate-docs.mjs` and commit `docs/_generated`. Generated docs are **never** hand-edited.

---

## 11. Security

- Secrets in local `.env` (never committed). Verification/reset tokens **SHA-256 hashed**, single-use, 15-min expiry. Passwords: BCrypt.
- M2M JWT on the router; RBAC resolved in `interceptors/security` via the `krizaka-users-core` ports.
- **Pinned upstream versions**: no `-SNAPSHOT` dependency as a rule (Spring Boot is pinned to the `4.0` GA line; Spring AI to `2.0.0` — see ADR-030 for the migration off the former `3.5.0` line). **Pinning a line means tracking its patches, not freezing on `.0`**: take `4.0.x` as it ships and leave `4.1` alone. One property bump also moves everything the BOM manages (JDBC driver, JUnit, Mockito, Lettuce, Micrometer…) to versions Spring tested together, which is why those must never be bumped individually — doing so overrides the BOM and is the churn this rule exists to prevent.
- **Audit, don't upgrade reflexively.** `npm audit` and `versions:display-property-updates` are the inputs; "a newer version exists" is not a reason. Never run `npm audit fix --force` in this repo: it proposes `react-native@0.72` (older than what is installed) and `expo@57` as "fixes", either of which would break the mobile client. Advisories reachable at runtime come first; build-time tooling can wait for the SDK migration that carries it.
-

---

## 12. Declared, not inferred

**When one component must know something about another's work, the other one says it. The receiver
never deduces it from a name, a shape, or a value that happens to be present.**

The test, for a case not listed below: *if this fact is wrong or absent, who finds out?* When the
answer is "nobody, and the bill or the behaviour is silently wrong", the fact must be declared by
the party who knows it — a marker, a typed field, a configuration key — and the receiver must fail
closed without it. A default is a declaration nobody made.

Four independent problems resolved the same way, which is why this is a rule and not a habit:

| Problem | The inference that failed | What is declared instead |
|:---|:---|:---|
| **Metering** [BILL-001] | the interceptor would have sniffed for a `runId` to know a turn was already metered — silently unbilling every future producer that carried one | the producer stamps `orazaka.metering.deferred` and is accountable for it (ADR-044, ADR-045) |
| **Enrichment namespace** | the engine enriched one hardcoded prefix, so a pack could not have a context of its own without editing an engine library (ADR-049 §6) | the producer declares `orazaka.enrichment.namespace`; no declaration, no enrichment (ADR-050) |
| **Failure cause** *(closed)* | the saga read the executor's error prose — and a scope-guard **refusal** and a model **outage** arrived identical, so a SENSITIVE pack's trail could not tell "we protected" from "we broke" | the executor stamps a typed `cause` on `job.{id}.error`; absence reads as `EXECUTOR_FAULT`, which releases and blames nobody (ADR-053) |

**The corollary decides who a guard protects.** Where the two failure modes are asymmetric — one
party edits this repository in review, the other is outside it and cannot be asked to remember — the
guard fails closed against the party who is not in the room. That is why [PACK-002] became a
whitelist of engine namespaces: a new engine namespace costs its author one line in the same
change, and a new pack namespace has nobody to ask (ADR-050).

**What this does not license.** Declaring is not the same as trusting: a declaration is a claim its
author is accountable for, and one that decides money or access is still checked. `EntitlementInterceptor`
honours the metering marker and *still* runs the entitlement check; `MeteringMarkerRules` fails the
build for a producer that declares nothing. Declared, then verified.

**Repairing an inert control is a change, not a fix.** A control that has never been active has a
branch that has never executed. Trace that branch before making it reachable. Three of the inert
controls found in this codebase had an unexecuted branch behind them, and in one case — the
orchestration kill-switch (ADR-062) — repairing the binding alone would have created the
vulnerability the switch was believed to prevent. A bypass is the same thing from the other side:
removing the one that skipped the pipeline for a prompt carrying an image ran the refiner and the
router on images for the first time, and they were what had to change (ADR-063).

---

## 12.1 Static rules & codemods (OpenRewrite, ArchUnit)

The org code rules (addendum 2026-10-15) are checked by tools, not by review.

- **OpenRewrite** (`rewrite-maven-plugin`, configured once in `krizaka-parent`, inherited by every JVM repository):
  common static analysis, test best practices, Java 21 idioms, Spring idioms (no `@Autowired` on a constructor; `@Bean` methods stay public), no duplicate POM dependency. `./mvnw rewrite:dryRun` lists what would change and **fails** if
  anything would; `./mvnw rewrite:run` applies it — behaviour
  identical, run the tests, land it in a small pull request. A recipe that is wrong for this repository is excluded here
  (`<exclusions>` / a `rewrite.yml`), with the reason; test fixtures (`**/fixture/**`) are excluded everywhere.
- **ArchUnit** (shared rules in `krizaka-test-support`: `CodeRules`, `ConfigBindingRules`, `SourceRules`,
  `ArchitectureRules`): the governance tests of a module call them. `ArchitectureRules` holds the structure — layers
  (web → service → repository, none skipped, none calling up), no package cycle, `*Controller` / `*Service` /
  `*Repository` names, `@Value` and the environment read only in configuration, `@Transactional` only on services. The
  older `CodeRules` hold the rest (no field injection, no `System.out`, domain free of framework, one class per file…).
- **Debt only shrinks — `FrozenRules`** (ArchUnit `FreezingArchRule`): a rule a module cannot satisfy yet is checked with
  `FrozenRules.check(ArchitectureRules.xxxRule(...), CLASSES)`. The violations that exist are recorded in the module's
  `archunit_store/` (committed); a **new** violation fails, a fixed one leaves the store (commit it). A rule with no store
  fails: record a new rule's debt once with `-Darchunit.freeze.store.default.allowStoreCreation=true`, review the store,
  commit it. Never add a violation to a store by hand to get a build green.
- A rule is never weakened, skipped or deleted to get a PR green (AGENTS §5.3 of the org); fix the code or freeze the existing debt.

- Sovkora's own governance (`orazaka-test-support`: `GovernanceRules`, hexagonal boundaries, pack coherence…) stays the contract; its structure rules (`StructureRules`) freeze what a service cannot satisfy yet.

## 13. Agent session protocol

1. **Load first** this contract. Never generate code that violates a rule or an ArchUnit rule.
2. For "how does X work / where is X", prefer **structured search** (the codegraph index, ripgrep) over repeated file reads.
3. **Before declaring a task done**: the module's own check is green (README « Run it »), and if interfaces, config or migrations changed, `node scripts/generate-docs.mjs` has run.
4. **Commits.** One repository: a change that spans modules is one pull request, green in the one CI. The parent POM and every user of it move in the same change.
5. **Local-first runtime**: never introduce cloud deployment or an unsolicited network dependency; CI is limited to what §0 allows.

---

*Agent-neutral contract. Imported as-is by `CLAUDE.md` via `@AGENTS.md`. Last rewrite: 2026-11-08 (ADR-029: one repository, folders by role, the Java naming convention).*

## Before you open or update a pull request (coding agents)

These are the defects code review found most often on 2026-10-23. Check them yourself before you open or update a pull request:
1. **Checks:** run the repository's full check (lint, typecheck, tests, build) and fix it. Never push a red check.
2. **Concurrency:** any « count then insert » or « check then write » runs in one transaction with a lock, and has a concurrency test.
3. **One gate:** a business rule (limit, permission, quota) is enforced in the service, so no route can skip it. Test every route. Field validation stays in the record's compact constructor (§4), never copied into the service.
4. **Errors:** a cleanup (ROLLBACK, DROP, close) never masks the original error. Rethrow it, or throw an `AggregateError` with the original first.
5. **Validation:** validate the original input, never a truncated or pre-trimmed copy. Server and client reject the same inputs.
6. **Accessibility:**
   - every control has a unique label, and FR/EN pairs say their language;
   - focus rings are never clipped by `overflow`;
   - text never overflows its box at 360 px.
7. **Naming:** environment variables follow `<CONTEXT>_<THING>` (`APP_`, `DATABASE_`, `AUTH_`, `MEDIA_`, `MAIL_`, `PAYMENTS_`, `AI_`…) and are declared once.
8. **Words:** every user-facing string is in the FR and EN messages, and no colour is hard-coded.
9. **Review threads:** answer every one. Fix it test-first, or reply with the reason, then resolve it.

- **A bug means a missing guard** ([standard](https://github.com/krizaka/.github/blob/main/docs/standards/bug-means-missing-guard.md)): the fix PR first adds a guard that fails on `main`, then fixes the bug; the bug issue says why the guards missed it.

**Repository layout:** the root follows [krizaka/.github `docs/standards/repo-layout.md`](https://github.com/krizaka/.github/blob/main/docs/standards/repo-layout.md); the CI guard `check-repo-layout` fails on a stray root file, and a justified extra is declared in `.repo-layout.json` with its why.

- **React effects**: `useEffect` is for subscriptions and object URLs only; data is `useQuery`/`useMutation` from `@krizaka/query`, derived state is computed in render. [Standard](https://github.com/krizaka/.github/blob/main/docs/standards/react-effects.md).

## 9. Anti-slop (ADR-015)

What a model tends to write and the code does not need. Each line points at the rule that refuses it
([ADR-015](https://github.com/krizaka/krizaka-internal/blob/main/docs/adr/.github/ADR-015-ai-code-audit-measured-and-enforced.md), [docs/standards/guards.md](https://github.com/krizaka/.github/blob/main/docs/standards/guards.md)).

1. **No defensive null on `@NullMarked` code.** No `Objects.requireNonNull` or `if (x == null)` on a value the type already
   guarantees. Enforced by NullAway at compile time; a new package is `@NullMarked` (ArchUnit).
2. **A closed set is a type.** An enum, a sealed interface or an `as const` object, never a string compared by hand;
   strategies are a `Map<Kind, Strategy>` built once at startup. Enforced by PMD / ArchUnit (Java) and `no-enum-string-compare` (ast-grep).
3. **HTTP in Java is a DTO record, `@Valid` and `ProblemDetail`.** No `Map.of("error", …)`, no entity in a response, one
   `@RestControllerAdvice` per service. Enforced by ArchUnit.
4. **No `*Utils`, `*Helper` or `*Manager`.** Name the thing by what it does (§4). Enforced by ArchUnit (ADR-015 §5).
5. **`?.` and `??` in TypeScript**, not a chain of `&&` or a ternary on null. The target rules are `@typescript-eslint/prefer-optional-chain` and `prefer-nullish-coalescing`; they are not in `@krizaka/config` yet.
6. **The build is the judge.** Compile, lint and test before every push; `krizaka-edit-check` runs on each edit (below) and
   NullAway and ArchUnit run at pre-push and in CI. A rule is never silenced to get green.

**The edit hook.** Every repository adds this to `.claude/settings.json`. After each `Edit` or `Write`, the one file is
checked in under 10 s: ESLint with the repository's config for TypeScript, PMD with the Krizaka rules for Java (never Maven).
A finding comes back to the agent (exit 2); a timeout is silent.

```json
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Edit|Write",
        "hooks": [{ "type": "command", "command": "npx --yes -p @krizaka/config krizaka-edit-check", "timeout": 10 }]
      }
    ]
  }
}
```

In a TypeScript repository that already depends on `@krizaka/config`, use `npx --no-install krizaka-edit-check` (no download).
