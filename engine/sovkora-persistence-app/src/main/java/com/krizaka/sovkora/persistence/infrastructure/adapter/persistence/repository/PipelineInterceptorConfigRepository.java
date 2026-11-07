package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository;

import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.PipelineInterceptorConfigEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository for pipeline interceptor configuration entries. */
public interface PipelineInterceptorConfigRepository
    extends JpaRepository<PipelineInterceptorConfigEntity, Integer> {

  /**
   * Retrieves all interceptor configurations ordered by execution order.
   *
   * @return Ordered list of pipeline interceptor config entities.
   */
  List<PipelineInterceptorConfigEntity> findAllByOrderByExecutionOrderAscIdAsc();

  /**
   * Finds a single interceptor configuration by its unique key.
   *
   * @param interceptorKey The bean class simple name.
   * @return Optional containing the entity if found.
   */
  Optional<PipelineInterceptorConfigEntity> findByInterceptorKey(String interceptorKey);
}
