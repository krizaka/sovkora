package com.krizaka.sovkora.persistence.infrastructure.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.jobs.domain.model.CapabilityDeclaration;
import com.krizaka.sovkora.persistence.domain.ports.inbound.CapabilityManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CapabilityRoutingAdapterTest {

  private static CapabilityDeclaration capability(String featureKey, boolean enabled) {
    return new CapabilityDeclaration(
        featureKey,
        "handler",
        "job.media.generate",
        "IMAGE_STEP",
        "IMAGE",
        "BATCH",
        "{}",
        "{}",
        enabled);
  }

  /**
   * A stand-in registry: the rule under test is what the ROW decides, so the row is the fixture.
   */
  private static CapabilityManager registryOf(CapabilityDeclaration... rows) {
    Map<String, CapabilityDeclaration> byKey =
        List.of(rows).stream()
            .collect(java.util.stream.Collectors.toMap(CapabilityDeclaration::featureKey, r -> r));
    return new CapabilityManager() {
      @Override
      public List<CapabilityDeclaration> findAll() {
        return List.copyOf(byKey.values());
      }

      @Override
      public Optional<CapabilityDeclaration> findByFeatureKey(String featureKey) {
        return Optional.ofNullable(byKey.get(featureKey));
      }

      @Override
      public CapabilityDeclaration save(CapabilityDeclaration capability) {
        throw new UnsupportedOperationException("read-only fixture");
      }

      @Override
      public boolean delete(String featureKey) {
        throw new UnsupportedOperationException("read-only fixture");
      }
    };
  }

  @Test
  @DisplayName("Maps the row onto the Tier-1 route, whole")
  void mapsTheRow() {
    CapabilityRoutingAdapter adapter =
        new CapabilityRoutingAdapter(registryOf(capability("orazaka.core.chat.completion", true)));

    assertThat(adapter.route("orazaka.core.chat.completion"))
        .hasValueSatisfying(
            route -> {
              assertThat(route.featureKey()).isEqualTo("orazaka.core.chat.completion");
              assertThat(route.routingKey()).isEqualTo("job.media.generate");
              assertThat(route.billableUnit()).isEqualTo("IMAGE_STEP");
              assertThat(route.enabled()).isTrue();
            });
  }

  @Test
  @DisplayName("A disabled capability has NO route — not a route flagged disabled")
  void aDisabledCapabilityHasNoRoute() {
    CapabilityRoutingAdapter adapter =
        new CapabilityRoutingAdapter(registryOf(capability("orazaka.core.chat.completion", false)));

    // Returning the row and leaving the caller to check enabled() would make "did anyone remember
    // to look?" the property that decides whether disabled work still gets dispatched.
    assertThat(adapter.route("orazaka.core.chat.completion")).isEmpty();
  }

  @Test
  @DisplayName("An unknown or blank capability resolves to empty, never to a default")
  void unknownCapabilityIsEmpty() {
    CapabilityRoutingAdapter adapter = new CapabilityRoutingAdapter(registryOf());

    assertThat(adapter.route("nothing.here")).isEmpty();
    assertThat(adapter.route(" ")).isEmpty();
    assertThat(adapter.route(null)).isEmpty();
  }
}
