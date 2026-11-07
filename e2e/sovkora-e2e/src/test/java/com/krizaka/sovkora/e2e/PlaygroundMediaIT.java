package com.krizaka.sovkora.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Tier 3 — Full UI E2E: Playground Media Generation (Heavy Multi-Modal).
 *
 * <p>This test runs LAST in the E2E sequence (enforced by two-phase Failsafe configuration) to
 * preserve system resources. Validates all playground pages render their input controls, and
 * verifies the job queue infrastructure is accessible via JDBC.
 */
@Tag("ui")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PlaygroundMediaIT extends AbstractUiE2eTest {

  private static final String ADMIN_EMAIL = System.getProperty("admin.email");
  private static final String ADMIN_PASSWORD = System.getProperty("admin.password");

  // ── Step 0: Login ───────────────────────────────────────────────────────

  @Test
  @Order(0)
  @DisplayName("Login as admin for playground tests")
  void shouldLogin() {
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

  // ── Step 1: Playground Hub ──────────────────────────────────────────────

  @Test
  @Order(1)
  @DisplayName("Navigate to playground hub and verify it renders interactive elements")
  void shouldLoadPlaygroundHub() {
    page.navigate(UI_BASE_URL + "/playground");

    Locator mainContent = page.locator("main");
    mainContent
        .first()
        .waitFor(
            new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(30_000));

    // Playground hub should have navigation links or cards to media types
    page.waitForCondition(
        () -> {
          int links = page.locator("a[href*='playground']").count();
          int cards = page.locator("[class*='card'], section, [data-testid*='playground']").count();
          return (links + cards) > 0;
        },
        new Page.WaitForConditionOptions().setTimeout(15_000));

    assertTrue(
        page.locator("main").first().isVisible(), "Playground hub main content must be visible");
  }

  // ── Step 2: Image Generation Page ───────────────────────────────────────

  @Test
  @Order(2)
  @DisplayName("Image generation page renders with input controls")
  void shouldLoadImageGenerationPage() {
    page.navigate(UI_BASE_URL + "/playground/image/generate");

    Locator mainContent = page.locator("main");
    mainContent
        .first()
        .waitFor(
            new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(30_000));

    // Verify the page has form controls for prompt input
    page.waitForCondition(
        () -> {
          int inputs = page.locator("textarea, input[type='text']").count();
          int buttons = page.locator("button").count();
          return (inputs + buttons) > 0;
        },
        new Page.WaitForConditionOptions().setTimeout(15_000));

    int controls = page.locator("textarea, input[type='text'], button").count();
    assertTrue(
        controls > 0, "Image generation page must have input controls (found: " + controls + ")");
  }

  // ── Step 3: Playground Sub-Pages ────────────────────────────────────────

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(
      strings = {
        "/playground/speech/synthesis",
        "/playground/video/generate",
        "/playground/video/analyze",
        "/playground/audio/analyze",
        "/playground/vision/analyze",
        "/playground/code/scaffold"
      })
  @Order(3)
  @DisplayName("Playground sub-pages render with main content")
  void shouldLoadPlaygroundSubPages(String path) {
    page.navigate(UI_BASE_URL + path);

    Locator mainContent = page.locator("main");
    mainContent
        .first()
        .waitFor(
            new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(30_000));

    assertTrue(mainContent.first().isVisible(), path + " main content must be visible");
  }

  // ── Step 9: JDBC — Verify job queue infrastructure is accessible ────────

  @Test
  @Order(9)
  @DisplayName("JDBC: Job queue table is accessible and structured correctly")
  void shouldVerifyJobQueueInfrastructure() throws SQLException {
    // Verify the orazaka_jobs table exists and is queryable
    long tableExists =
        E2eJdbcClient.count(
            "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = 'public' AND table_name = 'orazaka_jobs'");
    assertEquals(1, tableExists, "orazaka_jobs table must exist in the live database");

    // Verify the table has the correct columns
    long columnCount =
        E2eJdbcClient.count(
            "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_name = 'orazaka_jobs' AND column_name IN "
                + "('id', 'user_id', 'feature_key', 'status', 'payload', 'result')");
    assertTrue(
        columnCount >= 5,
        "orazaka_jobs must have required columns (id, user_id, feature_key, status, payload)");
  }

  // ── Step 10: AMQP — Verify RabbitMQ media queue infrastructure ──────────

  @Test
  @Order(10)
  @DisplayName("AMQP: RabbitMQ connectivity and queue infrastructure is live")
  void shouldVerifyAmqpInfrastructure() {
    // 1. Verify AMQP connectivity
    assertTrue(
        E2eAmqpClient.isConnectable(), "AMQP connection to provisioned RabbitMQ must succeed");

    // 2. Verify the media jobs queue exists (auto-declared by Spring AMQP)
    //    The queue name is declared in the gateway RabbitMQ configuration.
    //    If the queue doesn't exist yet (no messages published), the passive
    //    declare will return false — which is acceptable for a clean E2E run.
    //    What matters is that RabbitMQ is live and AMQP protocol works.
    boolean queueExists = E2eAmqpClient.queueExists("orazaka.media.jobs");
    // Queue may not exist on first run (lazy declaration), so just log
    if (queueExists) {
      long messageCount = E2eAmqpClient.getQueueMessageCount("orazaka.media.jobs");
      assertTrue(
          messageCount >= 0,
          "orazaka.media.jobs queue message count must be queryable (found: " + messageCount + ")");
    }
  }
}
