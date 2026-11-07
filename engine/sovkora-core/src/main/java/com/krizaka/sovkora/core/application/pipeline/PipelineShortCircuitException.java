package com.krizaka.sovkora.core.application.pipeline;

import java.io.Serial;

/**
 * Thrown by an interceptor that is <b>refusing</b> the request, not failing on it.
 *
 * <p>The distinction matters because {@link DynamicPipelineExecutor} deliberately swallows
 * interceptor failures: an enrichment step that breaks must degrade the answer, never lose the
 * user's turn. That is right for enrichment and wrong for a gate — an entitlement or credit refusal
 * that were merely logged would let the request proceed exactly as if it had been allowed, which is
 * the one outcome a gate exists to prevent.
 *
 * <p>This exception is therefore the pipeline's only escape hatch: the executor re-throws it and
 * swallows everything else. Interceptors that enrich must not use it.
 *
 * <p>The {@code reason} is a machine-readable code the transport layer maps to a status —
 * deliberately not a status code itself, because the core knows nothing about HTTP.
 */
public class PipelineShortCircuitException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final String reason;
  private final String interceptorId;

  /**
   * Constructs a refusal.
   *
   * @param interceptorId the interceptor that refused, retained for audit
   * @param reason a machine-readable refusal code, e.g. {@code capability_not_in_plan}
   * @param message a human-readable explanation
   * @param cause the refusal this wraps, carrying whatever the transport layer needs to render it
   */
  public PipelineShortCircuitException(
      String interceptorId, String reason, String message, Throwable cause) {
    super(message, cause);
    this.interceptorId = interceptorId;
    this.reason = reason;
  }

  /**
   * @return the machine-readable refusal code
   */
  public String reason() {
    return reason;
  }

  /**
   * @return the interceptor that refused
   */
  public String interceptorId() {
    return interceptorId;
  }
}
