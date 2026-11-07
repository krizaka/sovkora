package com.krizaka.sovkora.persistence.domain.model;

import java.util.Objects;

/**
 * Persistence DTO for a pipeline-interceptor configuration row — the persistence-neutral shape
 * exposed by {@link com.krizaka.sovkora.persistence.domain.ports.inbound.PipelineConfigManager}.
 */
public record InterceptorConfigDto(
    String interceptorKey,
    String displayLabel,
    int executionOrder,
    boolean enabled,
    String description) {
  public InterceptorConfigDto {
    Objects.requireNonNull(interceptorKey, "interceptorKey cannot be null");
  }
}
