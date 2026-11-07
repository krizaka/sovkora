---
title: "Sovkora — Interface contracts (ports & adapters)"
description: "All interfaces for the interactions between layers: transport, business, core, interceptors, identity, persistence (split), messaging, and UI tier (client/admin/mobile/cli)."
category: Architecture
order: 2
---

# Sovkora — Interface contracts

> This document freezes **the interfaces of all interactions**. Each interface is tagged by its hexagonal role:
> **[IN]** inbound port (what a layer offers) · **[OUT]** outbound port (what it needs, implemented elsewhere) · **[SPI]** extension point · **[DTO]** immutable data · **[ADAPTER]** implementation.
>
> Golden rule: a port is **defined in the module that needs it** (the core) and **implemented in an outer module**. The `core` depends on no implementation.
>
> See also: [VISION_ARCHITECTURE.md](VISION_ARCHITECTURE.md) (layers & flows).

---

## 0. Naming decision — `orazaka-interceptors` vs `orazaka-hooks`

**Recommendation: `orazaka-interceptors`.**

| Criterion | `interceptors` | `hooks` |
| :--- | :--- | :--- |
| Semantics | an **ordered** chain that can **inspect, modify, short-circuit** a request in transit | **lifecycle** extension points (react at a moment), order/flow implicit |
| Our case | a deterministic/agentic pipeline that **enriches and routes** the `PromptContext`, with short-circuit (kill-switch) | does not capture the notion of an ordered pipeline |
| Ambient vocabulary | aligned with Spring (`HandlerInterceptor`) / Spring AI (`Advisor`) | "hooks" **collides with React hooks** (present in the same monorepo) |
| Connotation | precise, "enterprise" | light, but vague on order and transformation |

A `hook` describes a **lifecycle callback**; our module is an **ordered, transforming, short-circuitable pipeline** — exactly an *interceptor*. We keep `orazaka-interceptors`, and reserve the word "hook" for genuine lifecycle extension points if the need arises. (Possible but rejected alternatives: `advisors` — too tied to Spring AI; `middleware` — web/HTTP connotation.)

---

## 1. Persistence split (bounded contexts)

`orazaka-persistence` stays split into **two contexts**, each containing only **adapters** that implement **outbound ports** defined by the domain. **No JPA entity outside this module.**

```
orazaka-persistence/
├── app/        # AI application state
│   ├── command/   (aggregates + WRITE repositories)    ── implements the *Repository ports (core/business)
│   ├── query/     (projections + READ repositories)     ── implements the *ReadModel ports
│   ├── outbox/    (outbox table + relay)                ── implements OutboxStore
│   └── cache/     (Caffeine L1 + Redis L2)              ── implements *Cache
└── identity/   # identity & profile
    ├── command/   (User, Profile, Credential, Token …)  ── implements the *Repository ports (identity)
    ├── query/     (profile / RBAC views)
    └── cache/     (sessions, rate-limit counters)
```

| Context | Owns (entities) | Implements (outbound ports, §6) |
| :--- | :--- | :--- |
| **persistence-app** | ChatSession, Job, CatalogModel, AiProvider, AiRagStore, Mcp/Tool config, Pipeline/Validation/Policy config, **Outbox** | `ChatSessionRepository`, `JobRepository`, `JobReadModel`, `ModelCatalogRepository`, `PipelineConfigProvider`, `ToolConfigProvider`, `OutboxStore`, … |
| **persistence-identity** | User, **UserProfile**, Credential, Authority, VerificationToken, PasswordResetToken, RateLimit(+Tier), UserInterception | `UserRepository`, `ProfileRepository`, `CredentialRepository`, `TokenRepository`, `AuthorityRepository`, `RateLimitRepository` |

> The **profile** (`UserProfile`, preferences, language, display RBAC) lives in **persistence-identity** (it is an attribute of identity), not in `app`. `app` references the user only by an opaque `ActorId` — no cross-context FK, coupling broken.

---

## 2. Shared kernel — `Intention` & shared types  **[DTO]**

The *shared kernel* (module `orazaka-business/api`, re-exported on the TS side in `orazaka-shared`).

```java
public record Intention(
    Actor        actor,        // resolved identity + RBAC roles
    IntentionType type,        // COMMAND | QUERY            (CQRS pivot)
    Capability   capability,   // CHAT | IMAGE | AUDIO | VIDEO | AGENT | ADMIN
    ExecutionMode mode,        // SYNC | ASYNC               (offload)
    String       goal,         // business intent
    Payload      payload,      // sealed: ChatPayload | ImagePayload | AgentPayload | …
    IntentionContext context   // sessionId, preferences, env signals
) {}

public record Actor(ActorId id, Set<Role> roles, Tenant tenant) {}
public enum IntentionType { COMMAND, QUERY }
public enum ExecutionMode { SYNC, ASYNC }
public enum Capability { CHAT, IMAGE, AUDIO, VIDEO, AGENT, ADMIN }
public record JobHandle(JobId id, JobStatus status, URI eventsStream) {}  // return of an async submit
```

---

## 3. UI tier — client · admin · mobile · cli

All **four** front-ends consume the **same** contract via `orazaka-shared`, through the **BFF**. None talks to the router directly.

### 3.1 Unified client contract  **[IN on the TS side]** — `orazaka-shared`

```ts
export interface OrazakaClient {
  auth: AuthApi;                                   // login / refresh / logout
  chat(req: ChatRequest): AsyncIterable<ChatChunk>; // token-by-token SSE streaming (sync)
  intentions: IntentionApi;                         // submission + tracking (sync or async)
  jobs: JobApi;                                     // status & events of async jobs
  catalog: CatalogApi;                              // available models/capabilities
  admin?: AdminApi;                                 // present only if ADMIN role
}

export interface IntentionApi {
  submit(i: Intention): Promise<JobHandle>;         // 202 + jobId for heavy ones
  run<R>(i: Intention): Promise<R>;                 // short sync
  events(id: JobId): JobEventStream;                // SSE: progress → done
}

export interface JobEventStream {                   // SSE abstraction
  on(event: "progress" | "done" | "error", cb: (e: JobEvent) => void): void;
  close(): void;
}

// Zod schemas (runtime validation, source of truth for the types)
export const ChatRequestSchema = z.object({ … });
export type ChatRequest = z.infer<typeof ChatRequestSchema>;
```

### 3.2 Role & surface of each front-end

| Front-end | Surface | Consumed interfaces | Specific |
| :--- | :--- | :--- | :--- |
| **web-client** | end user (chat, playgrounds, job tracking) | `chat`, `intentions`, `jobs`, `catalog`, `auth` | SSE streaming, input-blocking |
| **web-client** (operator console, `admin.*`) | SecOps (models, pipeline, features, jobs, compliance) | + `admin: AdminApi` | ADMIN role required |
| **mobile-client** | subset of web-client | `chat`, `intentions`, `jobs`, `auth` | Expo, light offline |
| **cli** | developer (chat/agent/image, scripts) | all of `OrazakaClient` | **`OfflineJobQueue`** (SQLite) |

### 3.3 BFF — server proxy contract  **[ADAPTER]** (Next.js, server side)

| BFF route (Next.js server) | Delegates to (router) | Role |
| :--- | :--- | :--- |
| `POST /api/intentions` | `POST /v1/intentions` | submits an intention (injects Bearer) |
| `GET /api/intentions/:id/events` | `GET /v1/jobs/:id/events` (SSE) | SSE event relay |
| `POST /api/chat/stream/:sid` | `POST /v1/chat/stream/:sid` (SSE) | chat streaming |
| `POST /api/graphql` | `POST /v1/graphql` | GraphQL queries |
| `/api/auth/*` | `/v1/auth/*` | login/refresh (NextAuth) |
| `/api/admin/*` | `/v1/admin/*` | admin console (RBAC guard) |

### 3.4 CLI — offline job queue  **[IN]**

```ts
export interface OfflineJobQueue {            // offline resilience (local SQLite)
  enqueue(i: Intention): Promise<LocalJobId>;
  pending(): Promise<LocalJob[]>;
  sync(client: OrazakaClient): Promise<void>; // replays to the backend when available
}
```

---

## 4. Router (ingress) ⇄ Business

The router is an **inbound adapter**: it translates transport into an `Intention` and calls business. It exposes no business interface; it **consumes** the inbound ports of business.

```java
// orazaka-business · api   [IN]  — called by the router's REST/SSE/GraphQL adapters
public interface UseCaseDispatcher {
    <R> R          dispatch(Intention intention);   // QUERY or short sync COMMAND
    Flux<JobEvent> stream(Intention intention);     // sync streaming (relayed over SSE)
    JobHandle      submit(Intention intention);     // ASYNC → publishes a job, returns 202+jobId
}
```

On the router side, the purely technical **inbound** (for reference, not a business port):

```java
// orazaka-router · infrastructure.adapter.rest   [ADAPTER]
// IntentController:  HttpRequest → Intention → UseCaseDispatcher
// JobEventController:   GET /v1/jobs/{id}/events → SseEmitter ← JobEventRelay (§7)
```

---

## 5. Business (App Factory) — orchestration  **[IN] / [SPI]**

```java
// [SPI] — extension point: adding a product = implement UseCase + declare a descriptor
public interface UseCase<I extends UseCasePayload, R> {
    UseCaseDescriptor descriptor();
    R execute(UseCaseContext ctx, I input);           // orchestrates core capabilities
}

public interface UseCaseRegistry {                    // [IN] auto-discovery (component scan / ServiceLoader)
    Optional<UseCase<?,?>> resolve(Intention intention);
    Collection<UseCaseDescriptor> all();
}

public record UseCaseDescriptor(                      // [DTO] metadata (metadata-driven)
    String id, Capability capability, Set<String> personas,
    RoutingMode routing,            // DETERMINISTIC | AGENTIC (for delegated planning)
    Set<String> requiredTools, RbacPolicy rbac) {}

// Workflow orchestration (saga) — reacts to domain events
public interface WorkflowOrchestrator {               // [IN], triggered by WorkflowEventConsumer (§7)
    void onEvent(DomainEvent event);                  // advances the DAG / saga
}
```

> Reminder: for a **dynamic** workflow, `UseCase.execute` **delegates planning** to the core's `AiClient.agent(...)` (§6); business runs the plan, it does not host the intelligence.

---

## 6. Core — cognition  **[IN] (offers) / [OUT] (needs)**

### 6.1 Inbound ports (what the core offers)

```java
public interface AiClient {                           // [IN] single facade
    ChatResponse        chat(ChatRequest r);
    Flux<ChatResponse>  stream(ChatRequest r);
    ImageResponse       image(ImageRequest r);
    AudioResponse       audio(AudioRequest r);
    VideoResponse       video(VideoRequest r);
    Flux<AgentEvent>    agent(AgentRequest r);         // agent capability: plan→act→observe loop
}

public interface ChatSessionService { … }             // [IN] conversation sessions
public interface JobService {                          // [IN] async job lifecycle
    JobHandle        enqueue(JobCommand c);
    JobStatus        status(JobId id);
    Flux<JobEvent>   events(JobId id);
}
public interface CatalogModelService { List<ModelInfo> available(Capability c); }  // [IN]
public interface McpService { … }                     // [IN] MCP orchestration
```

### 6.2 Outbound ports (what the core needs — implemented elsewhere)

```java
// Generation (implemented by the Provider Mesh providers → native runtimes)
public interface ChatGeneratorClient  { ChatResponse generate(Prompt p); Flux<ChatResponse> stream(Prompt p); }
public interface ImageGeneratorClient { ImageResponse generate(ImagePrompt p); }
public interface AudioGeneratorClient { AudioResponse synthesize(AudioPrompt p); }
public interface VideoGeneratorClient { VideoResponse render(VideoPrompt p); }   // → media worker (async)

// Knowledge & tools (implemented by orazaka-tools)
public interface KnowledgeService     { List<Chunk> retrieve(RagQuery q); void index(Document d); } // RAG
public interface ToolRegistry         { List<ToolCallback> resolve(ToolScope scope); }
public interface McpOrchestrator      { McpSession open(McpServerRef ref); }
public interface SandboxExecutor      { ExecResult run(ExecRequest r); }

// Context & routing (implemented by interceptor providers / identity)
public interface MemoryResolver       { List<Message> window(SessionId s, int n); }
public interface SemanticClassifierPort { ResolvedIntent classify(String prompt); }  // agentic routing
public interface UserContextProvider  { UserContext resolve(ActorId id); }           // ← identity
public interface ModelCatalogProvider { List<ModelEntry> catalog(); }
public interface PipelineConfigProvider { List<InterceptorRef> ordered(RoutingMode m); }

// State (implemented by persistence-app)
public interface ChatSessionRepository { … }          // write
public interface JobRepository         { … }          // write
public interface JobReadModel          { JobView byId(JobId id); }                   // read (projection)

// Messaging (implemented by persistence-app/outbox + AMQP adapter)
public interface EventPublisher  { void publish(DomainEvent e); }     // via transactional outbox
public interface JobDispatcher   { void dispatch(JobCommand c); }     // publishes a job command
```

### 6.3 `agent` capability  **[DTO]**

```java
public record AgentRequest(SessionId session, String goal, Set<String> allowedTools, int maxSteps) {}
public sealed interface AgentEvent permits Planned, Acting, Observed, Finished, Failed {}
//  Planned(List<Step>) · Acting(ToolCall) · Observed(ToolResult) · Finished(Result) · Failed(Error)
```

---

## 7. Interceptors (pipeline) — `orazaka-interceptors`  **[SPI]**

```java
public interface PromptContextInterceptor extends Ordered {   // [SPI] one filter of the pipeline
    PromptContext apply(PromptContext ctx);                   // enriches / routes / may short-circuit
    boolean       isAiDependent();                            // kill-switch gate
    default boolean supports(Intention i) { return true; }
}

public interface InterceptorPolicy {                          // [SPI] conditional activation (governance)
    boolean isActive(PromptContext ctx);
}
```

Internal packs (one module, boundaries by concern):
`security/` `token/` `context/` `translation/` `enrichment/` `reformulation/` `tooling/` `validation/` `governance/`.

---

## 8. Identity — cross-cutting security  **[IN] / [OUT]**

```java
// [IN] — used by the router (filters) and the security/ interceptor
public interface AuthenticationService {
    AuthResult authenticate(Credentials c);
    TokenPair  refresh(RefreshToken t);
    void       logout(SessionId s);
}
public interface UserService {
    UserId register(Registration r);
    void   verify(VerificationToken t);
    void   requestPasswordReset(Email e);
    void   resetPassword(ResetToken t, Secret newSecret);
}
public interface ProfileService {                    // ← "profile" lives here
    UserProfile get(UserId id);
    UserProfile update(UserId id, ProfilePatch p);
}
public interface AuthorizationService { boolean can(Actor a, Permission p); }  // RBAC

// [OUT] — implemented by persistence-identity
public interface UserRepository, ProfileRepository, CredentialRepository,
                 TokenRepository, AuthorityRepository, RateLimitRepository { … }

// [OUT] — messaging (implemented by the AMQP adapter)
public interface UserEventPublisher extends EventPublisher { }   // UserRegistered, PasswordReset…
```

---

## 9. Messaging (RabbitMQ) — contracts  **[OUT] / [IN]**

### 9.1 Producers  **[OUT]**

```java
public interface JobDispatcher  { void dispatch(JobCommand c); }   // router/business → heavy jobs
public interface EventPublisher { void publish(DomainEvent e); }   // write-side → events (outbox)
```

### 9.2 Consumers  **[IN]** (AMQP adapters)

```java
public interface JobConsumer        { void onJob(JobCommand c); }        // worker executes
public interface JobEventRelay      { Flux<JobEvent> subscribe(JobId id); } // router → SSE (relay)
public interface WorkflowEventConsumer { void onEvent(DomainEvent e); }  // business → orchestration/saga
```

### 9.3 Conventions  **[DTO]**

```text
exchange   : orazaka.jobs   (topic)        |  orazaka.events (topic)
routing key: job.{capability}.{action}     |  evt.{aggregate}.{eventType}
job events : job.{jobId}.progress|done|error
DLQ        : <queue>.dlq  (exponential retry, idempotency by messageId)
```

```java
public record JobCommand(JobId id, Intention intention, int attempt) {}
public record JobEvent(JobId id, JobPhase phase, int step, int total, Object data) {}
public sealed interface DomainEvent permits UserRegistered, JobCompleted, SceneRendered, … {}
```

---

## 10. Boundary summary (who defines / who implements)

| Interface | Defined in | Implemented in | Type |
| :--- | :--- | :--- | :--- |
| `OrazakaClient`, `IntentionApi` | `orazaka-shared` (TS) | each front-end | IN (TS) |
| `UseCaseDispatcher` | business/api | business/core | IN |
| `UseCase`, `UseCaseRegistry` | business/api | business/usecases | SPI |
| `AiClient`, `JobService` | core/domain.ports.inbound | core/application | IN |
| `ChatGeneratorClient`, `KnowledgeService`, `ToolRegistry` | core/domain.ports.outbound | providers / tools | OUT |
| `EventPublisher`, `JobDispatcher` | core/domain.ports.outbound | persistence-app/outbox + AMQP | OUT |
| `PromptContextInterceptor` | core (SPI) | interceptors/* | SPI |
| `UserContextProvider` | core/domain.ports.outbound | identity | OUT |
| `AuthenticationService`, `ProfileService` | identity/domain.ports.inbound | identity/application | IN |
| `*Repository` (app) | core / business (OUT) | persistence-app | OUT |
| `*Repository` (identity) | identity (OUT) | persistence-identity | OUT |
| `JobEventRelay`, `WorkflowEventConsumer` | core/identity (OUT) | router / business + AMQP | IN/ADAPTER |

> All dependency arrows point **toward the core**: `core` depends only on its own ports; everything else (router, business, tools, identity, persistence, messaging, UI) is an **adapter** of it.

---

*Contracts frozen on 2026-06-08 — interface skeletons for implementation (Claude Code handoff). Indicative signatures: `…` types to be completed at implementation time.*
