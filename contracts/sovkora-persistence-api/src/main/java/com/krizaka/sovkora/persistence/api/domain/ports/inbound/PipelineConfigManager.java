package com.krizaka.sovkora.engine.persistence.api.domain.ports.inbound;

import com.krizaka.sovkora.persistence.api.domain.model.InterceptorConfigDto;
import java.util.List;

/**
 * Inbound port for the interceptor-pipeline configuration registry — the single DB entry point for
 * pipeline config. Encapsulates ordering, upsert-by-key and reset semantics.
 */
public interface PipelineConfigManager {

  /** Returns all interceptor configs ordered by execution order. */
  List<InterceptorConfigDto> findAllOrdered();

  /** Upserts a single interceptor config (by interceptor key) and returns the stored state. */
  InterceptorConfigDto save(InterceptorConfigDto config);

  /** Upserts all configs and returns the stored states. */
  List<InterceptorConfigDto> saveAll(List<InterceptorConfigDto> configs);

  /** Replaces the entire registry with the given defaults. */
  void resetToDefaults(List<InterceptorConfigDto> defaults);
}
