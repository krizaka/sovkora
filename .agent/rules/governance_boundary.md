# Rule: System Layer Governance Boundary

## §1 Business vs. Core Separation of Concerns

The operational boundary between the **Business** and **Core** layers is non-negotiable and enforced at compile time by ArchUnit:

| Layer | Responsibility | Artifacts | Location |
|:---|:---|:---|:---|
| **Business** | **"What"** — intentions, rules, policies, business prompts | `WorkflowContext`, `.md` persona templates, interceptor policy sets | `engine/sovkora-engine-business/` |
| **Core** | **"How"** — execution mechanics, caching, fallback topology | `DynamicPipelineExecutor`, `.st` engine templates, `PromptContext` state machine | `engine/sovkora-engine-core/` |
| **Router** | Sole translation boundary between hexagons | `WorkflowAdapter` — maps business context to Core infrastructure types | `orazaka-apps/orazaka-router/` |

## §2 WorkflowContext Contract

- All business features requiring AI orchestration **must** declare intent via `WorkflowContext`.
- The `WorkflowOrchestrator` port interface is the **only** business-facing API for workflow execution.
- The `WorkflowAdapter` in `orazaka-router` is the **sole** adapter translating business context into Core infrastructure types.

**Banned**:
- Constructing `ChatRequest`, `Context`, or `PromptContext` directly from business code.
- Importing `com.krizaka.orazaka.core.*` types into `orazaka-business` source code.
- Bypassing the interceptor pipeline by calling `AiClient` directly from business logic.

## §3 Prompt Template Segregation

| Type | Location | Governance |
|:---|:---|:---|
| Engine templates (`.st`) | `orazaka-core/src/main/resources/prompts/` | Allow-listed: `context-envelope.st`, `system-refinement.st`, `system-router.st` only (ERR-125) |
| Business templates (`.md`) | `orazaka-business/src/main/resources/prompts/` | Unrestricted business persona templates |

Adding any `.md` file to `orazaka-core/src/main/resources/prompts/` will fail the ArchUnit resource isolation test.

## §4 ArchUnit Ring Rules

Agents must maintain **100% compliance** with `GovernanceTest` (a.k.a. `GlobalArchitectureGuardrailIT`). Key ring rules:

1. Core cannot import web starters, Spring Security context, or AMQP (ERR-102, ERR-118).
2. The `application.interceptor` package in core contains **only** the `PromptContextInterceptor` interface (ERR-122).
3. Records are pure data carriers — no loggers, I/O, Spring beans (ERR-135).
4. Core prompts directory is locked to the foundational engine allow-list (ERR-125).

**Pre-commit verification**: `./mvnw test -pl engine/sovkora-engine-core -Dtest=GovernanceTest`

## §5 Router Translation Namespace

When mapping `WorkflowContext` fields into `Context.preferences`, use these namespaces:

| Namespace | Purpose |
|:---|:---|
| `orazaka.pipeline.*` | Pipeline execution directives (contextId, forced/skipped interceptors) |
| `orazaka.user.*` | User-level attributes (tier, RBAC metadata) |
| `orazaka.user.meta.*` | Flattened business metadata entries |

## §6 Client Tier Workspace Boundary

**All** front-end and client-side applications reside under `orazaka-apps/ui/` as npm workspaces:

| Package | Role | Port |
|:---|:---|:---:|
| `orazaka-web-client` | Client-facing Next.js 16 App Router application | 3000 |
| `orazaka-web-client` (console, host `admin.*`) | Isolated SecOps Administration Console (the former `orazaka-web-admin`) | same app |
| `orazaka-mobile-client` | Expo SDK 53 cross-platform mobile app (6-screen SaaS boilerplate) | 8081 |
| `orazaka-cli` | Developer automation CLI with offline SQLite job queue | — |
| `orazaka-shared` | Shared TypeScript types + Zod validation schemas | — |

**Banned**:
- Creating standalone JS/TS client applications outside `orazaka-apps/ui/`.
- Duplicating TypeScript interfaces that exist in `orazaka-shared`.
- Importing types directly between client packs — always route through `orazaka-shared`.
- Using `workspace:*` protocol in `package.json` — use `"*"` for native npm workspace resolution.

**Mandatory**: All client packs must declare `"orazaka-shared": "*"` as a dependency and validate types against shared Zod schemas.
