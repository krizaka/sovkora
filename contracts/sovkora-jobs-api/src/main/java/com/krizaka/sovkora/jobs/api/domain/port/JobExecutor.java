package com.krizaka.sovkora.jobs.api.domain.port;

import com.krizaka.sovkora.jobs.api.domain.exception.JobExecutionException;
import com.krizaka.sovkora.jobs.api.domain.model.JobCommand;
import com.krizaka.sovkora.jobs.api.domain.model.JobExecutionContext;
import com.krizaka.sovkora.jobs.api.domain.model.JobExecutionResult;

/**
 * One capability's execution, in process.
 *
 * <p>The convenience half of the worker SPI. The <b>extension point</b> is the AMQP contract
 * (`docs/WORKER_PROTOCOL.md`): a worker is anything that honours the message contract, in any
 * language, linking nothing — which is already true of the Python media worker. This interface
 * exists for executors that are cheap to run inside the job service rather than as their own
 * process, and it is deliberately not the mechanism (ADR-037 §3.4, ADR-038).
 *
 * <p><b>Discovered, not registered by hand.</b> Implementations are contributed through {@code
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}, exactly as
 * {@code sovkora-engine-interceptors} contributes its pipeline [ERR-122]. An out-of-tree jar on the
 * classpath contributes an executor with no edit to this repository — that is the property this
 * interface exists to provide, and the one its acceptance test asserts.
 *
 * <p><b>Tier-1, and therefore pure.</b> No Spring, no file I/O, no engine types. An implementation
 * may use all three; the contract may not, or an executor could not be written outside a repository
 * that ships the engine.
 */
public interface JobExecutor {

  /**
   * The dispatch discriminant: which code path runs.
   *
   * <p>Matched against {@code orazaka_capabilities.handler_key}. It must be unique across every
   * registered executor — the listener refuses to start on a collision, because a silently shadowed
   * executor is a job that runs the wrong code.
   *
   * <p>Distinct from the capability's {@code routing_key}, which decides <b>which process</b>
   * receives the message. Two discriminants, two failure modes, both guarded (ADR-038).
   *
   * @return the stable handler key, e.g. {@code image.generate}
   */
  String handlerKey();

  /**
   * Executes one job.
   *
   * @param command the job to execute, with its typed payload accessors
   * @param context who it runs as, and under what preferences
   * @return the result and whatever the execution measured
   * @throws JobExecutionException when the job cannot be completed — the listener turns this into a
   *     {@code job.{jobId}.error} event and releases the hold, so a failed job is never billed
   */
  JobExecutionResult execute(JobCommand command, JobExecutionContext context)
      throws JobExecutionException;
}
