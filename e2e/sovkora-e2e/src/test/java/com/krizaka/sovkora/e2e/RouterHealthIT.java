package com.krizaka.sovkora.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * <p>Reads the router's actuator at its internal address (UP, no component without an
 * administrator's token), the edge's own {@code /healthz}, and proves the edge does not forward a
 * backend's {@code /actuator/**}.
 */
class RouterHealthIT {

  private static final String ROUTER_URL = System.getProperty("router.target.url");

  /** The router's own port, never routed by the edge: where its actuator is read. */
  private static final String ROUTER_INTERNAL_URL = "http://localhost:8080";

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
  @DisplayName(
      "The router's own health, read at its internal address, is UP and names no component")
  void routerHealthIsUpWithoutDetail() throws Exception {
    JsonNode root = readJson(ROUTER_INTERNAL_URL + "/actuator/health", 200);

    // The aggregate is DOWN when any indicator (db, diskSpace, ping, rabbit) is: UP proves them
    // all.
    assertEquals("UP", root.path("status").asString(), "Overall health status must be UP");
    // No administrator's token: the components are not shown (krizaka-restricted#348).
    assertFalse(root.has("components"), "An anonymous health answer must name no component");
  }

  @Test
  @DisplayName("Through the edge, /healthz is UP and a backend's actuator is not reachable")
  void theEdgeServesItsHealthAndForwardsNoActuator() throws Exception {
    JsonNode root = readJson(ROUTER_URL + "/healthz", 200);
    assertEquals("UP", root.path("status").asString(), "The edge's liveness must be UP");

    Page page = browser.newPage();
    Response actuator = page.navigate(ROUTER_URL + "/actuator/health");
    assertNotNull(actuator, "Response must not be null");
    assertEquals(404, actuator.status(), "The edge must not forward a backend's actuator");
    page.close();
  }

  private static JsonNode readJson(String url, int expectedStatus) throws Exception {
    Page page = browser.newPage();
    Response response = page.navigate(url);
    assertNotNull(response, "Response must not be null");
    assertEquals(expectedStatus, response.status(), url + " status");
    String body = page.innerText("body");
    page.close();
    return MAPPER.readTree(body);
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
