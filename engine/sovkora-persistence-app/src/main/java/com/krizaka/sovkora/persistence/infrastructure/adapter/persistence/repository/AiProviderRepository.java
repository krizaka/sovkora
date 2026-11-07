package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository;

import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.AiProviderEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** JPA Repository for accessing ai_providers table. */
@Repository
public interface AiProviderRepository extends JpaRepository<AiProviderEntity, Integer> {

  /**
   * Finds a provider by its unique name.
   *
   * @param name The provider's unique name.
   * @return Optional containing the AiProviderEntity if found.
   */
  Optional<AiProviderEntity> findByName(String name);
}
