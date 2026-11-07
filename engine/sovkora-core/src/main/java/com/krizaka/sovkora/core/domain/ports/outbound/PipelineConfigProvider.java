package com.krizaka.sovkora.core.domain.ports.outbound;

import com.krizaka.sovkora.core.domain.model.InterceptorConfig;
import java.util.List;

/**
 * Outbound port for pipeline interceptor configuration persistence.
 *
 * <p>Implemented by {@code sovkora-persistence-app} to read/write interceptor execution order and
 * enabled state from PostgreSQL.
 */
public interface PipelineConfigProvider {

  /**
   * Retrieves all interceptor configurations ordered by execution order.
   *
   * @return Ordered list of interceptor configurations.
   */
  List<InterceptorConfig> findAllOrdered();

  /**
   * Persists a single interceptor configuration update.
   *
   * @param config The interceptor configuration to save.
   * @return The saved configuration.
   */
  InterceptorConfig save(InterceptorConfig config);

  /**
   * Bulk-persists a list of interceptor configurations (typically after reorder).
   *
   * @param configs The list of configurations to save.
   * @return The saved configurations.
   */
  List<InterceptorConfig> saveAll(List<InterceptorConfig> configs);

  /**
   * Resets the pipeline configuration to the provided defaults.
   *
   * @param defaults The default interceptor configurations to restore.
   */
  void resetToDefaults(List<InterceptorConfig> defaults);
}
