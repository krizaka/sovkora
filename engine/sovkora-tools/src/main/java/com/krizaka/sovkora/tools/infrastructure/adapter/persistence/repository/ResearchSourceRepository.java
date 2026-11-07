package com.krizaka.sovkora.tools.infrastructure.adapter.persistence.repository;

import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity.ResearchSourceEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Research sources by key. */
public interface ResearchSourceRepository extends JpaRepository<ResearchSourceEntity, UUID> {

  /**
   * A source by its key.
   *
   * @param sourceKey the key
   * @return the source
   */
  Optional<ResearchSourceEntity> findBySourceKey(String sourceKey);
}
