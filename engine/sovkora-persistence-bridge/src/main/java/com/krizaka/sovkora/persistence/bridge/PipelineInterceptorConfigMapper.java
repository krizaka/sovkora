package com.krizaka.sovkora.persistence.bridge;

import com.krizaka.sovkora.core.domain.model.InterceptorConfig;
import com.krizaka.sovkora.persistence.domain.model.InterceptorConfigDto;

/** Package-private static mapper: persistence interceptor-config DTO ↔ core domain record. */
final class PipelineInterceptorConfigMapper {

  private PipelineInterceptorConfigMapper() {}

  static InterceptorConfig toRecord(InterceptorConfigDto dto) {
    return new InterceptorConfig(
        dto.interceptorKey(),
        dto.displayLabel(),
        dto.executionOrder(),
        dto.enabled(),
        dto.description());
  }

  static InterceptorConfigDto toDto(InterceptorConfig config) {
    return new InterceptorConfigDto(
        config.interceptorKey(),
        config.displayLabel(),
        config.executionOrder(),
        config.enabled(),
        config.description());
  }
}
