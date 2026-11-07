package com.krizaka.sovkora.persistence.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.krizaka.sovkora.persistence.domain.ports.inbound.CatalogModelManager;
import com.krizaka.sovkora.persistence.domain.ports.inbound.RuntimeConfigProvider;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.CatalogModelEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.RuntimeConfigEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.CatalogModelRepository;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.RuntimeConfigRepository;
import com.krizaka.sovkora.test.AbstractContainerIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** Policy order and sovereign eligibility come from live rows, not process-local defaults. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  RuntimeConfigProviderImpl.class,
  CatalogModelManagerImpl.class,
  RoutingConfigurationIT.CacheConfiguration.class
})
class RoutingConfigurationIT extends AbstractContainerIntegrationTest {

  @Autowired private RuntimeConfigRepository configs;
  @Autowired private CatalogModelRepository models;
  @Autowired private RuntimeConfigProvider runtime;
  @Autowired private CatalogModelManager catalog;
  @Autowired private EntityManager entityManager;

  @Test
  void policyRowsKeepIndependentOrderAndReadAdminEdits() {
    String capability = "routing.integration.generate.";
    savePolicy(capability + "SOVEREIGN_LOCAL_ONLY", "[\"local-primary\",\"local-secondary\"]");
    savePolicy(capability + "COST_OPTIMIZED", "[\"cheap\",\"local-primary\"]");
    savePolicy(capability + "LATENCY_FIRST", "[\"fast\",\"local-primary\"]");

    assertEquals(
        "[\"local-primary\",\"local-secondary\"]",
        runtime.getString(capability + "SOVEREIGN_LOCAL_ONLY", null));
    assertEquals(
        "[\"cheap\",\"local-primary\"]", runtime.getString(capability + "COST_OPTIMIZED", null));
    assertEquals(
        "[\"fast\",\"local-primary\"]", runtime.getString(capability + "LATENCY_FIRST", null));

    entityManager
        .createNativeQuery(
            "UPDATE orazaka_runtime_config SET config_value = :value WHERE config_key = :key")
        .setParameter("value", "[\"local-secondary\",\"local-primary\"]")
        .setParameter("key", capability + "SOVEREIGN_LOCAL_ONLY")
        .executeUpdate();
    entityManager.clear();

    assertEquals(
        "[\"local-secondary\",\"local-primary\"]",
        runtime.getString(capability + "SOVEREIGN_LOCAL_ONLY", null));
    assertNull(runtime.getString("routing.integration.missing.SOVEREIGN_LOCAL_ONLY", null));
  }

  @Test
  void catalogLocationAndAvailabilityEditsAreNotHiddenByACache() {
    var model = new CatalogModelEntity();
    model.setModelName("routing-integration-model");
    model.setModelLabel("Routing integration model");
    model.setCategory("chat");
    model.setRunsOn("local");
    model.setStatus("available");
    models.saveAndFlush(model);
    assertEquals(
        "local",
        catalog.getAllModels().stream()
            .filter(m -> model.getModelName().equals(m.modelName()))
            .findFirst()
            .orElseThrow()
            .runsOn());

    entityManager
        .createNativeQuery(
            "UPDATE orazaka_models SET runs_on = 'provider', status = 'planned' WHERE id = :id")
        .setParameter("id", model.getId())
        .executeUpdate();
    entityManager.clear();

    var updated =
        catalog.getAllModels().stream()
            .filter(m -> model.getModelName().equals(m.modelName()))
            .findFirst()
            .orElseThrow();
    assertEquals("provider", updated.runsOn());
    assertEquals("planned", updated.status());
  }

  private void savePolicy(String key, String value) {
    var row = new RuntimeConfigEntity();
    row.setConfigKey(key);
    row.setConfigValue(value);
    row.setValueType("JSON");
    configs.saveAndFlush(row);
  }

  @TestConfiguration
  @EnableCaching
  static class CacheConfiguration {
    @Bean
    public CacheManager cacheManager() {
      return new ConcurrentMapCacheManager("catalogModels", "catalogModelDefault");
    }
  }
}
