package com.krizaka.sovkora.core.domain.ports.inbound;

import com.krizaka.sovkora.core.domain.model.StepModelUnavailableException;
import com.krizaka.sovkora.core.domain.model.StepRoutingRequest;
import com.krizaka.sovkora.core.domain.ports.outbound.StepModelRecorder;
import java.util.function.Function;

/** Executes one step against only its configured, policy-eligible model candidates. */
public interface StepRouter {

  /**
   * Records each selected model before invoking it, preserving the configured attempt order.
   *
   * @param <T> invocation result type
   * @param request nonnull step routing requirements
   * @param recorder mandatory bridge to host-owned step persistence
   * @param invocation nonnull model invocation; only {@link StepModelUnavailableException} permits
   *     fallback to the next eligible candidate
   * @return invocation result, including null if returned by the invocation
   * @throws StepModelUnavailableException if no eligible candidate succeeds, retaining the last
   *     invocation unavailability failure as its cause, if any
   * @throws NullPointerException if any argument is null
   */
  <T> T execute(
      StepRoutingRequest request, StepModelRecorder recorder, Function<String, T> invocation);
}
