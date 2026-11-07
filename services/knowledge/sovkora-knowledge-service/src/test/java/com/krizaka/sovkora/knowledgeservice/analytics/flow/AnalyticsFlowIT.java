package com.krizaka.sovkora.knowledgeservice.analytics.flow;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.security.token.ServiceTokenProvider;
import com.krizaka.sovkora.test.architecture.SqlBoundaryRules;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Product analytics end to end (ADR-081), on the real schema (40-knowledge.sql on pgvector), the
 * real security chain and the real HTTP surface: the recommender's events and the analytics events
 * read back together through the {@code analytics_event_all} view, per tenant, with the audience
 * threshold, the funnel and the erasure across both tables.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.rabbitmq.listener.simple.auto-startup=false",
      "management.tracing.enabled=false",
      "logging.file.name=target/knowledge-service-analytics-it.log"
    })
class AnalyticsFlowIT {

  private static final String SECRET = "sovkora-test-secret-at-least-32-characters!";
  private static final String KNOWLEDGE_PASSWORD = "orazaka_knowledge_it";
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  @SuppressWarnings("resource")
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("bootstrap")
          .withUsername("postgres")
          .withPassword("postgres")
          .withCopyFileToContainer(
              MountableFile.forHostPath(
                  SqlBoundaryRules.locateInitDb(Path.of(System.getProperty("user.dir")))
                      .resolve("40-knowledge.sql")),
              "/docker-entrypoint-initdb.d/40-knowledge.sql");

  @LocalServerPort int port;

  @DynamicPropertySource
  static void wiring(DynamicPropertyRegistry registry) throws Exception {
    if (System.getProperty("api.version") == null && System.getenv("DOCKER_API_VERSION") == null) {
      System.setProperty("api.version", "1.43");
    }
    POSTGRES.start();
    POSTGRES.execInContainer(
        "psql",
        "-U",
        "postgres",
        "-c",
        "ALTER ROLE orazaka_knowledge PASSWORD '" + KNOWLEDGE_PASSWORD + "'");
    String url =
        "jdbc:postgresql://"
            + POSTGRES.getHost()
            + ":"
            + POSTGRES.getMappedPort(5432)
            + "/orazaka_knowledge_db";
    registry.add("orazaka.knowledge-service.datasource.url", () -> url);
    registry.add("orazaka.knowledge-service.datasource.username", () -> "orazaka_knowledge");
    registry.add("orazaka.knowledge-service.datasource.password", () -> KNOWLEDGE_PASSWORD);
    registry.add("krizaka.security.jwt.secret", () -> SECRET);
  }

  private static final String[] PEOPLE = {
    "s1-00000000000000000000000000000000000000000000000000000000000001",
    "s2-00000000000000000000000000000000000000000000000000000000000002",
    "s3-00000000000000000000000000000000000000000000000000000000000003",
    "s4-00000000000000000000000000000000000000000000000000000000000004"
  };

  private static final String RANGE = "from=2026-10-01&to=2026-10-15";

  @Test
  @DisplayName(
      "recommender and product events read back together: KPIs, top items, cohorts, a funnel")
  void insightsFromBothSources() throws Exception {
    String token = token("tenant-analytics");
    List<String> reco = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      reco.add(recoEvent("v" + i, PEOPLE[i], "VIEW", 50 + 10 * i, "2026-10-06T09:0" + i + ":00Z"));
    }
    for (int i = 0; i < 3; i++) {
      reco.add(recoEvent("l" + i, PEOPLE[i], "LIKE", null, "2026-10-06T10:0" + i + ":00Z"));
    }
    reco.add(recoEvent("t0", PEOPLE[0], "TIP", null, "2026-10-06T11:00:00Z"));
    call(
        token,
        "POST",
        "/api/v1/recommendations/events",
        "{\"events\":[" + String.join(",", reco) + "]}",
        200);

    List<String> product = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      product.add(event("s" + i, PEOPLE[i], "session_start", null, "2026-10-06T08:00:00Z"));
    }
    for (int i = 0; i < 3; i++) {
      product.add(event("k" + i, PEOPLE[i], "talk", 120.0, "2026-10-07T20:00:00Z"));
    }
    String batch = "{\"events\":[" + String.join(",", product) + "]}";
    JsonNode first = post(token, "/api/v1/analytics/events", batch);
    assertThat(first.get("accepted").asInt()).isEqualTo(7);
    JsonNode again = post(token, "/api/v1/analytics/events", batch);
    assertThat(again.get("duplicates").asInt()).as("at-least-once: resent ids").isEqualTo(7);

    JsonNode insights = get(token, "/api/v1/analytics/insights?" + RANGE);
    assertThat(insights.get("suppressed").asBoolean()).isFalse();
    assertThat(insights.at("/activeSubjects/current").asLong()).isEqualTo(4);
    assertThat(insights.at("/events/current").asLong()).isEqualTo(15);
    assertThat(insights.at("/events/change").isNull()).as("nothing before").isTrue();
    JsonNode view = kind(insights, "view");
    assertThat(view.at("/count/current").asLong()).isEqualTo(4);
    assertThat(view.get("valueMean").asDouble()).as("mean watched %").isEqualTo(65.0);
    JsonNode talk = kind(insights, "talk");
    assertThat(talk.get("unit").asString()).isEqualTo("SECONDS");
    assertThat(talk.get("valueSum").asDouble()).isEqualTo(360.0);
    assertThat(kind(insights, "tip")).as("one tipper is under the threshold").isNull();
    assertThat(insights.get("series").size()).isEqualTo(14);
    assertThat(insights.at("/topItems/0/itemId").asString()).isEqualTo("v1");
    assertThat(insights.at("/topItems/0/subjects").asLong()).isEqualTo(3);
    assertThat(insights.at("/cohorts/0/week").asString()).isEqualTo("2026-10-05");
    assertThat(insights.at("/cohorts/0/size").asLong()).isEqualTo(4);

    JsonNode creator = get(token, "/api/v1/analytics/insights?" + RANGE + "&owner=c1");
    assertThat(creator.at("/events/current").asLong())
        .as("a creator sees the events on their items only")
        .isEqualTo(8);

    JsonNode funnel = get(token, "/api/v1/analytics/funnel?steps=view,like,tip&" + RANGE);
    assertThat(funnel.at("/steps/0/subjects").asLong()).isEqualTo(4);
    assertThat(funnel.at("/steps/1/subjects").asLong()).isEqualTo(3);
    assertThat(funnel.at("/steps/1/fromPrevious").asDouble()).isEqualTo(0.75);
    assertThat(funnel.at("/steps/2/suppressed").asBoolean()).isTrue();

    JsonNode other = get(token("tenant-other"), "/api/v1/analytics/insights?" + RANGE);
    assertThat(other.get("suppressed").asBoolean()).as("no tenant reads another").isTrue();

    JsonNode erased =
        post(token, "/api/v1/analytics/erasures", "{\"subjects\":[\"" + PEOPLE[0] + "\"]}");
    assertThat(erased.get("events").asInt()).as("both tables").isEqualTo(5);
    JsonNode after = get(token, "/api/v1/analytics/funnel?steps=view,like&" + RANGE);
    assertThat(after.at("/steps/0/subjects").asLong()).isEqualTo(3);
  }

  @Test
  @DisplayName("refusals: a recommender kind, an unknown kind, an e-mail, a bad range, no session")
  void refusals() throws Exception {
    String token = token("tenant-refusals");
    call(
        token,
        "POST",
        "/api/v1/analytics/events",
        "{\"events\":[" + event("x1", PEOPLE[0], "view", 50.0, "2026-10-06T08:00:00Z") + "]}",
        400);
    call(
        token,
        "POST",
        "/api/v1/analytics/events",
        "{\"events\":[" + event("x2", PEOPLE[0], "teleport", null, "2026-10-06T08:00:00Z") + "]}",
        400);
    call(
        token,
        "POST",
        "/api/v1/analytics/events",
        "{\"events\":["
            + event("x3", "ana@example.com", "talk", 1.0, "2026-10-06T08:00:00Z")
            + "]}",
        400);
    call(token, "GET", "/api/v1/analytics/insights?from=2020-01-01&to=2026-10-15", null, 400);
    call(token, "GET", "/api/v1/analytics/funnel?steps=view", null, 400);
    call(null, "GET", "/api/v1/analytics/insights", null, 401);
  }

  // ── helpers ──────────────────────────────────────────────────────────────────────────────────

  private static String token(String tenant) {
    return new ServiceTokenProvider(SECRET, tenant).token();
  }

  private HttpResponse<String> call(
      String token, String method, String path, String body, int expected) throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body));
    if (token != null) {
      builder.header("Authorization", "Bearer " + token);
    }
    HttpResponse<String> response =
        HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as(path + " " + response.body()).isEqualTo(expected);
    return response;
  }

  private JsonNode post(String token, String path, String body) throws Exception {
    return JSON.readTree(call(token, "POST", path, body, 200).body());
  }

  private JsonNode get(String token, String path) throws Exception {
    return JSON.readTree(call(token, "GET", path, null, 200).body());
  }

  private static JsonNode kind(JsonNode insights, String kind) {
    for (JsonNode k : insights.get("kinds")) {
      if (kind.equals(k.get("kind").asString())) {
        return k;
      }
    }
    return null;
  }

  private static String recoEvent(String id, String user, String kind, Integer pct, String at) {
    return "{\"id\":\"r-"
        + id
        + "\",\"user\":\""
        + user
        + "\",\"item\":\"v1\",\"creator\":\"c1\",\"kind\":\""
        + kind
        + "\",\"watchedPct\":"
        + pct
        + ",\"at\":\""
        + at
        + "\"}";
  }

  private static String event(String id, String subject, String kind, Double value, String at) {
    return "{\"id\":\"a-"
        + id
        + "\",\"subject\":\""
        + subject
        + "\",\"kind\":\""
        + kind
        + "\",\"value\":"
        + value
        + ",\"at\":\""
        + at
        + "\"}";
  }
}
