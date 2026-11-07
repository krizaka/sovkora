package com.krizaka.sovkora.core.application.routing;

import com.krizaka.sovkora.core.domain.model.RoutingPolicy;
import com.krizaka.sovkora.core.domain.model.StepModelCandidate;
import com.krizaka.sovkora.core.domain.model.StepModelUnavailableException;
import com.krizaka.sovkora.core.domain.model.StepRoutingRequest;
import com.krizaka.sovkora.core.domain.ports.inbound.StepRouter;
import com.krizaka.sovkora.core.domain.ports.outbound.StepModelRecorder;
import com.krizaka.sovkora.core.domain.ports.outbound.StepRouteProvider;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Stateless step router that fails closed when no explicitly configured model is eligible. */
public class PolicyStepRouter implements StepRouter {

  private final StepRouteProvider routeProvider;

  /**
   * Creates a router backed by live host configuration.
   *
   * @param routeProvider nonnull provider of ordered, explicitly declared routes
   */
  public PolicyStepRouter(StepRouteProvider routeProvider) {
    this.routeProvider = Objects.requireNonNull(routeProvider, "routeProvider");
  }

  /** {@inheritDoc} */
  @Override
  public <T> T execute(
      StepRoutingRequest request, StepModelRecorder recorder, Function<String, T> invocation) {
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(recorder, "recorder");
    Objects.requireNonNull(invocation, "invocation");
    List<StepModelCandidate> candidates =
        routeProvider.candidates(request.capability(), request.policy());
    StepModelUnavailableException lastFailure = null;
    if (candidates != null) {
      for (StepModelCandidate candidate : candidates) {
        if (candidate == null
            || !candidate.available()
            || (request.policy() == RoutingPolicy.SOVEREIGN_LOCAL_ONLY
                && !candidate.sovereignLocal())) {
          continue;
        }
        recorder.recordModel(request.stepId(), candidate.modelName());
        try {
          return invocation.apply(candidate.modelName());
        } catch (StepModelUnavailableException unavailable) {
          lastFailure = unavailable;
        }
      }
    }
    throw new StepModelUnavailableException(
        "No available model for step "
            + request.stepId()
            + " and capability "
            + request.capability(),
        lastFailure);
  }
}
