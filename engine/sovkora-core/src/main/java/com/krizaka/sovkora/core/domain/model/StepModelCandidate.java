package com.krizaka.sovkora.core.domain.model;

/**
 * One explicitly configured model candidate.
 *
 * @param modelName nonblank model name passed unchanged to the invocation
 * @param sovereignLocal true only when the host positively identifies the model as sovereign-local;
 *     hosted or unknown models must use false
 * @param available whether the host currently considers the model available
 */
public record StepModelCandidate(String modelName, boolean sovereignLocal, boolean available) {

  /**
   * Creates a model candidate.
   *
   * @throws IllegalArgumentException if the model name is null or blank
   */
  public StepModelCandidate {
    if (modelName == null || modelName.isBlank()) {
      throw new IllegalArgumentException("modelName must not be blank");
    }
  }
}
