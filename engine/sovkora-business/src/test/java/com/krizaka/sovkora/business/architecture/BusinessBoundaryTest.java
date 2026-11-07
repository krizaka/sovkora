package com.krizaka.sovkora.business.architecture;

import static com.krizaka.sovkora.test.architecture.GovernanceRules.*;

import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ArchUnit boundary enforcement for the sovkora-business module.
 *
 * <p>Business is the App Factory: it orchestrates use-cases over <b>core</b> ports only. It must
 * stay blind to the router app and to sibling capability modules (identity, tools) [ERR-102].
 */
class BusinessBoundaryTest {

  private static final String PKG = "com.krizaka.sovkora.business";
  private static JavaClasses businessClasses;

  @BeforeAll
  static void importClasses() {
    businessClasses = importProductionClasses(PKG);
  }

  @Test
  @DisplayName("[ERR-102] Business must not depend on router")
  void businessIsRouterBlind() {
    assertNoDependencyOn(
        businessClasses,
        PKG,
        "com.krizaka.sovkora.conversationservice",
        "Business must not depend on the router app");
  }

  @Test
  @DisplayName("[ERR-102] Business must not depend on identity")
  void businessIsIdentityBlind() {
    assertNoDependencyOn(businessClasses, PKG, "com.krizaka.users", "Business is identity-blind");
  }

  @Test
  @DisplayName("[ERR-102] Business must not depend on tools")
  void businessIsToolsBlind() {
    assertNoDependencyOn(
        businessClasses, PKG, "com.krizaka.sovkora.tools", "Business is tools-blind");
  }

  @Test
  @DisplayName("[GOV-005] No field injection in business")
  void noFieldInjection() {
    assertNoFieldInjection(businessClasses, PKG);
  }
}
