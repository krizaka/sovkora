package com.krizaka.sovkora.engine.persistence.bridge;

import com.krizaka.sovkora.engine.core.domain.model.CapabilityDescriptor;
import com.krizaka.sovkora.jobs.api.domain.model.CapabilityDeclaration;

/** Package-private static mapper: persistence capability DTO → core domain descriptor. */
final class CapabilityMapper {

  private CapabilityMapper() {}

  /**
   * Maps a persistence DTO to a core domain descriptor.
   *
   * @param dto The persistence capability DTO.
   * @return The core domain descriptor.
   */
  static CapabilityDescriptor toDescriptor(CapabilityDeclaration dto) {
    return new CapabilityDescriptor(dto.featureKey(), dto.enabled());
  }
}
