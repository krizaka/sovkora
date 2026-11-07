package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository;

import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.OutboxEventEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link OutboxEventEntity}. */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

  /**
   * Locks and returns the next unpublished events whose backoff has elapsed. {@code FOR UPDATE SKIP
   * LOCKED} lets concurrent relay instances drain the table without contention (must be executed
   * inside a transaction).
   */
  @Query(
      value =
          "SELECT * FROM outbox_events "
              + "WHERE published_at IS NULL AND next_attempt_at <= CURRENT_TIMESTAMP "
              + "ORDER BY created_at LIMIT :batchSize FOR UPDATE SKIP LOCKED",
      nativeQuery = true)
  List<OutboxEventEntity> lockPendingBatch(@Param("batchSize") int batchSize);

  long deleteByPublishedAtBefore(Instant cutoff);
}
