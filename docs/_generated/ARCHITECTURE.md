---
title: Architecture Reference
description: Module map, dependencies, runtime flows, pipeline and messaging — extracted from the code.
category: Architecture
order: 2
generated: true
---

# Architecture Reference

Ports & Adapters: dependencies point toward the core. Each module has one **role** — one ring of the hexagon, outermost first — read from the code by the rule beside it.

| Role | Rule | Modules |
|:---|:---|:---|
| **Clients** | a UI repository of the workspace manifest (layer app, kind npm) | `cli`, `desktop`, `mobile`, `web` |
| **Services & workers** | a module with a main (SpringApplication.run), or a native worker | `sovkora-automation-service`, `sovkora-conversation-service`, `sovkora-edge-service`, `sovkora-job-service`, `sovkora-knowledge-service`, `sovkora-studio-service`, `worker-media`, `krizaka-billing-service`, `krizaka-notifications-service`, `krizaka-users-service` |
| **Adapters** | an imported module that is none of the below: persistence, typed HTTP clients, asset store, bridge | `sovkora-crypto`, `sovkora-engine-architecture`, `sovkora-engine-assets`, `sovkora-engine-persistence`, `sovkora-engine-persistence-bridge`, `sovkora-engine-springai`, `sovkora-studio-client`, `krizaka-billing-client`, `krizaka-payments`, `krizaka-quality`, `krizaka-rewrite-style`, `krizaka-users-client`, `krizaka-users-persistence` |
| **Application** | the orchestration, pipeline and capability libraries around the core (AGENTS.md §2: business, interceptors, tools) | `sovkora-engine-business`, `sovkora-engine-interceptors`, `sovkora-engine-tools` |
| **Domain core** | a `*-core` module — the centre of its hexagon, depending on no outer layer | `sovkora-engine-core`, `krizaka-users-core` |
| **Contracts** | a Tier-1 `*-api` module — pure interfaces and records | `sovkora-jobs-api`, `sovkora-persistence-api`, `sovkora-studio-api`, `krizaka-billing-api`, `krizaka-users-api` |
| **Krizaka service kit** | a module of krizaka-service-kit — the cross-cutting code with one author (ADR-073), starters included | `krizaka-app-config`, `krizaka-idempotency`, `krizaka-messaging`, `krizaka-notifications-api`, `krizaka-observability`, `krizaka-persistence`, `krizaka-ratelimit`, `krizaka-security`, `krizaka-spring-boot-starter-app-config`, `krizaka-spring-boot-starter-idempotency`, `krizaka-spring-boot-starter-observability`, `krizaka-spring-boot-starter-persistence`, `krizaka-spring-boot-starter-rabbitmq`, `krizaka-spring-boot-starter-ratelimit`, `krizaka-spring-boot-starter-security`, `krizaka-spring-boot-starter-web`, `krizaka-spring-boot-starter-webhooks`, `krizaka-web`, `krizaka-webhooks` |

## Modules

| Module | Role | Repository | Version | Inbound ports | Outbound ports |
|:---|:---|:---|:---|:---|:---|
| `cli` | client | `cli` | — | — | — |
| `desktop` | client | `desktop` | — | — | — |
| `mobile` | client | `mobile` | — | — | — |
| `web` | client | `web` | — | — | — |
| `sovkora-automation-service` | service | `automation` | 1.0.0-SNAPSHOT | — | — |
| `sovkora-conversation-service` | service | `conversation` | 1.0.0-SNAPSHOT | — | — |
| `sovkora-edge-service` | service | `edge` | 1.0.0-SNAPSHOT | — | — |
| `sovkora-job-service` | service | `job` | 1.0.0-SNAPSHOT | — | — |
| `sovkora-knowledge-service` | service | `knowledge` | 1.0.0-SNAPSHOT | — | — |
| `sovkora-studio-service` | service | `studio` | 1.0.0-SNAPSHOT | — | — |
| `worker-media` | service | `worker-media` | — | — | — |
| `krizaka-billing-service` | service | `krizaka-java` | 0.16.0 | — | — |
| `krizaka-notifications-service` | service | `krizaka-java` | 0.16.0 | — | — |
| `krizaka-users-service` | service | `krizaka-java` | 0.16.0 | — | — |
| `sovkora-crypto` | adapter | `contracts` | 1.0.0-SNAPSHOT | — | — |
| `sovkora-engine-architecture` | adapter | `engine` | 1.0.0-SNAPSHOT | — | — |
| `sovkora-engine-assets` | adapter | `engine` | 1.0.0-SNAPSHOT | — | — |
| `sovkora-engine-persistence` | adapter | `engine` | 1.0.0-SNAPSHOT | — | — |
| `sovkora-engine-persistence-bridge` | adapter | `engine` | 1.0.0-SNAPSHOT | — | — |
| `sovkora-engine-springai` | adapter | `engine` | 1.0.0-SNAPSHOT | — | — |
| `sovkora-studio-client` | adapter | `studio` | 1.0.0-SNAPSHOT | — | — |
| `krizaka-billing-client` | adapter | `krizaka-java` | 0.16.0 | — | — |
| `krizaka-payments` | adapter | `krizaka-java` | 0.16.0 | — | — |
| `krizaka-quality` | adapter | `krizaka-java` | 0.16.0 | — | — |
| `krizaka-rewrite-style` | adapter | `krizaka-java` | 0.16.0 | — | — |
| `krizaka-users-client` | adapter | `krizaka-java` | 0.16.0 | — | — |
| `krizaka-users-persistence` | adapter | `krizaka-java` | 0.16.0 | — | — |
| `sovkora-engine-business` | application | `engine` | 1.0.0-SNAPSHOT | — | — |
| `sovkora-engine-interceptors` | application | `engine` | 1.0.0-SNAPSHOT | — | — |
| `sovkora-engine-tools` | application | `engine` | 1.0.0-SNAPSHOT | — | — |
| `sovkora-engine-core` | domain | `engine` | 1.0.0-SNAPSHOT | AgentTurns, AiClient, CatalogModelService, ChatSessionService, CitationVerifier, ExternalCheckCapability, JobService, McpService, StepRouter | AccessAudit, AudioGeneratorClient, AudioTranscriber, CapabilityProvider, ChatGeneratorClient, ChatInvocation, ChatMemoryStore, CheckAuditLog, CheckHttp, CheckKeyStore, CheckMeter, CheckProviderCatalog, CheckSubjectPepper, CodeSandboxPort, ConsentRegistry, ControlVerdictRecorder, ConversationMemory, DomainEventPublisher, EmbeddingClient, ExternalCheckConnector, FeatureModelRoutes, ImageGeneratorClient, InfrastructureStatusProvider, KnowledgeService, McpOrchestrator, ModelCatalogProvider, ModelEndpointResolver, OutputJudge, PipelineConfigProvider, PlatformMcpServerProvider, PlatformToolConfigProvider, PromptAssembly, PromptCachePolicy, ProviderChatClient, SemanticClassifierPort, SpeechSynthesis, StepModelRecorder, StepRouteProvider, StructuredOutputBounds, StructuredOutputClient, TestShaperPort, ToolAnswerCodec, ToolLoopBounds, ToolOptions, ToolPermissionProvider, ToolRegistry, ToolResolution, ToolResultCache, UserCredentialsProvider, UserMcpServerProvider, ValidationPipelineRepository |
| `krizaka-users-core` | domain | `krizaka-java` | 0.16.0 | AdminUserService, ApiKeyService, CredentialGrantService, IdentityReconciliationService, IdentityService, LoginLockoutService, OrganizationService, PasswordPolicyService, PasswordRecoveryService, RateLimitProvider, SocialConnectionService, TokenService, UserProfileProvider | AdminUserRepositoryPort, ApiKeyRepositoryPort, AuthorityRepositoryPort, BreachedPasswordPort, CredentialGrantRepositoryPort, CryptographyPort, LockoutRepositoryPort, OAuth2ProviderVerifier, OrganizationRepositoryPort, PasswordEventPublisher, PasswordResetTokenRepositoryPort, SecretSealPort, SocialConnectionRepositoryPort, UserCredentialRepositoryPort, UserEventPublisher, UserInterceptionRepositoryPort, UserProfileRepositoryPort, UserRepositoryPort, VerificationTokenRepositoryPort |
| `sovkora-jobs-api` | contract | `contracts` | 1.0.0-SNAPSHOT | — | — |
| `sovkora-persistence-api` | contract | `contracts` | 1.0.0-SNAPSHOT | CapabilityManager, CatalogModelManager, ChatMemoryPersistenceProvider, ChatSessionPersistenceProvider, FeatureCachePolicyManager, FeatureRouteManager, FeatureStructuredBoundManager, FeatureToolBoundManager, JobPersistenceProvider, OutboxStore, PipelineConfigManager, PlatformMcpServerPersistenceProvider, PlatformToolConfigPersistenceProvider, RuntimeConfigProvider, SpeechVoiceManager, UserMcpServerPersistenceProvider, ValidationPipelineManager | — |
| `sovkora-studio-api` | contract | `studio` | 1.0.0-SNAPSHOT | — | — |
| `krizaka-billing-api` | contract | `krizaka-java` | 0.16.0 | — | — |
| `krizaka-users-api` | contract | `krizaka-java` | 0.16.0 | — | — |
| `krizaka-app-config` | platform | `krizaka-java` | 0.16.0 | — | — |
| `krizaka-idempotency` | platform | `krizaka-java` | 0.16.0 | — | — |
| `krizaka-messaging` | platform | `krizaka-java` | 0.16.0 | — | — |
| `krizaka-notifications-api` | platform | `krizaka-java` | 0.16.0 | — | — |
| `krizaka-observability` | platform | `krizaka-java` | 0.16.0 | — | — |
| `krizaka-persistence` | platform | `krizaka-java` | 0.16.0 | — | — |
| `krizaka-ratelimit` | platform | `krizaka-java` | 0.16.0 | — | — |
| `krizaka-security` | platform | `krizaka-java` | 0.16.0 | — | — |
| `krizaka-spring-boot-starter-app-config` | platform | `krizaka-java` | 0.16.0 | — | — |
| `krizaka-spring-boot-starter-idempotency` | platform | `krizaka-java` | 0.16.0 | — | — |
| `krizaka-spring-boot-starter-observability` | platform | `krizaka-java` | 0.16.0 | — | — |
| `krizaka-spring-boot-starter-persistence` | platform | `krizaka-java` | 0.16.0 | — | — |
| `krizaka-spring-boot-starter-rabbitmq` | platform | `krizaka-java` | 0.16.0 | — | — |
| `krizaka-spring-boot-starter-ratelimit` | platform | `krizaka-java` | 0.16.0 | — | — |
| `krizaka-spring-boot-starter-security` | platform | `krizaka-java` | 0.16.0 | — | — |
| `krizaka-spring-boot-starter-web` | platform | `krizaka-java` | 0.16.0 | — | — |
| `krizaka-spring-boot-starter-webhooks` | platform | `krizaka-java` | 0.16.0 | — | — |
| `krizaka-web` | platform | `krizaka-java` | 0.16.0 | — | — |
| `krizaka-webhooks` | platform | `krizaka-java` | 0.16.0 | — | — |

## Dependencies

Build dependencies between the modules above (test scope excluded): who packages whom.

| Module | Depends on |
|:---|:---|
| `sovkora-automation-service` | `krizaka-billing-client`, `krizaka-messaging`, `krizaka-security`, `sovkora-engine-assets` |
| `sovkora-conversation-service` | `krizaka-billing-client`, `krizaka-spring-boot-starter-app-config`, `krizaka-spring-boot-starter-observability`, `krizaka-spring-boot-starter-rabbitmq`, `krizaka-spring-boot-starter-security`, `krizaka-spring-boot-starter-web`, `krizaka-users-api`, `krizaka-users-client`, `sovkora-engine-assets`, `sovkora-engine-business`, `sovkora-engine-core`, `sovkora-engine-interceptors`, `sovkora-engine-persistence`, `sovkora-engine-persistence-bridge`, `sovkora-engine-tools`, `sovkora-jobs-api`, `sovkora-studio-client` |
| `sovkora-edge-service` | `krizaka-security` |
| `sovkora-job-service` | `krizaka-billing-client`, `krizaka-messaging`, `krizaka-security`, `krizaka-users-api`, `krizaka-users-client`, `sovkora-engine-assets`, `sovkora-engine-business`, `sovkora-engine-core`, `sovkora-engine-interceptors`, `sovkora-engine-persistence`, `sovkora-engine-persistence-bridge`, `sovkora-engine-tools`, `sovkora-jobs-api` |
| `sovkora-knowledge-service` | `krizaka-messaging`, `krizaka-security` |
| `sovkora-studio-service` | `krizaka-billing-api`, `krizaka-billing-client`, `krizaka-messaging`, `krizaka-security`, `krizaka-spring-boot-starter-observability`, `sovkora-crypto`, `sovkora-jobs-api`, `sovkora-studio-api` |
| `krizaka-billing-service` | `krizaka-billing-api`, `krizaka-messaging`, `krizaka-security`, `krizaka-spring-boot-starter-observability` |
| `krizaka-notifications-service` | `krizaka-messaging`, `krizaka-notifications-api`, `krizaka-spring-boot-starter-observability` |
| `krizaka-users-service` | `krizaka-security`, `krizaka-spring-boot-starter-observability`, `krizaka-users-core` |
| `sovkora-engine-architecture` | `sovkora-engine-business`, `sovkora-engine-core`, `sovkora-engine-interceptors`, `sovkora-engine-springai`, `sovkora-engine-tools` |
| `sovkora-engine-assets` | `sovkora-crypto` |
| `sovkora-engine-persistence` | `krizaka-messaging`, `sovkora-crypto`, `sovkora-jobs-api`, `sovkora-persistence-api` |
| `sovkora-engine-persistence-bridge` | `sovkora-crypto`, `sovkora-engine-core`, `sovkora-persistence-api` |
| `sovkora-engine-springai` | `sovkora-engine-business`, `sovkora-engine-core` |
| `sovkora-studio-client` | `krizaka-security`, `sovkora-studio-api` |
| `krizaka-billing-client` | `krizaka-billing-api`, `krizaka-security` |
| `krizaka-users-client` | `krizaka-security`, `krizaka-users-api` |
| `krizaka-users-persistence` | `krizaka-messaging` |
| `sovkora-engine-business` | `sovkora-engine-core`, `sovkora-studio-api`, `sovkora-studio-client` |
| `sovkora-engine-interceptors` | `krizaka-billing-api`, `sovkora-engine-core`, `sovkora-engine-springai`, `sovkora-jobs-api` |
| `sovkora-engine-tools` | `krizaka-billing-api`, `sovkora-engine-core`, `sovkora-engine-springai` |
| `sovkora-engine-core` | `krizaka-security`, `sovkora-persistence-api` |
| `krizaka-users-core` | `krizaka-security`, `krizaka-users-api`, `krizaka-users-persistence` |
| `sovkora-persistence-api` | `sovkora-jobs-api` |
| `sovkora-studio-api` | `sovkora-jobs-api` |
| `krizaka-idempotency` | `krizaka-web` |
| `krizaka-ratelimit` | `krizaka-web` |
| `krizaka-spring-boot-starter-app-config` | `krizaka-app-config` |
| `krizaka-spring-boot-starter-idempotency` | `krizaka-idempotency`, `krizaka-spring-boot-starter-web` |
| `krizaka-spring-boot-starter-observability` | `krizaka-observability` |
| `krizaka-spring-boot-starter-persistence` | `krizaka-persistence` |
| `krizaka-spring-boot-starter-rabbitmq` | `krizaka-messaging` |
| `krizaka-spring-boot-starter-ratelimit` | `krizaka-ratelimit`, `krizaka-spring-boot-starter-web` |
| `krizaka-spring-boot-starter-security` | `krizaka-security` |
| `krizaka-spring-boot-starter-web` | `krizaka-web` |
| `krizaka-spring-boot-starter-webhooks` | `krizaka-spring-boot-starter-web`, `krizaka-webhooks` |
| `krizaka-webhooks` | `krizaka-web` |

## Runtime flows

Who calls whom while the platform runs: HTTP (UI defaults, the edge route table, the typed clients a service packages) and AMQP (a published routing key that reaches a queue).

| From | To | Kind | Via |
|:---|:---|:---|:---|
| `krizaka-users-service` | `krizaka-notifications-service` | amqp | `krizaka.notifications.password-events` (`evt.password.reset`) |
| `krizaka-users-service` | `krizaka-notifications-service` | amqp | `krizaka.notifications.user-events` (`evt.user.registered`) |
| `sovkora-automation-service` | `sovkora-studio-service` | amqp | `orazaka.events.studio.connector` (`evt.automation.telemetry`) |
| `sovkora-conversation-service` | `sovkora-automation-service` | amqp | `orazaka.jobs.automation` (`job.automation.approved`) |
| `sovkora-conversation-service` | `sovkora-job-service` | amqp | `orazaka.jobs.batch` (`job.media.generate`) |
| `sovkora-conversation-service` | `sovkora-job-service` | amqp | `orazaka.jobs.interactive` (`job.text.*, job.media.analyze`) |
| `sovkora-conversation-service` | `sovkora-studio-service` | amqp | `orazaka.events.studio.capability` (`evt.capability.changed`) |
| `sovkora-job-service` | `krizaka-billing-service` | amqp | `krizaka.billing.settlements` (`job.{…}.done`) |
| `sovkora-job-service` | `krizaka-notifications-service` | amqp | `krizaka.billing.settlements` (`job.{…}.done`) |
| `sovkora-job-service` | `krizaka-users-service` | amqp | `krizaka.billing.settlements` (`job.{…}.done`) |
| `sovkora-job-service` | `sovkora-automation-service` | amqp | `krizaka.billing.settlements` (`job.{…}.done`) |
| `sovkora-job-service` | `sovkora-conversation-service` | amqp | `krizaka.billing.settlements` (`job.{…}.done`) |
| `sovkora-job-service` | `sovkora-conversation-service` | amqp | `orazaka.events.job-relay` (`job.{…}.cancelled`) |
| `sovkora-job-service` | `sovkora-knowledge-service` | amqp | `krizaka.billing.settlements` (`job.{…}.done`) |
| `sovkora-job-service` | `sovkora-studio-service` | amqp | `krizaka.billing.settlements` (`job.{…}.done`) |
| `sovkora-job-service` | `sovkora-studio-service` | amqp | `orazaka.events.studio.capability` (`evt.capability.changed`) |
| `sovkora-studio-service` | `sovkora-automation-service` | amqp | `orazaka.jobs.automation` (`job.automation.approved`) |
| `sovkora-studio-service` | `sovkora-job-service` | amqp | `orazaka.jobs.batch` (`job.media.generate`) |
| `sovkora-studio-service` | `sovkora-job-service` | amqp | `orazaka.jobs.interactive` (`job.text.*, job.media.analyze`) |
| `cli` | `sovkora-conversation-service` | http | `localhost:8080` |
| `cli` | `sovkora-edge-service` | http | `localhost:8088` |
| `sovkora-automation-service` | `krizaka-notifications-service` | http | `krizaka-billing-client` |
| `sovkora-conversation-service` | `krizaka-notifications-service` | http | `krizaka-billing-client` |
| `sovkora-conversation-service` | `krizaka-notifications-service` | http | `krizaka-users-client` |
| `sovkora-conversation-service` | `sovkora-studio-service` | http | `sovkora-studio-client` |
| `sovkora-edge-service` | `krizaka-billing-service` | http | `route /api/v1/billing` |
| `sovkora-edge-service` | `krizaka-users-service` | http | `route /api/v1/admin/users` |
| `sovkora-edge-service` | `krizaka-users-service` | http | `route /api/v1/api-keys` |
| `sovkora-edge-service` | `krizaka-users-service` | http | `route /api/v1/auth` |
| `sovkora-edge-service` | `krizaka-users-service` | http | `route /api/v1/credentials` |
| `sovkora-edge-service` | `krizaka-users-service` | http | `route /api/v1/interceptions` |
| `sovkora-edge-service` | `krizaka-users-service` | http | `route /api/v1/organizations` |
| `sovkora-edge-service` | `krizaka-users-service` | http | `route /api/v1/profile` |
| `sovkora-edge-service` | `sovkora-automation-service` | http | `route /api/v1/automations` |
| `sovkora-edge-service` | `sovkora-automation-service` | http | `route /hooks/` |
| `sovkora-edge-service` | `sovkora-conversation-service` | http | `route /` |
| `sovkora-edge-service` | `sovkora-knowledge-service` | http | `route /api/v1/analytics` |
| `sovkora-edge-service` | `sovkora-knowledge-service` | http | `route /api/v1/recommendations` |
| `sovkora-edge-service` | `sovkora-studio-service` | http | `route /api/v1/studios` |
| `sovkora-job-service` | `krizaka-notifications-service` | http | `krizaka-billing-client` |
| `sovkora-job-service` | `krizaka-notifications-service` | http | `krizaka-users-client` |
| `sovkora-job-service` | `sovkora-studio-service` | http | `sovkora-studio-client` |
| `sovkora-studio-service` | `krizaka-notifications-service` | http | `krizaka-billing-client` |
| `web` | `sovkora-conversation-service` | http | `localhost:8080` |
| `web` | `sovkora-edge-service` | http | `localhost:8088` |

## Interceptor pipeline

**Phase 1 — core chain** (fixed in code, non-bypassable, ADR-051)

| # | Interceptor | Concern |
|:--|:---|:---|
| 1 | `SafetyInterceptor` | validation |
| 2 | `ScopeGuardInterceptor` | governance |
| 3 | `UserContextResolver` | context |
| 4 | `SystemContextInjector` | context |
| 5 | `RagInterceptor` | enrichment |

**Phase 2 — configured chain** (order and switch in `pipeline_interceptor_config`)

| Order | Interceptor | Enabled | Implemented |
|:--|:---|:--|:--|
| 4 | `EntitlementInterceptor` | ✅ | ✅ |
| 4 | `SpendCapInterceptor` | ✅ | ✅ |
| 5 | `McpInterceptor` | ✅ | ✅ |
| 6 | `BrandContextInterceptor` | ✅ | ✅ |
| 7 | `MemoryInterceptor` | ✅ | ✅ |
| 8 | `RefinerInterceptor` | ✅ | ✅ |
| 8 | `SemanticCacheInterceptor` | — | ✅ |
| 9 | `RouterInterceptor` | ✅ | ✅ |
| 9 | `ToolInterceptor` | ✅ | ✅ |
| 10 | `MediaInterceptor` | ✅ | ⚠ no class |
| 11 | `UserContextInterceptor` | ✅ | ✅ |
| 12 | `CitationVerifierInterceptor` | — | ✅ |

## Messaging topology (AGENTS.md §6)

**Exchanges**

- `orazaka.jobs` (topic)
- `orazaka.events` (topic)
- `orazaka.dlx` (direct)

**Retry, then dead letter** — a listener on the kit's container (`krizaka-messaging`, `krizaka.messaging.retry`) runs up to 5 times, waiting 500 ms ×2 up to 10 s, then the message goes to `<queue>.dlq`.

**Queues**

| Queue | Exchange | Binding | DLQ | Declared by | Consumed by |
|:---|:---|:---|:---|:---|:---|
| `krizaka.billing.settlements` | `orazaka.events` | `job.*.done, job.*.error` | `krizaka.billing.settlements.dlq` | `krizaka-billing-service` | `krizaka-billing-service`, `krizaka-messaging` |
| `krizaka.billing.unmetered` | `orazaka.events` | `evt.turn.unmetered` | `krizaka.billing.unmetered.dlq` | `krizaka-billing-service` | `krizaka-billing-service` |
| `krizaka.notifications.password-events` | `orazaka.events` | `evt.password.*` | `krizaka.notifications.password-events.dlq` | `krizaka-notifications-service` | `krizaka-notifications-service` |
| `krizaka.notifications.requests` | `orazaka.events` | `evt.notification.requested` | `krizaka.notifications.requests.dlq` | `krizaka-notifications-service` | `krizaka-notifications-service` |
| `krizaka.notifications.user-events` | `orazaka.events` | `evt.user.*` | `krizaka.notifications.user-events.dlq` | `krizaka-notifications-service` | `krizaka-notifications-service` |
| `orazaka.automation.studio-runs` | `orazaka.events` | — | `orazaka.automation.studio-runs.dlq` | `sovkora-automation-service` | `sovkora-automation-service` |
| `orazaka.events.job-relay` | `orazaka.events` | `job.#` | `orazaka.events.job-relay.dlq` | `sovkora-engine-persistence` | `sovkora-conversation-service` |
| `orazaka.events.studio` | `orazaka.events` | `job.*.done, job.*.error` | `orazaka.events.studio.dlq` | `sovkora-studio-service` | — |
| `orazaka.events.studio.capability` | `orazaka.events` | `evt.capability.*` | `orazaka.events.studio.capability.dlq` | `sovkora-studio-service` | `sovkora-studio-service` |
| `orazaka.events.studio.connector` | `orazaka.events` | `evt.automation.telemetry` | `orazaka.events.studio.connector.dlq` | `sovkora-studio-service` | `sovkora-studio-service` |
| `orazaka.events.studio.subscription` | `orazaka.events` | `evt.subscription.*` | `orazaka.events.studio.subscription.dlq` | `sovkora-studio-service` | `sovkora-studio-service` |
| `orazaka.jobs.automation` | `orazaka.jobs` | `job.automation.*` | `orazaka.jobs.automation.dlq` | `sovkora-automation-service` | `sovkora-automation-service` |
| `orazaka.jobs.batch` | `orazaka.jobs` | `job.media.generate` | `orazaka.jobs.batch.dlq` | `sovkora-engine-persistence` | `sovkora-job-service` |
| `orazaka.jobs.interactive` | `orazaka.jobs` | `job.text.*, job.media.analyze` | `orazaka.jobs.interactive.dlq` | `sovkora-engine-persistence` | `sovkora-job-service` |
| `orazaka.jobs.rag` | `orazaka.jobs` | `job.rag.*` | `orazaka.jobs.rag.dlq` | `sovkora-knowledge-service` | `sovkora-knowledge-service` |
| `orazaka.jobs.video` | `orazaka.jobs` | `job.video.*` | `orazaka.jobs.video.dlq` | `sovkora-engine-persistence` | — |

**Producers**

| Module | Exchange | Routing key |
|:---|:---|:---|
| `krizaka-billing-service` | `orazaka.events` | `(dynamic)` |
| `krizaka-users-core` | `orazaka.events` | `evt.password.reset` |
| `krizaka-users-core` | `orazaka.events` | `evt.user.registered` |
| `sovkora-automation-service` | `orazaka.jobs` | `(dynamic)` |
| `sovkora-automation-service` | `orazaka.events` | `evt.automation.telemetry` |
| `sovkora-conversation-service` | `orazaka.jobs` | `(capability route)` |
| `sovkora-conversation-service` | `orazaka.events` | `(dynamic)` |
| `sovkora-conversation-service` | `orazaka.events` | `evt.agent.presence` |
| `sovkora-conversation-service` | `orazaka.events` | `evt.capability.changed` |
| `sovkora-conversation-service` | `orazaka.jobs` | `job.automation.approved` |
| `sovkora-engine-persistence-bridge` | `orazaka.events` | `evt.control.verdict` |
| `sovkora-job-service` | `orazaka.jobs` | `(dynamic)` |
| `sovkora-job-service` | `orazaka.events` | `(dynamic)` |
| `sovkora-job-service` | `orazaka.events` | `evt.capability.changed` |
| `sovkora-job-service` | `orazaka.events` | `job.{…}.cancelled` |
| `sovkora-job-service` | `orazaka.events` | `job.{…}.done` |
| `sovkora-job-service` | `orazaka.events` | `job.{…}.error` |
| `sovkora-job-service` | `orazaka.events` | `job.{…}.progress` |
| `sovkora-studio-service` | `orazaka.jobs` | `(capability route)` |
| `sovkora-studio-service` | `orazaka.events` | `evt.studio.run.failed` |
| `sovkora-studio-service` | `orazaka.events` | `evt.studio.run.started` |
| `sovkora-studio-service` | `orazaka.events` | `evt.studio.run.succeeded` |
| `sovkora-studio-service` | `orazaka.jobs` | `job.automation.approved` |
| `sovkora-studio-service` | `orazaka.jobs` | `job.cancel.{…}` |

**Capability routes** (`orazaka_capabilities`: where a job of each capability is published)

| Capability | Handler | Routing key |
|:---|:---|:---|
| `orazaka.studio.media.compose` | `media.compose` | `job.compose.assemble` |
| `orazaka.core.media.vision` | `image.analyze` | `job.media.analyze` |
| `orazaka.core.media.audio.analysis` | `audio.analyze` | `job.media.generate` |
| `orazaka.core.media.image` | `image.generate` | `job.media.generate` |
| `orazaka.core.media.video.analysis` | `video.analyze` | `job.media.generate` |
| `orazaka.studio.media.sound` | `media.sound` | `job.sound.generate` |
| `orazaka.core.chat.completion` | `text.generate` | `job.text.process` |
| `orazaka.core.checks.courtrecords` | `check.external` | `job.text.process` |
| `orazaka.core.checks.credit` | `check.external` | `job.text.process` |
| `orazaka.core.checks.identity` | `check.external` | `job.text.process` |
| `orazaka.core.media.speech` | `speech.synthesize` | `job.text.process` |
| `orazaka.core.research.grounded` | `research.grounded` | `job.text.process` |
| `orazaka.core.media.video` | `video.generate` | `job.video.generate` |
