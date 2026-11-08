package com.krizaka.sovkora.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Tier 3 — Infrastructure Integrity Sensor.
 *
 * <p>Interrogates the Spring Boot Actuator endpoint and explicitly parses the JSON payload to
 * assert individual health indicators for db (Postgres), diskSpace, and ping report explicit UP
 * statuses.
 */
class RouterHealthIT {

  private static final String ROUTER_URL = System.getProperty("router.target.url");

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static Playwright playwright;
  private static Browser browser;

  @BeforeAll
  static void launchBrowser() {
    if (ROUTER_URL == null || ROUTER_URL.isBlank()) {
      throw new IllegalStateException("Target Gateway URL is missing. Check your root .env file.");
    }
    playwright = Playwright.create();
    browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
  }

  @AfterAll
  static void closeBrowser() {
    if (browser != null) browser.close();
    if (playwright != null) playwright.close();
  }

  @Test
  @DisplayName("GET /actuator/health returns 200 with individual component statuses")
  void actuatorHealthReturnsUpWithComponents() throws Exception {
    Page page = browser.newPage();
    Response response = page.navigate(ROUTER_URL + "/actuator/health");

    assertNotNull(response, "Response must not be null");
    assertEquals(200, response.status(), "Health endpoint must return 200");

    String body = page.innerText("body");
    assertNotNull(body, "Health body must not be null");

    // Parse the full JSON payload — no weak string matching
    JsonNode root = MAPPER.readTree(body);

    // Assert overall status
    assertEquals("UP", root.path("status").asString(), "Overall health status must be UP");

    // Assert individual components exist and are UP
    JsonNode components = root.path("components");
    assertTrue(components.has("db"), "Health payload must contain a 'db' component for Postgres");
    assertEquals(
        "UP",
        components.path("db").path("status").asString(),
        "Postgres health indicator must report UP");

    assertTrue(components.has("diskSpace"), "Health payload must contain a 'diskSpace' component");
    assertEquals(
        "UP",
        components.path("diskSpace").path("status").asString(),
        "DiskSpace health indicator must report UP");

    assertTrue(components.has("ping"), "Health payload must contain a 'ping' component");
    assertEquals(
        "UP",
        components.path("ping").path("status").asString(),
        "Ping health indicator must report UP");

    // Redis and RabbitMQ health indicators are only auto-configured when
    // spring-boot-starter-data-redis / spring-boot-starter-amqp are on the
    // classpath. The gateway uses raw Lettuce (Bucket4j) and raw amqp-client,
    // so these indicators may not be present. Connectivity is proven by
    // the dedicated redisConnectivityIsLive() and amqpConnectivityIsLive() tests.
    if (components.has("redis")) {
      assertEquals(
          "UP",
          components.path("redis").path("status").asString(),
          "Redis health indicator must report UP against provisioned container");
    }
    if (components.has("rabbit")) {
      assertEquals(
          "UP",
          components.path("rabbit").path("status").asString(),
          "RabbitMQ health indicator must report UP against provisioned container");
    }

    page.close();
  }

  @Test
  @DisplayName("Postgres JDBC connectivity is live from E2E module")
  void jdbcConnectivityIsLive() throws Exception {
    // Direct JDBC connection to provisioned Postgres — proves infrastructure
    long result =
        E2eJdbcClient.count(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public'");
    assertTrue(
        result > 0, "Live Postgres must contain at least 1 public table (schema initialized)");
  }

  @Test
  @DisplayName("Redis: Lettuce PING proves live Redis connectivity from E2E module")
  void redisConnectivityIsLive() {
    String pong = E2eRedisClient.ping();
    assertEquals(
        "PONG",
        pong,
        "Redis PING must return PONG — proves Lettuce connectivity to provisioned container");
    assertTrue(E2eRedisClient.isConnectable(), "E2eRedisClient.isConnectable() must return true");
  }

  @Test
  @DisplayName("RabbitMQ: AMQP handshake proves live RabbitMQ connectivity from E2E module")
  void amqpConnectivityIsLive() {
    assertTrue(
        E2eAmqpClient.isConnectable(),
        "AMQP connection to provisioned RabbitMQ must succeed (TCP handshake + protocol negotiation)");
  }
}
