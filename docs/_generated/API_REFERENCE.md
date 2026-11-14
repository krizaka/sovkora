---
title: API Reference
description: Every REST endpoint of every service, with the authorisation rule that guards it, extracted from the controllers and their SecurityConfig.
category: API
order: 6
generated: true
---

# API Reference


**Access** is read from each service's `SecurityConfig`, most-specific matcher first — the same
order Spring Security applies. `SERVICE` is the machine-to-machine authority required on
`/internal/v1/**` (ADR-035); `public` means no credential at all, and **⚠ no rule** means
no matcher covers the path — the service has no security on it whatsoever.


## automation-service

| Method | Path | Access | Controller | Summary |
|:---|:---|:---|:---|:---|
| GET | `/api/v1/automations` | authenticated | AutomationController | The caller's automations. |
| POST | `/api/v1/automations` | authenticated | AutomationController | Creates an automation. |
| GET | `/api/v1/automations/{automationId}/runs` | authenticated | RunController | The runs of one automation. |
| GET | `/api/v1/automations/{automationId}/triggers` | authenticated | TriggerController | The triggers of the automation (no secrets). |
| POST | `/api/v1/automations/{automationId}/triggers` | authenticated | TriggerController | Adds a trigger. |
| DELETE | `/api/v1/automations/{automationId}/triggers/{triggerId}` | authenticated | TriggerController | Removes a trigger; its runs stay in the history. |
| PATCH | `/api/v1/automations/{automationId}/triggers/{triggerId}` | authenticated | TriggerController | Enables or disables a trigger. |
| POST | `/api/v1/automations/{automationId}/triggers/{triggerId}/rotate` | authenticated | TriggerController | Issues a new secret. |
| DELETE | `/api/v1/automations/{id}` | authenticated | AutomationController | Removes an automation with its triggers and history. |
| GET | `/api/v1/automations/{id}` | authenticated | AutomationController | One automation. |
| PATCH | `/api/v1/automations/{id}` | authenticated | AutomationController | Changes an automation; a missing field is left as it is. |
| GET | `/api/v1/automations/runs` | authenticated | RunController | Every run of every automation of the caller. |
| GET | `/api/v1/automations/runs/{runId}` | authenticated | RunController | One run, with the webhook body it was started with. |
| POST | `/hooks/{triggerId}` | authenticated | WebhookIngressController | Receives a signed call and starts the automation it stands for. |

## conversation-service

| Method | Path | Access | Controller | Summary |
|:---|:---|:---|:---|:---|
| ANY | `/` | authenticated | LawCorpusAdminController | The Corpus tab of the review queue (ADR-089 §8): the instruments, an article by point in time (metadata only, never its text), the imports and the manual import. |
| ANY | `/` | authenticated | LawReviewController | The review queue of ADR-089 (§5, §8): the proposals and their decisions, the golden cases and the promotion of a kind, the derivations, and the demand. |
| GET | `/api/v1/admin/spend-caps` | ADMIN | SpendCapController | Every cap, with the actions on offer and the price table. |
| PUT | `/api/v1/admin/spend-caps/{environment}/{productKey}` | ADMIN | SpendCapController | Creates or changes the cap of one environment and product key. |
| POST | `/api/v1/agent/report` | authenticated | AgentController | Receives execution reports from the CLI agent after local task completion. |
| GET | `/api/v1/agent/stream` | authenticated | AgentController | Establishes a persistent SSE stream for the CLI agent. |
| GET | `/api/v1/assets/{jobId}/{filename}` | ADMIN + USER | AssetController | Streams one asset to its owner, or to an administrator. |
| GET | `/api/v1/chat/sessions` | authenticated | ChatController |  |
| POST | `/api/v1/chat/sessions` | authenticated | ChatController |  |
| DELETE | `/api/v1/chat/sessions/{sessionId}` | authenticated | ChatController |  |
| PATCH | `/api/v1/chat/sessions/{sessionId}` | authenticated | ChatController |  |
| POST | `/api/v1/chat/stream/{conversationId}` | ADMIN + USER | ChatController | Streams chat via SSE using POST. |
| GET | `/api/v1/checks/{providerRef}` | authenticated | ChecksController | Collects a check that came back PENDING. |
| POST | `/api/v1/checks/{purpose}` | authenticated | ChecksController | Starts a check. |
| POST | `/api/v1/code` | authenticated | CodeController | Streams code generation via SSE using POST. |
| POST | `/api/v1/embeddings` | ADMIN + USER | EmbeddingController |  |
| GET | `/api/v1/features` | ADMIN + USER | FeatureController | What this deployment can currently run: every enabled capability with its live degraded-mode state. |
| PUT | `/api/v1/features/{featureKey}` | ADMIN | FeatureController | Admin: toggles an existing capability's enabled state. |
| GET | `/api/v1/features/all` | ADMIN | FeatureController | Admin: all capabilities (enabled or not) from the database. |
| POST | `/api/v1/intent` | authenticated | IntentController | Dispatches a synchronous user intention through the App Factory and returns the result. |
| POST | `/api/v1/intent/route` | public | IntentController | M2M Level-0 gate routing: verifies the envelope and returns a signed intent token. |
| GET | `/api/v1/jobs` | authenticated | JobController | Retrieves a paginated list of jobs for the authenticated user (or all jobs for admins). |
| DELETE | `/api/v1/jobs/{id}` | ADMIN + USER | JobController | Deletes a finished job's result: files out of every store, row marked deleted (sovkora-web#86). |
| GET | `/api/v1/jobs/{id}` | ADMIN + USER | JobController | Fetches the single, atomic state of a specific job. |
| POST | `/api/v1/jobs/{id}/progress` | ADMIN + USER | JobController | Updates progress of a running job and broadcasts it to connected users. |
| POST | `/api/v1/jobs/{jobId}/approve` | ADMIN + USER | JobController | Approves a pending automation job and dispatches its payload to the execution queue. |
| POST | `/api/v1/jobs/{jobId}/revoke` | ADMIN + USER | JobController | Revokes a pending automation job, preventing its execution. |
| GET | `/api/v1/jobs/active-connections` | ADMIN | JobController | Retrieves the current active SSE connection count. |
| POST | `/api/v1/jobs/purge` | ADMIN | JobController | Purges jobs, chat sessions and rate-limit cache for the default test accounts. |
| GET | `/api/v1/jobs/stream` | ADMIN + USER | JobController | Registers a Server-Sent Events stream for real-time job status notifications. |
| GET | `/api/v1/law/articles` | ADMIN + USER | LawArticleController | Reads an article as it was in force on a day. |
| GET | `/api/v1/mcp/servers/platform` | ADMIN | McpController |  |
| POST | `/api/v1/mcp/servers/platform` | ADMIN | McpController |  |
| DELETE | `/api/v1/mcp/servers/platform/{id}` | ADMIN | McpController |  |
| GET | `/api/v1/mcp/servers/user` | authenticated | McpController |  |
| POST | `/api/v1/mcp/servers/user` | authenticated | McpController |  |
| DELETE | `/api/v1/mcp/servers/user/{id}` | authenticated | McpController |  |
| GET | `/api/v1/mcp/tools` | authenticated | McpController | Retrieves the registered tools the caller may use, with their schemas. |
| POST | `/api/v1/mcp/tools/{name}/execute` | authenticated | McpController | Executes a tool by name with the provided arguments, for the authenticated caller: a tool the caller may not use answers 403 and leaves an audit line (ADR-091). |
| DELETE | `/api/v1/media/assets/{assetId}` | authenticated | MediaAnalysisController | Deletes one of the caller's own uploads (the sealed file in <uploads>/<user>/temp). |
| GET | `/api/v1/media/search` | authenticated | MediaAnalysisController | Performs a passive RAG context search against the semantic index, among what the caller may read: the actor is built from the session, and a caller without one gets 401 (ADR-091). |
| POST | `/api/v1/media/upload` | authenticated | MediaAnalysisController | Securely uploads a raw binary asset, isolated per authenticated user. |
| GET | `/api/v1/models` | ADMIN + USER | ModelController | Retrieves the local catalog of Ollama models. |
| POST | `/api/v1/models` | ADMIN | ModelController | Adds a new model definition to the catalog. |
| DELETE | `/api/v1/models/{id}` | ADMIN | ModelController | Deletes a model definition by its database ID. |
| PUT | `/api/v1/models/{id}` | ADMIN | ModelController | Updates an existing model definition in the catalog. |
| GET | `/api/v1/models/admin/audit` | ADMIN | OperatorAuditController |  |
| GET | `/api/v1/models/admin/feature-cache-policies` | ADMIN | FeatureCachePolicyAdminController | Every policy, the default row first. |
| DELETE | `/api/v1/models/admin/feature-cache-policies/{featureKey}` | ADMIN | FeatureCachePolicyAdminController | Removes the policy a feature set for itself; the default row then applies. |
| PUT | `/api/v1/models/admin/feature-cache-policies/{featureKey}` | ADMIN | FeatureCachePolicyAdminController | Sets the policy of a feature (* is the default row). |
| GET | `/api/v1/models/admin/feature-routes` | ADMIN | FeatureAdminController | The feature routes, read only. |
| GET | `/api/v1/models/admin/feature-structured-bounds` | ADMIN | FeatureStructuredBoundAdminController | Every bound, the default row first. |
| DELETE | `/api/v1/models/admin/feature-structured-bounds/{featureKey}` | ADMIN | FeatureStructuredBoundAdminController | Removes the structured-output bound a feature set for itself; the default row then applies. |
| PUT | `/api/v1/models/admin/feature-structured-bounds/{featureKey}` | ADMIN | FeatureStructuredBoundAdminController | Sets the bound of a feature (* is the default row). |
| GET | `/api/v1/models/admin/feature-tool-bounds` | ADMIN | FeatureAdminController | Every bound, the default row first. |
| DELETE | `/api/v1/models/admin/feature-tool-bounds/{featureKey}` | ADMIN | FeatureAdminController | Removes a feature's own bound so it falls back to the default row. |
| PUT | `/api/v1/models/admin/feature-tool-bounds/{featureKey}` | ADMIN | FeatureAdminController | Sets the bound of a feature (* is the default row). |
| GET | `/api/v1/models/catalog` | authenticated | ModelController | Retrieves the complete AI model catalog definitions from the database. |
| GET | `/api/v1/models/providers` | authenticated | ModelController | Retrieves all available AI provider names from the database. |
| GET | `/api/v1/models/supported` | authenticated | ModelController | Retrieves the list of supported model names grouped by media type. |
| GET | `/api/v1/pipeline/interceptors` | ADMIN | PipelineController | Retrieves all pipeline interceptor configurations ordered by execution order. |
| PUT | `/api/v1/pipeline/interceptors` | ADMIN | PipelineController | Bulk-updates pipeline interceptor ordering and enabled state. |
| POST | `/api/v1/pipeline/interceptors/reset` | ADMIN | PipelineController | Resets all pipeline interceptor configurations to hardcoded defaults. |
| GET | `/api/v1/pipeline/validation` | ADMIN | PipelineController | Retrieves all validation pipeline tier configurations ordered by execution order. |
| PUT | `/api/v1/pipeline/validation` | ADMIN | PipelineController | Bulk-updates validation pipeline tier ordering and enabled state. |
| GET | `/api/v1/status/graph` | ADMIN + USER | StatusController | Compiles and outputs the operations graph. |
| GET | `/api/v1/status/health` | public | StatusController | Returns sovereign readiness health details (public probe). |
| GET | `/api/v1/status/infrastructure` | authenticated | StatusController | Returns the current online/offline snapshot of the local inference engines. |
| GET | `/api/v1/voice/clips/{clipId}` | public | VoiceController | One spoken sentence, by its signed URL — no bearer token, because an <audio> element cannot send one; the signature and its five-minute expiry are the authorisation. |
| POST | `/api/v1/voice/turns` | ADMIN + USER | VoiceController | Takes a turn: audio (WAV, Ogg/Opus, WebM, MP3; ~30 s at most) or text, answered over SSE — transcript, delta, sentence (text and a signed audio URL valid 300 s), safety.crisis or scope.refused when a guard answers, then done with the turn's usage and timings. |
| GET | `/articles` | authenticated | LawCorpusAdminController | An article by point in time. |
| GET | `/demand` | authenticated | LawReviewController | The unsupported and unknown kinds, counted. |
| POST | `/derivations` | authenticated | LawReviewController | Starts the derivation of a kind. |
| GET | `/imports` | authenticated | LawCorpusAdminController | The imports. |
| POST | `/imports` | authenticated | LawCorpusAdminController | The manual import: the consolidated XML an administrator uploads, with where it came from. |
| GET | `/instruments` | authenticated | LawCorpusAdminController | The instruments. |
| POST | `/internal/v1/agent-turns` | SERVICE | AgentTurnsController | Runs one model turn. |
| POST | `/internal/v1/protected-conversations/erase` | SERVICE | ProtectedConversationController | Erases a person's conversations under a pack. |
| GET | `/kinds/{kindId}/golden` | authenticated | LawReviewController | The golden cases of a kind against the minimum of promotion. |
| POST | `/kinds/{kindId}/promote` | authenticated | LawReviewController | Opens the packs pull request of approved proposals. |
| GET | `/proposals` | authenticated | LawReviewController | The queue. |
| GET | `/proposals/{id}` | authenticated | LawReviewController | One proposal. |
| PUT | `/proposals/{id}` | authenticated | LawReviewController | Edits a proposal: its next revision, vetted again. |
| POST | `/proposals/{id}/approve` | authenticated | LawReviewController | Approves a proposal. |
| POST | `/proposals/{id}/reject` | authenticated | LawReviewController | Rejects a proposal, with its reason. |

## edge-service

| Method | Path | Access | Controller | Summary |
|:---|:---|:---|:---|:---|
| ANY | `/**` | ⚠ no rule | ProxyController | Catch-all proxy entry point — everything not served by the edge itself is forwarded. |

## job-service

| Method | Path | Access | Controller | Summary |
|:---|:---|:---|:---|:---|
| DELETE | `/internal/v1/capabilities/{featureKey}` | SERVICE | CapabilityController | Removes one capability. |
| PUT | `/internal/v1/capabilities/{featureKey}` | SERVICE | CapabilityController | Registers or replaces one capability a pack contributes. |
| GET | `/internal/v1/capabilities/{featureKey}/route` | SERVICE | CapabilityController | Resolves where one capability's work is executed. |
| DELETE | `/internal/v1/gpu-pods/{podKey}/token` | SERVICE | PodTokenController | Revokes the token of a pod the launcher terminated. |
| POST | `/internal/v1/gpu-pods/{podKey}/token` | SERVICE | PodTokenController | Mints the token of a pod about to start; a token the pod held before is revoked. |
| POST | `/internal/v1/gpu-pods/tokens/sweep` | SERVICE | PodTokenController | Revokes the token of every pod that no longer runs. |
| GET | `/internal/v1/jobs/{jobId}/input/image` | SERVICE, or GPU_POD (its own chain) | JobOutputController | The starting image of a job, for a worker that does not share the platform's disk. |
| GET | `/internal/v1/jobs/{jobId}/output/{filename}` | SERVICE | JobOutputController | Reads one of the actor's own outputs back (a step whose input is an earlier step's clip). |
| PUT | `/internal/v1/jobs/{jobId}/output/{filename}` | SERVICE, or GPU_POD (its own chain) | JobOutputController | Stores one output file of a job, sealed, and answers the URL it is served under. |
| POST | `/internal/v1/jobs/erase` | SERVICE | JobErasureController | Erases jobs of one person: rows, files and the durable copy. |
| GET | `/internal/v1/models/options` | SERVICE | ModelOptionsController | The published models of one category, the ones that answer first, the default marked. |
| GET | `/internal/v1/models/queue` | SERVICE | ModelOptionsController | The queue a job on a model is published with when it is not the capability's own (a hosted video model is run by the CPU worker's queue, a self-hosted one by the GPU worker's). |
| POST | `/internal/v1/workers` | SERVICE, or GPU_POD (its own chain) | WorkerController | Registers a worker, or refreshes what is known about it. |
| POST | `/internal/v1/workers/{workerName}/heartbeat` | SERVICE, or GPU_POD (its own chain) | WorkerController | Records a heartbeat from an already-registered worker. |

## knowledge-service

| Method | Path | Access | Controller | Summary |
|:---|:---|:---|:---|:---|
| POST | `/api/v1/analytics/erasures` | authenticated | AnalyticsController | Forgets subjects and items, in the analytics and the recommender's events. |
| POST | `/api/v1/analytics/events` | authenticated | AnalyticsController | Records a batch of events (idempotent by id). |
| GET | `/api/v1/analytics/funnel` | authenticated | AnalyticsController | The ordered funnel of steps over [from, to). |
| GET | `/api/v1/analytics/insights` | authenticated | AnalyticsController | KPIs, series, top items and cohorts over [from, to) (UTC dates, to excluded). |
| POST | `/api/v1/recommendations/erasures` | authenticated | RecommendationController | Forgets people and items. |
| POST | `/api/v1/recommendations/events` | authenticated | RecommendationController | Records a batch of interaction events (idempotent by id). |
| GET | `/api/v1/recommendations/for-you` | authenticated | RecommendationController | The person's "For you" feed. |
| GET | `/api/v1/recommendations/insights` | authenticated | RecommendationController | A creator's analytics. |
| POST | `/api/v1/recommendations/items` | authenticated | RecommendationController | Upserts eligible items; the ones whose text changed are embedded. |
| GET | `/api/v1/recommendations/similar` | authenticated | RecommendationController | Items like an item. |
| POST | `/internal/v1/knowledge/retrieve` | SERVICE | KnowledgeController | Semantic RAG retrieval over the vector store, among the passages the principal may read. |
| PUT | `/internal/v1/knowledge/sources/{id}/acl` | SERVICE | KnowledgeController | Replaces who may read a source; an administrator of the source's tenant only. |
| POST | `/internal/v1/knowledge/sources/search` | SERVICE | KnowledgeController | Substring search over a tool's registered RAG sources the principal may read. |

## studio-service

| Method | Path | Access | Controller | Summary |
|:---|:---|:---|:---|:---|
| POST | `/api/v1/studio-runs/{runId}/turns` | authenticated | StudioTurnController | Runs one model turn of a session. |
| GET | `/api/v1/studios` | authenticated | StudioController | The catalogue, optionally narrowed to one profession. |
| GET | `/api/v1/studios/{studioKey}` | authenticated | StudioController | One Studio's detail, including the schemas its forms are generated from. |
| GET | `/api/v1/studios/{studioKey}/blueprints` | ADMIN | StudioBlueprintController | Every version of a Studio, drafts included — the Builder's version list. |
| DELETE | `/api/v1/studios/{studioKey}/blueprints/{version}` | ADMIN | StudioBlueprintController | Deprecates a version without deleting it. |
| PUT | `/api/v1/studios/{studioKey}/blueprints/{version}` | ADMIN | StudioBlueprintController | Creates or replaces a draft version. |
| POST | `/api/v1/studios/{studioKey}/blueprints/{version}/publish` | ADMIN | StudioBlueprintController | Publishes a draft, minting it as the Studio's latest version. |
| POST | `/api/v1/studios/{studioKey}/installations` | authenticated | StudioInstallationController | Installs a Studio, pinning its newest published version. |
| POST | `/api/v1/studios/{studioKey}/policy-check` | authenticated | StudioRunController | Dry-run of the content policy: what would a run with these inputs do? Nothing is held, stored or sent to a provider. |
| POST | `/api/v1/studios/{studioKey}/runs` | authenticated | StudioRunController | Starts a run of a Studio, addressed by its key rather than by an installation. |
| GET | `/api/v1/studios/{studioKey}/versions` | authenticated | StudioController | A Studio's version history. |
| GET | `/api/v1/studios/admin/audit` | ADMIN | OperatorAuditController |  |
| GET | `/api/v1/studios/admin/runtime-config` | ADMIN | StudioRuntimeConfigController |  |
| PUT | `/api/v1/studios/admin/runtime-config/{key}` | ADMIN | StudioRuntimeConfigController |  |
| GET | `/api/v1/studios/admin/runtime-config/audit` | ADMIN | StudioRuntimeConfigController |  |
| GET | `/api/v1/studios/composer` | authenticated | StudioController | The Studios that belong in a chat composer, for this actor. |
| GET | `/api/v1/studios/installations` | authenticated | StudioInstallationController | "My Studios". |
| DELETE | `/api/v1/studios/installations/{installationId}` | authenticated | StudioInstallationController | Uninstalls, keeping the configuration for a future reinstall. |
| GET | `/api/v1/studios/installations/{installationId}` | authenticated | StudioInstallationController | One installation. |
| PATCH | `/api/v1/studios/installations/{installationId}` | authenticated | StudioInstallationController | Replaces an installation's configuration. |
| POST | `/api/v1/studios/installations/{installationId}/runs` | authenticated | StudioRunController | Starts a run. |
| POST | `/api/v1/studios/installations/{installationId}/upgrade` | authenticated | StudioInstallationController | Moves the pin to the newest published version — never automatic. |
| GET | `/api/v1/studios/packs` | authenticated | PackCatalogController | The marketplace, optionally narrowed to one shelf. |
| GET | `/api/v1/studios/packs/{packKey}` | authenticated | PackCatalogController | One Pack's detail. |
| GET | `/api/v1/studios/packs/{packKey}/consent` | authenticated | ProtectedDataController | What a person agrees to, in their language, with the verified crisis line of each region. |
| DELETE | `/api/v1/studios/packs/{packKey}/my-data` | authenticated | ProtectedDataController | Erases everything the pack holds of the caller: entries, inputs and outputs, files, installations and the consent they recorded. |
| POST | `/api/v1/studios/packs/bundles` | ADMIN | PackBundleController | Installs a bundle. |
| DELETE | `/api/v1/studios/packs/bundles/{bundleKey}` | ADMIN | PackBundleController | Removes a bundle's catalogue rows and withdraws its pack from sale. |
| POST | `/api/v1/studios/packs/bundles/validation` | ADMIN | PackBundleController | Checks a bundle against this platform without writing anything. |
| GET | `/api/v1/studios/packs/categories` | authenticated | PackCatalogController | The shelves themselves, so the client renders headings it did not have to invent. |
| GET | `/api/v1/studios/public/downloads` | authenticated | PublicSettingsController |  |
| GET | `/api/v1/studios/runs` | authenticated | StudioRunController | This actor's run history. |
| DELETE | `/api/v1/studios/runs/{runId}` | authenticated | ProtectedDataController | Erases one entry of the caller's: a run of a protected pack, with its inputs, outputs and files. |
| GET | `/api/v1/studios/runs/{runId}` | authenticated | StudioRunController | One run with its per-step states and outputs. |
| POST | `/api/v1/studios/runs/{runId}/approve` | authenticated | StudioRunController | Resolves an approval step, resuming the run. |
| POST | `/api/v1/studios/runs/{runId}/cancel` | authenticated | StudioRunController | Cancels a run and releases its hold. |
| POST | `/api/v1/studios/runs/{runId}/deny` | authenticated | StudioRunController | Refuses the approval a run waits on: the run stops without the step and its hold is released (sovkora-studio-service#115). |
| GET | `/api/v1/studios/runs/{runId}/events` | authenticated | StudioRunController | Follows a run as Server-Sent Events instead of polling it (sovkora#189, ADR-083). |
| POST | `/api/v1/studios/runs/{runId}/resume` | authenticated | StudioRunController | Starts a new run from a step of a finished one (sovkora-studio-service#63): the steps before it keep their recorded outputs and only the re-run steps are billed. |
| POST | `/api/v1/studios/runs/{runId}/send` | authenticated | StudioRunController | Answers a run that waits for input: the message is the parked step's output and the run goes on (sovkora-studio-service#115). |
| GET | `/api/v1/studios/runs/events` | authenticated | StudioRunController | Follows every run of the caller on one connection (the per-key stream of sovkora#189): event: run whenever a run in flight changes, event: done when one ends, each carrying the run as GET /runs/runId would. |
| GET | `/api/v1/studios/webhooks` | authenticated | WebhookEndpointController | The caller's endpoints (no secrets). |
| POST | `/api/v1/studios/webhooks` | authenticated | WebhookEndpointController | Creates an endpoint. |
| DELETE | `/api/v1/studios/webhooks/{id}` | authenticated | WebhookEndpointController | Removes an endpoint and its log. |
| GET | `/api/v1/studios/webhooks/{id}/deliveries` | authenticated | WebhookEndpointController | The delivery log of an endpoint, newest first. |
| POST | `/api/v1/studios/webhooks/{id}/rotate` | authenticated | WebhookEndpointController | Issues a new secret. |
| GET | `/internal/v1/installations/{installationId}/turn-declarations` | SERVICE | InstallationDeclarationController | What a turn taken under this installation must carry: the pack's guards, the reviewed crisis reply for the installation's region, and the pack's system prompt. |
