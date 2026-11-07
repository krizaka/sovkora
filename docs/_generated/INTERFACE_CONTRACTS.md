---
title: Interface Contracts (generated)
description: Inbound and outbound ports per module, extracted from the code.
category: Architecture
order: 3
generated: true
---

# Interface Contracts (generated)

## `sovkora-engine-core`

**Inbound ports**

- `AgentTurns`
- `AiClient`
- `CatalogModelService`
- `ChatSessionService`
- `CitationVerifier`
- `ExternalCheckCapability`
- `JobService`
- `McpService`
- `StepRouter`

**Outbound ports**

- `AccessAudit`
- `AudioGeneratorClient`
- `AudioTranscriber`
- `CapabilityProvider`
- `ChatGeneratorClient`
- `ChatInvocation`
- `ChatMemoryStore`
- `CheckAuditLog`
- `CheckHttp`
- `CheckKeyStore`
- `CheckMeter`
- `CheckProviderCatalog`
- `CheckSubjectPepper`
- `CodeSandboxPort`
- `ConsentRegistry`
- `ControlVerdictRecorder`
- `ConversationMemory`
- `DomainEventPublisher`
- `EmbeddingClient`
- `ExternalCheckConnector`
- `FeatureModelRoutes`
- `ImageGeneratorClient`
- `InfrastructureStatusProvider`
- `KnowledgeService`
- `McpOrchestrator`
- `ModelCatalogProvider`
- `ModelEndpointResolver`
- `OutputJudge`
- `PipelineConfigProvider`
- `PlatformMcpServerProvider`
- `PlatformToolConfigProvider`
- `PromptAssembly`
- `PromptCachePolicy`
- `ProviderChatClient`
- `SemanticClassifierPort`
- `SpeechSynthesis`
- `StepModelRecorder`
- `StepRouteProvider`
- `StructuredOutputBounds`
- `StructuredOutputClient`
- `TestShaperPort`
- `ToolAnswerCodec`
- `ToolLoopBounds`
- `ToolOptions`
- `ToolPermissionProvider`
- `ToolRegistry`
- `ToolResolution`
- `ToolResultCache`
- `UserCredentialsProvider`
- `UserMcpServerProvider`
- `ValidationPipelineRepository`

## `krizaka-users-core`

**Inbound ports**

- `AdminUserService`
- `ApiKeyService`
- `CredentialGrantService`
- `IdentityReconciliationService`
- `IdentityService`
- `LoginLockoutService`
- `OrganizationService`
- `PasswordPolicyService`
- `PasswordRecoveryService`
- `RateLimitProvider`
- `SocialConnectionService`
- `TokenService`
- `UserProfileProvider`

**Outbound ports**

- `AdminUserRepositoryPort`
- `ApiKeyRepositoryPort`
- `AuthorityRepositoryPort`
- `BreachedPasswordPort`
- `CredentialGrantRepositoryPort`
- `CryptographyPort`
- `LockoutRepositoryPort`
- `OAuth2ProviderVerifier`
- `OrganizationRepositoryPort`
- `PasswordEventPublisher`
- `PasswordResetTokenRepositoryPort`
- `SecretSealPort`
- `SocialConnectionRepositoryPort`
- `UserCredentialRepositoryPort`
- `UserEventPublisher`
- `UserInterceptionRepositoryPort`
- `UserProfileRepositoryPort`
- `UserRepositoryPort`
- `VerificationTokenRepositoryPort`

## `sovkora-persistence-api`

**Inbound ports**

- `CapabilityManager`
- `CatalogModelManager`
- `ChatMemoryPersistenceProvider`
- `ChatSessionPersistenceProvider`
- `FeatureCachePolicyManager`
- `FeatureRouteManager`
- `FeatureStructuredBoundManager`
- `FeatureToolBoundManager`
- `JobPersistenceProvider`
- `OutboxStore`
- `PipelineConfigManager`
- `PlatformMcpServerPersistenceProvider`
- `PlatformToolConfigPersistenceProvider`
- `RuntimeConfigProvider`
- `SpeechVoiceManager`
- `UserMcpServerPersistenceProvider`
- `ValidationPipelineManager`

