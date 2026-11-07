package com.krizaka.sovkora.engine.persistence.api.domain.ports.inbound;

import com.krizaka.sovkora.persistence.api.domain.model.ValidationPipelineConfigDto;
import java.util.List;

/**
 * Inbound port for the validation-pipeline configuration registry — the single DB entry point.
 * Encapsulates ordering and upsert-by-step-type semantics.
 */
public interface ValidationPipelineManager {

  /** Returns all validation steps ordered by execution order. */
  List<ValidationPipelineConfigDto> findAllOrderedByExecution();

  /** Upserts a single validation step (by step type) and returns the stored state. */
  ValidationPipelineConfigDto save(ValidationPipelineConfigDto config);

  /** Upserts all steps and returns the stored states. */
  List<ValidationPipelineConfigDto> saveAll(List<ValidationPipelineConfigDto> configs);
}
