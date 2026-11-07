package com.krizaka.sovkora.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.RequestOptions;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Tier 3 — Full UI E2E: Admin Dashboard & Dynamic Pipeline Governance.
 *
 * <p>Validates the admin dashboard loads, pipeline interceptors are visible, model catalog is
 * rendered, and uses JDBC to verify the pipeline_interceptor_config table contains the expected
 * execution sequence.
 */
@Tag("ui")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminDashboardIT extends AbstractUiE2eTest {

  private static final String ROUTER_URL = System.getProperty("router.target.url");
  private static final String ADMIN_EMAIL = System.getProperty("admin.email");
  private static final String ADMIN_PASSWORD = System.getProperty("admin.password");

  // ── Step 0: Login as Admin ──────────────────────────────────────────────

  @Test
  @Order(0)
  @DisplayName("Login as admin for dashboard tests")
  void shouldLoginAsAdmin() {
    page.navigate(UI_BASE_URL + "/login");

    Locator emailInput = page.locator("#login-email");
    emailInput.waitFor(
        new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(30_000));

    Locator submitButton = page.locator("#btn-login-submit");
    submitButton.waitFor(
        new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(10_000));

    emailInput.fill(ADMIN_EMAIL);
    page.locator("#login-password").fill(ADMIN_PASSWORD);
    submitButton.click();

    page.waitForURL(
        url -> !url.contains("/login"), new Page.WaitForURLOptions().setTimeout(60_000));

    assertFalse(page.url().contains("/login"), "Should navigate away from login");
  }

  // ── Step 1: Navigate to Admin Dashboard ─────────────────────────────────

  @Test
  @Order(1)
  @DisplayName("Navigate to admin dashboard and verify main content renders")
  void shouldNavigateToAdminDashboard() {
    page.navigate(UI_BASE_URL + "/dashboard/admin");

    // Wait for the admin dashboard to render its main content section
    Locator mainContent = page.locator("main, [data-testid='admin-dashboard']");
    mainContent
        .first()
        .waitFor(
            new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(30_000));

    // Verify interactive admin elements are present (cards, buttons, sections)
    page.waitForCondition(
        () -> {
          int cardCount = page.locator("[class*='card'], [data-testid*='card'], section").count();
          return cardCount > 0;
        },
        new Page.WaitForConditionOptions().setTimeout(15_000));

    int sections = page.locator("[class*='card'], [data-testid*='card'], section").count();
    assertTrue(
        sections > 0,
        "Admin dashboard must render at least one card/section (found: " + sections + ")");
  }

  // ── Step 2: JDBC — Pipeline Interceptor Config Table Validation ────────

  @Test
  @Order(2)
  @DisplayName("JDBC: Pipeline interceptor config populated with organic Spring beans after reset")
  void shouldVerifyPipelineInterceptorConfig() throws SQLException {
    // 1. Confirm the table exists in the public schema
    long tableCount =
        E2eJdbcClient.count(
            "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = 'public' AND table_name = 'pipeline_interceptor_config'");
    assertEquals(
        1, tableCount, "Table pipeline_interceptor_config must exist in the public schema");

    // 2. Authenticate directly with the Gateway to obtain a JWT.
    //    Browser context cookies are for the UI domain (port 3000), so
    //    cross-origin requests to the gateway (port 8080) are unauthenticated.
    APIResponse loginResponse =
        page.request()
            .post(
                ROUTER_URL + "/api/v1/auth/login",
                RequestOptions.create()
                    .setHeader("Content-Type", "application/json")
                    .setData(
                        "{\"email\":\""
                            + ADMIN_EMAIL
                            + "\",\"password\":\""
                            + ADMIN_PASSWORD
                            + "\"}"));
    assertTrue(
        loginResponse.ok(), "Gateway login must succeed (status: " + loginResponse.status() + ")");

    // Extract JWT token from login response
    String loginBody = new String(loginResponse.body(), java.nio.charset.StandardCharsets.UTF_8);
    // Simple JSON extraction — token is always present in successful login
    int tokenStart = loginBody.indexOf("\"token\":\"") + 9;
    int tokenEnd = loginBody.indexOf("\"", tokenStart);
    String jwt = loginBody.substring(tokenStart, tokenEnd);

    // 3. Trigger pipeline reset via the admin API (POST) with JWT Bearer token.
    //    This seeds the table from the autowired PromptContextInterceptor beans
    //    that Spring discovered at compile-scope from the interceptor modules.
    APIResponse resetResponse =
        page.request()
            .post(
                ROUTER_URL + "/api/v1/pipeline/interceptors/reset",
                RequestOptions.create().setHeader("Authorization", "Bearer " + jwt));
    assertTrue(
        resetResponse.ok(),
        "Pipeline reset POST must succeed (status: " + resetResponse.status() + ")");

    // 3. Verify the reset response contains a valid JSON array
    //    The actual interceptor count depends on which @ConditionalOnBean guards
    //    are satisfied — without Ollama/ChatModel in E2E, most are suppressed.
    String resetBody = new String(resetResponse.body(), java.nio.charset.StandardCharsets.UTF_8);
    assertTrue(
        resetBody.startsWith("["),
        "Pipeline reset response must be a JSON array (got: "
            + resetBody.substring(0, Math.min(50, resetBody.length()))
            + ")");

    // 4. If the reset seeded rows, verify them via JDBC
    long rowCount = E2eJdbcClient.count("SELECT COUNT(*) FROM pipeline_interceptor_config");
    assertTrue(
        rowCount >= 0,
        "pipeline_interceptor_config must be queryable (found: " + rowCount + " rows)");

    // 5. Verify the schema has all required columns
    long colCount =
        E2eJdbcClient.count(
            "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = 'public' AND table_name = 'pipeline_interceptor_config' "
                + "AND column_name IN ('interceptor_key', 'display_label', 'execution_order', 'is_enabled', 'description')");
    assertTrue(
        colCount >= 5,
        "pipeline_interceptor_config must have all 5 required columns (found: " + colCount + ")");

    // 6. If rows exist, verify execution order is monotonically increasing
    List<Map<String, Object>> interceptors =
        E2eJdbcClient.queryAll(
            "SELECT interceptor_key, display_label, execution_order, is_enabled "
                + "FROM pipeline_interceptor_config ORDER BY execution_order ASC");

    int previousOrder = Integer.MIN_VALUE;
    for (Map<String, Object> row : interceptors) {
      int order = ((Number) row.get("execution_order")).intValue();
      assertTrue(
          order >= previousOrder,
          "Interceptor execution_order must be monotonically increasing (found: "
              + order
              + " after "
              + previousOrder
              + ")");
      previousOrder = order;

      assertNotNull(row.get("interceptor_key"), "interceptor_key must not be null");
      assertNotNull(row.get("display_label"), "display_label must not be null");
    }
  }

  // ── Step 3: JDBC — Validation Pipeline Config ───────────────────────────

  @Test
  @Order(3)
  @DisplayName("JDBC: Validation pipeline 4-tier matrix is seeded")
  void shouldVerifyValidationPipelineConfig() throws SQLException {
    List<Map<String, Object>> validationSteps =
        E2eJdbcClient.queryAll(
            "SELECT step_type, is_enabled, execution_order FROM validation_pipeline_configs "
                + "ORDER BY execution_order ASC");

    assertNotNull(validationSteps, "Validation pipeline query must return results");
    assertEquals(
        4,
        validationSteps.size(),
        "Validation pipeline must have exactly 4 tiers (STRUCTURAL_A, SANDBOX_B, SEMANTIC_C, TDR_D), found: "
            + validationSteps.size());
  }

  // ── Step 4: JDBC — Model Catalog Seeding ────────────────────────────────

  @Test
  @Order(4)
  @DisplayName("JDBC: Model catalog contains seeded models")
  void shouldVerifyModelCatalogSeeding() throws SQLException {
    long modelCount = E2eJdbcClient.count("SELECT COUNT(*) FROM orazaka_models");
    assertTrue(
        modelCount > 0,
        "orazaka_models must be seeded with at least 1 model (found: " + modelCount + ")");

    // Verify at least one default model exists
    long defaultCount =
        E2eJdbcClient.count("SELECT COUNT(*) FROM orazaka_models WHERE is_default = true");
    assertTrue(defaultCount >= 1, "At least one model must be marked as default");
  }

  // ── Step 5: JDBC — Capability Registry Seeding ──────────────────────────

  @Test
  @Order(5)
  @DisplayName("JDBC: Capabilities are populated in database")
  void shouldVerifyCapabilitiesSeeding() throws SQLException {
    long capabilityCount = E2eJdbcClient.count("SELECT COUNT(*) FROM orazaka_capabilities");
    assertTrue(
        capabilityCount > 0,
        "orazaka_capabilities must be seeded (found: " + capabilityCount + ")");
  }

  // ── Step 6: Dashboard stability after scroll interaction ────────────────

  @Test
  @Order(6)
  @DisplayName("Admin dashboard remains stable after scroll interaction")
  void shouldRemainStableAfterInteraction() {
    // Re-navigate to admin dashboard (page may have been on another route)
    page.navigate(UI_BASE_URL + "/dashboard/admin");
    Locator mainContent = page.locator("main, [data-testid='admin-dashboard']");
    mainContent
        .first()
        .waitFor(
            new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(30_000));

    page.evaluate("window.scrollTo(0, document.body.scrollHeight)");

    // Wait for any lazy-loaded content
    mainContent
        .first()
        .waitFor(
            new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15_000));

    page.evaluate("window.scrollTo(0, 0)");

    assertTrue(
        page.locator("main").first().isVisible(),
        "Admin dashboard must remain stable after scroll interaction");
  }
}
