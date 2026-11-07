package com.krizaka.sovkora.core.domain.model;

/**
 * Routing requirements for one host-owned step.
 *
 * @param stepId nonblank identifier of the step
 * @param capability nonblank capability to resolve
 * @param policy routing policy; {@code null} means sovereign-local-only
 */
public record StepRoutingRequest(String stepId, String capability, RoutingPolicy policy) {

  /**
   * Creates a request, defaulting a missing policy to sovereign-local-only.
   *
   * @throws IllegalArgumentException if the step identifier or capability is null or blank
   */
  public StepRoutingRequest {
    if (stepId == null || stepId.isBlank()) {
      throw new IllegalArgumentException("stepId must not be blank");
    }
    if (capability == null || capability.isBlank()) {
      throw new IllegalArgumentException("capability must not be blank");
    }
    if (policy == null) {
      policy = RoutingPolicy.SOVEREIGN_LOCAL_ONLY;
    }
  }

  /**
   * Creates a sovereign-local-only request.
   *
   * @param stepId nonblank identifier of the step
   * @param capability nonblank capability to resolve
   */
  public StepRoutingRequest(String stepId, String capability) {
    this(stepId, capability, RoutingPolicy.SOVEREIGN_LOCAL_ONLY);
  }
}
