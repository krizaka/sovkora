package com.krizaka.sovkora.persistence.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.krizaka.sovkora.persistence.domain.ports.inbound.FeatureRouteManager;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.FeatureModelEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.FeatureModelRepository;
import com.krizaka.sovkora.test.AbstractContainerIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

/** The routing rows are read in rank order, whatever order they were written in. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(FeatureRouteManagerImpl.class)
class FeatureRouteManagerIT extends AbstractContainerIntegrationTest {

  @Autowired private FeatureModelRepository rows;
  @Autowired private FeatureRouteManager routes;

  private void save(String feature, int rank, String model) {
    var row = new FeatureModelEntity();
    row.setFeatureKey(feature);
    row.setRank(rank);
    row.setModelName(model);
    rows.saveAndFlush(row);
  }

  @Test
  void aFeatureRoutesToItsModelsBestRankFirstAndAnotherFeatureToNothing() {
    save("agent.it.feature", 2, "second");
    save("agent.it.feature", 1, "first");
    save("agent.it.other", 1, "elsewhere");

    assertEquals(List.of("first", "second"), routes.modelNames("agent.it.feature"));
    assertEquals(List.of(), routes.modelNames("agent.it.none"));
  }
}
