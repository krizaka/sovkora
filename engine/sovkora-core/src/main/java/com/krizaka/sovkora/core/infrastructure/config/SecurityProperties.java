package com.krizaka.sovkora.core.infrastructure.config;

/**
 * Security governance configuration properties. Maps to {@code orazaka.security} in {@code
 * application.yml}.
 *
 * <p>The {@code disableAi} property acts as a hard governance kill-switch. When set to {@code
 * true}, the {@code DynamicPipelineExecutor} will throw a {@link SecurityException} if any
 * interceptor returning {@code isAiDependent() == true} is invoked.
 *
 * @param disableAi When {@code true}, blocks all AI-dependent interceptors with a hard {@link
 *     SecurityException}. This is a compliance shield, not a performance failover.
 */
public record SecurityProperties(boolean disableAi) {

  /**
   * Compact canonical constructor — the ONLY constructor, deliberately.
   *
   * <p>This record used to carry a no-argument constructor as well. With two of them, Spring's
   * constructor binding chose the no-argument one and never read the property: the kill-switch
   * could not be switched on, by yaml, by environment variable or by system property. It read
   * "inactive" in the startup log of every deployment this platform has ever had, and the log line
   * was correct — which is why nobody noticed (ADR-055 §8).
   *
   * <p>The default (off) is the boolean default; the binding lives in the Spring adapter.
   */
  public SecurityProperties {}
}
