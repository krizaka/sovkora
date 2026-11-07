package com.krizaka.sovkora.automationservice.infrastructure.config;

/**
 * AMQP topology constants for the external services worker (AGENTS.md §6).
 *
 * <p>Deliberately duplicates the subset of the platform messaging contract this worker binds to
 * (topic exchanges {@code orazaka.jobs} / {@code orazaka.events}): after the service split there is
 * no shared jar, so the AMQP contract tests keep this copy honest.
 */
public final class AmqpConstants {

  // ── Exchanges ────────────────────────────────────────────────────────────
  public static final String JOBS_EXCHANGE = "orazaka.jobs";
  public static final String EVENTS_EXCHANGE = "orazaka.events";
  public static final String DLX_EXCHANGE = "orazaka.dlx";

  // ── Automation jobs (consumed) — job.automation.{action} ─────────────────
  public static final String AUTOMATION_QUEUE = "orazaka.jobs.automation";
  public static final String AUTOMATION_BINDING = "job.automation.*";
  public static final String AUTOMATION_DLQ = AUTOMATION_QUEUE + ".dlq";

  // ── Studio run outcomes (consumed) — evt.studio.run.{succeeded,failed} ───
  /** The Studio service announces a run that succeeded. */
  public static final String STUDIO_RUN_SUCCEEDED = "evt.studio.run.succeeded";

  /** The Studio service announces a run that failed. */
  public static final String STUDIO_RUN_FAILED = "evt.studio.run.failed";

  /** Where this service hears of them, to close the runs that wait on a Studio run (ADR-084 §3). */
  public static final String STUDIO_RUNS_QUEUE = "orazaka.automation.studio-runs";

  public static final String STUDIO_RUNS_DLQ = STUDIO_RUNS_QUEUE + ".dlq";

  // ── Published keys ────────────────────────────────────────────────────────
  /** Execution telemetry emitted on {@code orazaka.events}. */
  public static final String TELEMETRY_ROUTING_KEY = "evt.automation.telemetry";

  /** Command dispatched to a specific user's CLI agent: {@code job.agent.dispatch.{userId}}. */
  public static final String AGENT_DISPATCH_PREFIX = "job.agent.dispatch.";

  private AmqpConstants() {}
}
