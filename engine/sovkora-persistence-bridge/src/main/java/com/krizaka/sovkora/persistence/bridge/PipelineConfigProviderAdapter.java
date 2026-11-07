package com.krizaka.sovkora.persistence.bridge;

import com.krizaka.sovkora.core.domain.model.InterceptorConfig;
import com.krizaka.sovkora.core.domain.ports.outbound.PipelineConfigProvider;
import com.krizaka.sovkora.persistence.domain.ports.inbound.PipelineConfigManager;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Router-layer adapter implementing the core {@link PipelineConfigProvider} outbound port by
 * delegating to the persistence {@link PipelineConfigManager} inbound port — all DB access stays in
 * the persistence module; this adapter only maps the persistence DTO ↔ the core record.
 */
@Service
class PipelineConfigProviderAdapter implements PipelineConfigProvider {

  private final PipelineConfigManager pipelineConfigManager;

  PipelineConfigProviderAdapter(PipelineConfigManager pipelineConfigManager) {
    this.pipelineConfigManager =
        Objects.requireNonNull(pipelineConfigManager, "PipelineConfigManager cannot be null");
  }

  @Override
  public List<InterceptorConfig> findAllOrdered() {
    return pipelineConfigManager.findAllOrdered().stream()
        .map(PipelineInterceptorConfigMapper::toRecord)
        .toList();
  }

  @Override
  public InterceptorConfig save(InterceptorConfig config) {
    return PipelineInterceptorConfigMapper.toRecord(
        pipelineConfigManager.save(PipelineInterceptorConfigMapper.toDto(config)));
  }

  @Override
  public List<InterceptorConfig> saveAll(List<InterceptorConfig> configs) {
    return pipelineConfigManager
        .saveAll(configs.stream().map(PipelineInterceptorConfigMapper::toDto).toList())
        .stream()
        .map(PipelineInterceptorConfigMapper::toRecord)
        .toList();
  }

  @Override
  public void resetToDefaults(List<InterceptorConfig> defaults) {
    pipelineConfigManager.resetToDefaults(
        defaults.stream().map(PipelineInterceptorConfigMapper::toDto).toList());
  }
}
