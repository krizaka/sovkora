package com.krizaka.sovkora.persistence.infrastructure.config;

/**
 * Canonical AMQP topology contract (AGENTS.md §6): topic exchanges {@code orazaka.jobs} / {@code
 * orazaka.events}, routing keys {@code job.{capability}.{action}} / {@code evt.{aggregate}.{type}},
 * job lifecycle events {@code job.{jobId}.progress|done|error}, and one DLQ per queue ({@code
 * <queue>.dlq}) behind the shared {@code orazaka.dlx}.
 *
 * <p>Consumers living outside this Maven reactor (the integrations worker app, the Python media
 * worker) deliberately duplicate the subset of this contract they bind to — after the service split
 * there is no shared jar, so the AMQP contract tests are what keep the copies honest.
 */
public final class MessagingContract {

  // ── Exchanges ────────────────────────────────────────────────────────────
  public static final String JOBS_EXCHANGE = "orazaka.jobs";
  public static final String EVENTS_EXCHANGE = "orazaka.events";
  public static final String DLX_EXCHANGE = "orazaka.dlx";

  // ── Job commands — job.{capability}.{action} ─────────────────────────────
  public static final String JOB_MEDIA_GENERATE = "job.media.generate";

  /**
   * Media ANALYSIS commands — the interactive lane's media key (ADR-067).
   *
   * <p>Analysis used to share {@code job.media.generate} with image generation, which is what made
   * a lane impossible: one key cannot feed two queues, and on this machine those two capabilities
   * are p50 4.9 s and p50 67 s. Same grammar, different action — {@code job.{capability}.{action}}
   * is unchanged (AGENTS.md §6).
   */
  public static final String JOB_MEDIA_ANALYZE = "job.media.analyze";

  /** Video generation commands — consumed by the Python media worker (Phase 3b). */
  public static final String JOB_VIDEO_GENERATE = "job.video.generate";

  public static final String JOB_TEXT_PROCESS = "job.text.process";
  public static final String JOB_AUTOMATION_APPROVED = "job.automation.approved";

  /** Command dispatched to a specific user's CLI agent: {@code job.agent.dispatch.{userId}}. */
  public static final String JOB_AGENT_DISPATCH_PREFIX = "job.agent.dispatch.";

  // ── Domain events — evt.{aggregate}.{type} ───────────────────────────────
  public static final String EVT_USER_REGISTERED = "evt.user.registered";

  /**
   * A capability row changed — enabled, disabled, or re-routed (ADR-038).
   *
   * <p>Producers cache a capability's route on the dispatch hot path. Without this event their only
   * bound is a TTL, which makes {@code is_enabled = false} an eventual kill switch rather than an
   * immediate one — tolerable for cost, not for a REGULATED pack that must stop serving on the
   * instant it is withdrawn.
   */
  public static final String EVT_CAPABILITY_CHANGED = "evt.capability.changed";

  public static final String EVT_PASSWORD_RESET = "evt.password.reset";

  /** Execution report emitted by a user's CLI agent: {@code evt.agent.result.{userId}}. */
  public static final String EVT_AGENT_RESULT_PREFIX = "evt.agent.result.";

  /** CLI agent presence heartbeat (ONLINE/OFFLINE carried in the payload). */
  public static final String EVT_AGENT_PRESENCE = "evt.agent.presence";

  // ── Lanes: queues bound to orazaka.jobs ──────────────────────────────────
  // Two queues and two consumer pools, never AMQP priorities (ADR-067). `x-max-priority` reorders
  // WAITING messages at fetch and preempts nothing: with prefetch=1 and a 67-second image in
  // flight, no priority frees the consumer, so priority solves the blocking only in the case where
  // it does not occur. A lane is a binding and a queue — which `worker.yaml` has supported since
  // ADR-038 — and it buys FAIRNESS between tenants, not speed: a second lane is not a second
  // accelerator, it moves the wait onto whoever asked for the long work.

  /** Work whose duration is bounded by the model's own speed — a turn, one image to describe. */
  public static final String JOBS_INTERACTIVE_QUEUE = "orazaka.jobs.interactive";

  public static final String JOBS_INTERACTIVE_TEXT_BINDING = "job.text.*";
  public static final String JOBS_INTERACTIVE_MEDIA_BINDING = JOB_MEDIA_ANALYZE;
  public static final String JOBS_INTERACTIVE_DLQ = JOBS_INTERACTIVE_QUEUE + ".dlq";

  /** Work whose duration follows what the user supplied or asked to produce. */
  public static final String JOBS_BATCH_QUEUE = "orazaka.jobs.batch";

  public static final String JOBS_BATCH_BINDING = JOB_MEDIA_GENERATE;
  public static final String JOBS_BATCH_DLQ = JOBS_BATCH_QUEUE + ".dlq";

  public static final String JOBS_VIDEO_QUEUE = "orazaka.jobs.video";
  public static final String JOBS_VIDEO_BINDING = "job.video.*";
  public static final String JOBS_VIDEO_DLQ = JOBS_VIDEO_QUEUE + ".dlq";

  // ── Queues bound to orazaka.events ───────────────────────────────────────
  /**
   * Relay queue feeding the SSE job streams. Binds every {@code job.{jobId}.*} lifecycle event;
   * {@code evt.*} keys deliberately do not match. Becomes an exclusive per-node queue when the
   * relay moves behind the edge gateway.
   */
  public static final String EVENTS_JOB_RELAY_QUEUE = "orazaka.events.job-relay";

  public static final String EVENTS_JOB_RELAY_BINDING = "job.#";
  public static final String EVENTS_JOB_RELAY_DLQ = EVENTS_JOB_RELAY_QUEUE + ".dlq";

  // ── Job lifecycle event keys — job.{jobId}.progress|done|error ───────────
  private static final String JOB_EVENT_PREFIX = "job.";
  public static final String JOB_EVENT_PROGRESS_SUFFIX = ".progress";
  public static final String JOB_EVENT_DONE_SUFFIX = ".done";
  public static final String JOB_EVENT_ERROR_SUFFIX = ".error";

  private MessagingContract() {}

  /** Routing key of a job progress event: {@code job.{jobId}.progress}. */
  public static String jobProgressKey(String jobId) {
    return JOB_EVENT_PREFIX + jobId + JOB_EVENT_PROGRESS_SUFFIX;
  }

  /** Routing key of a job completion event: {@code job.{jobId}.done}. */
  public static String jobDoneKey(String jobId) {
    return JOB_EVENT_PREFIX + jobId + JOB_EVENT_DONE_SUFFIX;
  }

  /** Routing key of a job failure event: {@code job.{jobId}.error}. */
  public static String jobErrorKey(String jobId) {
    return JOB_EVENT_PREFIX + jobId + JOB_EVENT_ERROR_SUFFIX;
  }
}
