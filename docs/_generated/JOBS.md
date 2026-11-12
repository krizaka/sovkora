---
title: Jobs, Schedules & Automation
description: "Everything Orazaka runs without a user waiting: the job plane, its executors and workers, every scheduled task and the automation connectors — extracted from the code."
category: Core
order: 7
generated: true
---

# Jobs, Schedules & Automation

Three mechanisms run work in the background, each with one owner:

- **The job plane** (`orazaka-job-service`) — asynchronous AI work: a capability is published as a job on
  RabbitMQ and executed in process or by a worker. Studio runs and `ASYNC` intentions use it.
- **Scheduled tasks** — sweepers and probes inside each service (`@Scheduled`), configured by properties.
- **Automation** (`automation`) — connector actions (Slack, Jira, the CLI agent…) and its
  Quartz scheduler.

## The job plane

Asynchronous RabbitMQ listener that consumes task execution requests. Manages the state of jobs from PENDING → PROCESSING → COMPLETED or FAILED. Delegates feature-specific execution to the registered `JobExecutor` whose `handlerKey()` matches the capability's `handler_key` column. The executors arrive by the AutoConfiguration SPI, so an out-of-tree jar contributes one without touching this class (ADR-038).

A capability row (`orazaka_capabilities`) says **where** a job goes (`routing_key`, the queue a process drains)
and **which code** runs it (`handler_key`). Both are data: a pack adds a capability with a row, not a deploy.

| Capability | Handler | Routing key | Runs in |
|:---|:---|:---|:---|
| `orazaka.studio.media.compose` | `media.compose` | `job.compose.assemble` | a worker (see the [worker protocol](../WORKER_PROTOCOL.md)) |
| `orazaka.core.media.vision` | `image.analyze` | `job.media.analyze` | `VisionAnalysisStrategy` (in process) |
| `orazaka.core.media.audio.analysis` | `audio.analyze` | `job.media.generate` | `AudioAnalysisStrategy` (in process) |
| `orazaka.core.media.image` | `image.generate` | `job.media.generate` | `ImageGenerationStrategy` (in process) |
| `orazaka.core.media.video.analysis` | `video.analyze` | `job.media.generate` | `VideoAnalysisStrategy` (in process) |
| `orazaka.studio.media.sound` | `media.sound` | `job.sound.generate` | a worker (see the [worker protocol](../WORKER_PROTOCOL.md)) |
| `orazaka.core.chat.completion` | `text.generate` | `job.text.process` | `ChatGenerationStrategy` (in process) |
| `orazaka.core.checks.courtrecords` | `check.external` | `job.text.process` | `ExternalCheckStrategy` (in process) |
| `orazaka.core.checks.credit` | `check.external` | `job.text.process` | `ExternalCheckStrategy` (in process) |
| `orazaka.core.checks.identity` | `check.external` | `job.text.process` | `ExternalCheckStrategy` (in process) |
| `orazaka.core.media.speech` | `speech.synthesize` | `job.text.process` | `SpeechSynthesisStrategy` (in process) |
| `orazaka.core.research.grounded` | `research.grounded` | `job.text.process` | `GroundedResearchStrategy` (in process) |
| `orazaka.core.media.video` | `video.generate` | `job.video.generate` | a worker (see the [worker protocol](../WORKER_PROTOCOL.md)) |

### In-process executors

One capability's execution, in process.

| Handler key | Executor | Repository | Summary |
|:---|:---|:---|:---|
| `audio.analyze` | `AudioAnalysisStrategy` | `job` | Executes audio transcription/analysis jobs by delegating to the `AudioPreProcessor`. |
| `check.external` | `ExternalCheckStrategy` | `job` | Runs the engine's external checks as job steps (sovkora-packs#138): the capabilities `orazaka.core.checks.courtrecords`, `.credit` and `.identity`, whose rows all name the handler `check.external`. |
| `image.analyze` | `VisionAnalysisStrategy` | `job` | Executes vision/image analysis jobs using the AI chat client with vision models. |
| `image.generate` | `ImageGenerationStrategy` | `job` | Executes image generation jobs via the AI client's image endpoint. |
| `research.grounded` | `GroundedResearchStrategy` | `job` | Runs grounded research as a job step (ADR-087 slice 5): the capability `orazaka.core.research.grounded`, whose row names the handler `research.grounded`. |
| `speech.synthesize` | `SpeechSynthesisStrategy` | `job` | Executes text-to-speech synthesis jobs via the AI client's audio endpoint. |
| `text.generate` | `ChatGenerationStrategy` | `job` | Default fallback strategy for text/chat generation jobs. |
| `video.analyze` | `VideoAnalysisStrategy` | `job` | Executes video analysis jobs by delegating to the `VideoPreProcessor`. |

### Capability and worker registries

Service-to-service (`SERVICE` token): how a pack registers a capability and a worker announces itself.

| Method | Path | Summary |
|:---|:---|:---|
| DELETE | `/internal/v1/capabilities/{featureKey}` | Removes one capability. |
| PUT | `/internal/v1/capabilities/{featureKey}` | Registers or replaces one capability a pack contributes. |
| GET | `/internal/v1/capabilities/{featureKey}/route` | Resolves where one capability's work is executed. |
| POST | `/internal/v1/workers` | Registers a worker, or refreshes what is known about it. |
| POST | `/internal/v1/workers/{workerName}/heartbeat` | Records a heartbeat from an already-registered worker. |

### Following and approving jobs

| Method | Path | Access | Summary |
|:---|:---|:---|:---|
| GET | `/api/v1/jobs` | authenticated | Retrieves a paginated list of jobs for the authenticated user (or all jobs for admins). |
| DELETE | `/api/v1/jobs/{id}` | ADMIN + USER | Deletes a finished job's result: files out of every store, row marked deleted (sovkora-web#86). |
| GET | `/api/v1/jobs/{id}` | ADMIN + USER | Fetches the single, atomic state of a specific job. |
| POST | `/api/v1/jobs/{id}/progress` | ADMIN + USER | Updates progress of a running job and broadcasts it to connected users. |
| POST | `/api/v1/jobs/{jobId}/approve` | ADMIN + USER | Approves a pending automation job and dispatches its payload to the execution queue. |
| POST | `/api/v1/jobs/{jobId}/revoke` | ADMIN + USER | Revokes a pending automation job, preventing its execution. |
| GET | `/api/v1/jobs/active-connections` | ADMIN | Retrieves the current active SSE connection count. |
| POST | `/api/v1/jobs/purge` | ADMIN | Purges jobs, chat sessions and rate-limit cache for the default test accounts. |
| GET | `/api/v1/jobs/stream` | ADMIN + USER | Registers a Server-Sent Events stream for real-time job status notifications. |

## Scheduled tasks

Every `@Scheduled` method of the platform. A property in parentheses overrides the interval (or cron) in
the service's `application.yml` or environment.

| Repository | Task | Schedule | What it does |
|:---|:---|:---|:---|
| `automation` | `SweepTicker.tick` | every 15s (`orazaka.automation-service.scheduler.interval`) | The clock of the automation service: every interval it runs one sweep (schedule slots, queued runs, lost events, retention). |
| `conversation` | `JobReconciliationService.reconcileScheduled` | every 60 s (`orazaka.jobs.reconcile-interval-ms`) | Recovers jobs whose result was written to disk but whose terminal status update never reached the database (e.g. |
| `conversation` | `JobStreamService.sendHeartbeats` | every 15 s | Service managing client Server-Sent Events (SSE) connections for real-time job lifecycle updates. |
| `conversation` | `ProtectedConversationSweeper.sweep` | cron `0 45 3 * * *` (`orazaka.router.regulated-retention.cron`) | Ages out the conversation memory of regulated packs, daily: the 30 days the consent statement states, counted from the conversation's last turn (sovkora#301). |
| `engine` | `InfrastructureProber.probeVideoInfrastructure` | every 5 s (`orazaka.core.video.probing.interval-ms`) | Periodically pings the configured video port to check if the video inference engine is responsive. |
| `engine` | `InfrastructureProber.probeImageInfrastructure` | every 5 s (`orazaka.core.image.probing.interval-ms`) | Periodically pings the configured image port to check if the image inference engine (Stable Diffusion) is responsive. |
| `engine` | `LawWatchJob.run` | cron `0 0 6 * * MON` (`orazaka.law.watcher.cron`) | The scheduled run of the law watcher. |
| `engine` | `SandboxEvictionScheduler.evictExpiredSandboxes` | every 60 s | Scheduled eviction process — prunes expired or orphaned jobId sandbox contexts from memory. |
| `job` | `JobRetentionSweeper.sweep` | every 60 s (`orazaka.jobs.retention-sweep-interval`) | Purges expired jobs, then deletes any purged job's directory that came back. |
| `job` | `JobRetentionSweeper.sweepUploads` | every 3600 s (`orazaka.jobs.upload-retention-interval`) | Deletes uploads older than the ordinary retention window; hourly, listing is not free. |
| `job` | `WorkerHeartbeatSweeper.markStaleWorkers` | every 30 s (`orazaka.jobs.worker-sweeper-interval`) | Marks workers that have stopped heartbeating as `STALE`. |
| `job` | `WorkerSelfRegistration.heartbeat` | every 30 s (`orazaka.jobs.worker-heartbeat-interval`) | Registers the job service in the worker registry from its own `worker.yaml`. |
| `knowledge` | `AnalyticsRetentionSchedule.purge` | cron `0 27 3 * * *` | Runs the analytics retention sweep once a day (03:27 UTC — after the recommender's). |
| `knowledge` | `RetentionSchedule.purge` | cron `0 17 3 * * *` | Runs the retention sweep once a day (03:17 UTC — off the hour, off the other sweeps). |
| `krizaka-java` | `HoldSweeper.sweep` | every 60 s (`krizaka.billing-service.sweeper.interval`) | Time-triggered inbound adapter: releases `ACTIVE` holds that outlived their TTL. |
| `studio` | `OutcomeListenerConcurrencyManager.refreshConcurrency` | every 5 s | — |
| `studio` | `PackBootstrap.install` | every 30 s (`orazaka.studio-service.packs.bootstrap-interval`) | Installs the packs this deployment ships, so a fresh environment has Studios (ADR-068, `#45`). |
| `studio` | `RetentionSweeper.purge` | cron `0 30 3 * * *` (`orazaka.studio-service.retention.cron`) | Purges what the retention window says may no longer be kept (ADR-034 §14, GDPR / Loi 25). |
| `studio` | `RunSweeper.sweep` | every 60 s (`orazaka.studio-service.sweeper.interval`) | Closes runs whose steps stopped reporting, and releases their holds. |
| `studio` | `StalledRunSweeper.sweep` | every 60 s (`orazaka.studio-service.sweeper.interval`) | Schedules `StalledRunService`: a run with no dispatched step ends instead of hanging. |
| `studio` | `WebhookSweeper.sweep` | every 10 s (`orazaka.studio-service.webhooks.interval`) | Queues and sends the webhooks of finished runs (sovkora#189 slice B). |

## Automation

Connector actions are jobs too: a producer publishes on `job.automation.*` (exchange `orazaka.jobs`), the
automation service consumes `orazaka.jobs.automation` (failures dead-letter to `orazaka.jobs.automation.dlq`), runs the connector and
reports each state change on `evt.automation.telemetry` (exchange `orazaka.events`).

| Producer | Repository | Routing key |
|:---|:---|:---|
| `AmqpStepExecutionAdapter` | `studio` | `job.automation.approved` |

| Connector | Behaviour |
|:---|:---|
| `JIRA` | logs the action (no outbound call yet) |
| `WHATSAPP` | logs the action (no outbound call yet) |
| `MESSENGER` | logs the action (no outbound call yet) |
| `SLACK` | logs the action (no outbound call yet) |
| `CLI_AGENT` | forwarded to the user's CLI agent on `job.agent.dispatch.{userId}` |

Automation job states: `PENDING_APPROVAL` · `APPROVED` · `RUNNING` · `COMPLETED` · `FAILED` · `AWAITING_CLI_EXECUTION`.

The CLI agent runs on the user's machine and connects **outbound** (no inbound port):

```bash
orazaka login
orazaka agent listen   # Start CLI agent listener and reverse tunnel
```

| Method | Path | Access | Summary |
|:---|:---|:---|:---|
| POST | `/api/v1/agent/report` | authenticated | Receives execution reports from the CLI agent after local task completion. |
| GET | `/api/v1/agent/stream` | authenticated | Establishes a persistent SSE stream for the CLI agent. |
