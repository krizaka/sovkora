package com.krizaka.sovkora.interceptor.architecture;

import static com.krizaka.sovkora.test.architecture.GovernanceRules.*;

import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ArchUnit boundary enforcement for the sovkora-interceptors module.
 *
 * <p>Interceptors implement the core pipeline port ({@code PromptContextInterceptor}); they may
 * depend on <b>core</b> only and must stay blind to the router app and to sibling capability
 * modules (identity, tools, business) [ERR-102].
 */
class InterceptorsBoundaryTest {

  private static final String PKG = "com.krizaka.sovkora.interceptor";
  private static JavaClasses interceptorClasses;

  @BeforeAll
  static void importClasses() {
    interceptorClasses = importProductionClasses(PKG);
  }

  @Test
  @DisplayName("[ERR-102] Interceptors must not depend on router")
  void interceptorsAreRouterBlind() {
    assertNoDependencyOn(
        interceptorClasses,
        PKG,
        "com.krizaka.sovkora.conversationservice",
        "Interceptors must not depend on the router app");
  }

  @Test
  @DisplayName("[ERR-102] Interceptors must not depend on identity")
  void interceptorsAreIdentityBlind() {
    assertNoDependencyOn(
        interceptorClasses, PKG, "com.krizaka.users", "Interceptors are identity-blind");
  }

  @Test
  @DisplayName("[ERR-102] Interceptors must not depend on tools")
  void interceptorsAreToolsBlind() {
    assertNoDependencyOn(
        interceptorClasses, PKG, "com.krizaka.sovkora.tools", "Interceptors are tools-blind");
  }

  @Test
  @DisplayName("[ERR-102] Interceptors must not depend on business")
  void interceptorsAreBusinessBlind() {
    assertNoDependencyOn(
        interceptorClasses, PKG, "com.krizaka.sovkora.business", "Interceptors are business-blind");
  }

  @Test
  @DisplayName("[GOV-005] No field injection in interceptors")
  void noFieldInjection() {
    assertNoFieldInjection(interceptorClasses, PKG);
  }
}
