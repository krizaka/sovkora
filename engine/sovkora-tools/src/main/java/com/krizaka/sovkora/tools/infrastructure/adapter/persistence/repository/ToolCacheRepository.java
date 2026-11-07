package com.krizaka.sovkora.tools.infrastructure.adapter.persistence.repository;

import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity.ToolCacheEntity;
import com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity.ToolCacheId;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** JPA Repository for performing database operations on {@link ToolCacheEntity}. */
@Repository
public interface ToolCacheRepository extends JpaRepository<ToolCacheEntity, ToolCacheId> {

  /**
   * Resolves a cached tool execution result by tool ID and cache key.
   *
   * @param toolId The unique ID of the tool.
   * @param cacheKey The key identifier for the cached execution argument.
   * @return An Optional containing the cache entity, if any.
   */
  Optional<ToolCacheEntity> findByIdToolIdAndIdCacheKey(String toolId, String cacheKey);

  /**
   * Deletes a cached entry by tool ID and cache key.
   *
   * @param toolId The unique ID of the tool.
   * @param cacheKey The key identifier for the cached execution argument.
   */
  void deleteByIdToolIdAndIdCacheKey(String toolId, String cacheKey);
}
