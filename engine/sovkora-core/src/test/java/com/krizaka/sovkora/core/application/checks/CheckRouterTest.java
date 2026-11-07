package com.krizaka.sovkora.core.application.checks;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.core.domain.model.checks.CheckProviderRow;
import com.krizaka.sovkora.core.domain.model.checks.CheckPurpose;
import com.krizaka.sovkora.core.infrastructure.checks.MockConnector;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CheckRouterTest {

  private static CheckProviderRow row(
      String provider, Set<CheckPurpose> purposes, Set<String> regions, int priority, boolean on) {
    return new CheckProviderRow(
        provider, provider, purposes, regions, priority, on, "https://x.test", 1);
  }

  private static final Set<CheckPurpose> COURT = Set.of(CheckPurpose.COURT_RECORDS);

  private static CheckRouter router(List<CheckProviderRow> rows) {
    MockConnector any = new MockConnector();
    return new CheckRouter(
        () -> rows, Map.of("soquij", any, "certn", any, "trustii", any, "mock", any));
  }

  @Test
  @DisplayName("a Québec subject goes to SOQUIJ, the others to Certn, by the rows' regions")
  void byRegion() {
    CheckRouter router =
        router(
            List.of(
                row("certn", COURT, Set.of(), 2, true),
                row("soquij", COURT, Set.of("QC"), 1, true)));

    assertThat(router.route(CheckPurpose.COURT_RECORDS, "QC").orElseThrow().row().provider())
        .isEqualTo("soquij");
    assertThat(router.route(CheckPurpose.COURT_RECORDS, "ON").orElseThrow().row().provider())
        .isEqualTo("certn");
    assertThat(router.route(CheckPurpose.COURT_RECORDS, null).orElseThrow().row().provider())
        .isEqualTo("certn");
  }

  @Test
  @DisplayName("a disabled row, a purpose it does not serve or an unknown handler is skipped")
  void skips() {
    CheckProviderRow unknownHandler =
        new CheckProviderRow("ghost", "ghost", COURT, Set.of(), 0, true, "https://x.test", 0);
    CheckRouter router =
        router(
            List.of(
                unknownHandler,
                row("soquij", COURT, Set.of(), 1, false),
                row("trustii", Set.of(CheckPurpose.CREDIT), Set.of(), 2, true)));

    assertThat(router.route(CheckPurpose.COURT_RECORDS, "QC")).isEmpty();
    assertThat(router.route(CheckPurpose.CREDIT, "QC").orElseThrow().row().provider())
        .isEqualTo("trustii");
  }

  @Test
  @DisplayName("the order is the rows' priority, not their position")
  void priority() {
    CheckRouter router =
        router(
            List.of(
                row("certn", COURT, Set.of(), 5, true), row("soquij", COURT, Set.of(), 1, true)));

    assertThat(router.route(CheckPurpose.COURT_RECORDS, "QC").orElseThrow().row().provider())
        .isEqualTo("soquij");
    assertThat(router.routeOf("certn")).isPresent();
    assertThat(router.routeOf("nobody")).isEmpty();
  }
}
