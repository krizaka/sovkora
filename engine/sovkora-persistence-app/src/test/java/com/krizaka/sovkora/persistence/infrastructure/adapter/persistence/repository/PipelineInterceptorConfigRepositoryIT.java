package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.PipelineInterceptorConfigEntity;
import com.krizaka.sovkora.test.AbstractContainerIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

/**
 * Two interceptors on the same {@code execution_order} run in the order their rows were created —
 * the chain is data, and a tie must not depend on what the database happens to return.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("pipeline_interceptor_config — a tie on execution_order is broken by creation")
class PipelineInterceptorConfigRepositoryIT extends AbstractContainerIntegrationTest {

  @Autowired private PipelineInterceptorConfigRepository repository;

  private PipelineInterceptorConfigEntity row(String key, int order) {
    PipelineInterceptorConfigEntity e = new PipelineInterceptorConfigEntity();
    e.setInterceptorKey(key);
    e.setDisplayLabel(key);
    e.setExecutionOrder(order);
    return e;
  }

  @Test
  @DisplayName("same order: the first row created runs first, whatever its key")
  void tieBrokenByCreation() {
    repository.deleteAll();
    repository.saveAndFlush(row("Zeta", 8));
    repository.saveAndFlush(row("Alpha", 8));
    repository.saveAndFlush(row("Early", 7));

    List<String> keys =
        repository.findAllByOrderByExecutionOrderAscIdAsc().stream()
            .map(PipelineInterceptorConfigEntity::getInterceptorKey)
            .toList();

    assertEquals(List.of("Early", "Zeta", "Alpha"), keys);
  }
}
