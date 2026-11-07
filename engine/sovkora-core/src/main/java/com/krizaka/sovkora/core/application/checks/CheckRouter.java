package com.krizaka.sovkora.core.application.checks;

import com.krizaka.sovkora.core.domain.model.checks.CheckProviderRow;
import com.krizaka.sovkora.core.domain.model.checks.CheckPurpose;
import com.krizaka.sovkora.core.domain.ports.outbound.CheckProviderCatalog;
import com.krizaka.sovkora.core.domain.ports.outbound.ExternalCheckConnector;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Chooses the provider of a check from the admin rows (sovkora-engine#73): the enabled row that
 * serves the purpose and the region, lowest priority first, whose {@code handler_key} names a
 * registered adapter. It is data, never an {@code @Order} or a test on a provider's name; a row
 * whose handler is unknown is skipped, and none left means "not configured".
 */
public class CheckRouter {

  /**
   * The chosen provider.
   *
   * @param row the admin row
   * @param connector the adapter its handler key names
   */
  public record Route(CheckProviderRow row, ExternalCheckConnector connector) {}

  private final CheckProviderCatalog catalog;
  private final Map<String, ExternalCheckConnector> handlers;

  /**
   * @param catalog the admin rows
   * @param handlers the adapters by handler key
   */
  public CheckRouter(CheckProviderCatalog catalog, Map<String, ExternalCheckConnector> handlers) {
    this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
    this.handlers = Map.copyOf(Objects.requireNonNull(handlers, "handlers must not be null"));
  }

  /**
   * A router with one connector serving every purpose and region (a single-provider host, tests).
   *
   * @param connector the connector
   * @return the router
   */
  public static CheckRouter fixed(ExternalCheckConnector connector) {
    CheckProviderRow row =
        new CheckProviderRow(
            connector.provider(),
            connector.provider(),
            java.util.EnumSet.allOf(CheckPurpose.class),
            java.util.Set.of(),
            0,
            true,
            null,
            0);
    return new CheckRouter(() -> java.util.List.of(row), Map.of(connector.provider(), connector));
  }

  /**
   * The provider for a purpose and a region.
   *
   * @param purpose what is asked
   * @param region the subject's region, or {@code null}
   * @return the route, empty when none is configured
   */
  public Optional<Route> route(CheckPurpose purpose, String region) {
    return catalog.rows().stream()
        .filter(row -> row.serves(purpose, region))
        .filter(row -> handlers.containsKey(row.handlerKey()))
        .min(Comparator.comparingInt(CheckProviderRow::priority))
        .map(row -> new Route(row, handlers.get(row.handlerKey())));
  }

  /**
   * The route of a named provider, to read a deferred answer from the one that accepted the search.
   *
   * @param provider the provider's name as audited
   * @return the route, empty when its row is gone, disabled, or its handler unknown
   */
  public Optional<Route> routeOf(String provider) {
    return catalog.rows().stream()
        .filter(row -> row.enabled() && row.provider().equals(provider))
        .filter(row -> handlers.containsKey(row.handlerKey()))
        .findFirst()
        .map(row -> new Route(row, handlers.get(row.handlerKey())));
  }
}
