package com.krizaka.sovkora.knowledge.recommendation.flow;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.security.token.ServiceTokenProvider;
import com.krizaka.sovkora.test.support.architecture.SqlBoundaryRules;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
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
 * The recommender end to end, on the real schema (40-knowledge.sql on pgvector), the real security
 * chain and the real HTTP surface — the embedding model stubbed by words (texts sharing words get
 * close vectors). Also the service's context-load test with its deployment wiring (KNOWLEDGE_DB_*,
 * the JWT secret, the conversation service's URL), and the contract test: every request of {@code
 * contract/recommendations-contract.json} is sent and every live answer must have exactly the shape
 * of the recorded one — the same file the products' tests read.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.rabbitmq.listener.simple.auto-startup=false",
      "management.tracing.enabled=false",
      "logging.file.name=target/knowledge-service-it.log"
    })
class RecommendationFlowIT {

  private static final String SECRET = "sovkora-test-secret-at-least-32-characters!";
  private static final String KNOWLEDGE_PASSWORD = "orazaka_knowledge_it";
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final List<String> EMBEDDING_CALLERS = new CopyOnWriteArrayList<>();

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

  private static final HttpServer EMBEDDINGS = embeddingStub();

  @LocalServerPort int port;

  @DynamicPropertySource
  static void wiring(DynamicPropertyRegistry registry) throws Exception {
    if (System.getProperty("api.version") == null && System.getenv("DOCKER_API_VERSION") == null) {
      System.setProperty("api.version", "1.43");
    }
    POSTGRES.start();
    // The initdb file creates the role without a password (ADR-035); the deployment sets it.
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
    registry.add(
        "orazaka.knowledge-service.recommendation.embeddings-base-url",
        () -> "http://127.0.0.1:" + EMBEDDINGS.getAddress().getPort());
  }

  @AfterAll
  static void stop() {
    EMBEDDINGS.stop(0);
  }

  // ── the scenario ─────────────────────────────────────────────────────────────────────────────

  private static final String FAN2 =
      "fan2-0000000000000000000000000000000000000000000000000000000002";
  private static final String FAN4 =
      "fan4-0000000000000000000000000000000000000000000000000000000004";

  @Test
  @DisplayName("two people, two interactions, two different feeds — each item says why")
  void twoPeopleGetTwoFeeds() throws Exception {
    String token = token("tenant-flow");
    post(
        token,
        "/items",
        itemsJson(
            item("j1", "cA", "Jazz trio at midnight", "jazz piano double bass club", "jazz"),
            item("j2", "cB", "Jazz quartet rehearsal", "jazz saxophone piano club", "jazz"),
            item("j3", "cE", "Jazz standards on a rooftop", "jazz piano trumpet evening", "jazz"),
            item(
                "m1",
                "cD",
                "Tilt-shift harbour",
                "miniature model city harbour boats",
                "miniature"),
            item(
                "m2", "cF", "Tilt-shift train station", "miniature model city trains", "miniature"),
            item("m3", "cG", "Tilt-shift market", "miniature model city market crowd", "miniature"),
            item("k1", "cC", "Tea pour in slow motion", "tea pour cup kitchen steam", "tea")));
    assertThat(EMBEDDING_CALLERS)
        .as("embedded on behalf of the caller")
        .contains("Bearer " + token);

    post(
        token,
        "/events",
        eventsJson(
            event("f2-1", FAN2, "j1", "LIKE", null),
            event("f2-2", FAN2, "j2", "VIEW", 95),
            event("f4-1", FAN4, "m1", "LIKE", null),
            event("f4-2", FAN4, "m2", "SAVE", null)));

    JsonNode feed2 = get(token, "/for-you?user=" + FAN2 + "&limit=3");
    JsonNode feed4 = get(token, "/for-you?user=" + FAN4 + "&limit=3");
    assertThat(feed2.get("strategy").asString()).isEqualTo("personal");
    assertThat(feed2.get("items").get(0).get("id").asString()).isEqualTo("j3");
    assertThat(feed4.get("items").get(0).get("id").asString()).isEqualTo("m3");
    assertThat(feed2.get("items").get(0).get("reasons").toString()).contains("similar");
    assertThat(ids(feed2)).doesNotContain("j1", "j2");
    assertThat(ids(feed2)).isNotEqualTo(ids(feed4));

    JsonNode similar = get(token, "/similar?item=m1&limit=2");
    assertThat(ids(similar)).containsAnyOf("m2", "m3").doesNotContain("m1");

    // Idempotent: the same events again are duplicates.
    JsonNode again = post(token, "/events", eventsJson(event("f2-1", FAN2, "j1", "LIKE", null)));
    assertThat(again.get("duplicates").asInt()).isEqualTo(1);

    // Another tenant sees nothing of this one.
    JsonNode stranger = get(token("tenant-other"), "/for-you?user=" + FAN2 + "&limit=3");
    assertThat(stranger.get("strategy").asString()).isEqualTo("cold_start");
    assertThat(stranger.get("items").size()).isZero();

    // Erasure (account deleted): the person is forgotten, back to a cold start.
    post(token, "/erasures", "{\"users\":[\"" + FAN2 + "\"]}");
    assertThat(get(token, "/for-you?user=" + FAN2 + "&limit=3").get("strategy").asString())
        .isEqualTo("cold_start");
  }

  @Test
  @DisplayName("the recorded contract: every request is accepted and every answer has its shape")
  void contract() throws Exception {
    JsonNode fixture =
        JSON.readTree(
            Files.readString(Path.of("src/test/resources/contract/recommendations-contract.json")));
    String token = token("tenant-contract");
    expectShape(
        post(token, "/items", fixture.get("items").get("request").toString()),
        fixture.get("items").get("response"));
    expectShape(
        post(token, "/events", fixture.get("events").get("request").toString()),
        fixture.get("events").get("response"));
    JsonNode q = fixture.get("forYou").get("query");
    expectShape(
        get(
            token,
            "/for-you?user=" + q.get("user").asString() + "&limit=" + q.get("limit").asInt()),
        fixture.get("forYou").get("response"));
    JsonNode s = fixture.get("similar").get("query");
    expectShape(
        get(
            token,
            "/similar?item=" + s.get("item").asString() + "&limit=" + s.get("limit").asInt()),
        fixture.get("similar").get("response"));
    expectShape(
        get(
            token,
            "/insights?creator=" + fixture.get("insights").get("query").get("creator").asString()),
        fixture.get("insights").get("response"));
    expectShape(
        post(token, "/erasures", fixture.get("erasures").get("request").toString()),
        fixture.get("erasures").get("response"));
  }

  @Test
  @DisplayName("no session, no recommendation; a bad request is a 400, an unknown item a 404")
  void refusals() throws Exception {
    assertThat(call(null, "GET", "/for-you?user=" + FAN2, null).statusCode()).isEqualTo(401);
    String token = token("tenant-refusals");
    assertThat(call(token, "GET", "/for-you?user=someone@example.com", null).statusCode())
        .isEqualTo(400);
    assertThat(call(token, "GET", "/for-you?user=" + FAN2 + "&limit=500", null).statusCode())
        .isEqualTo(400);
    assertThat(call(token, "GET", "/similar?item=nope", null).statusCode()).isEqualTo(404);
    assertThat(
            call(
                    token,
                    "POST",
                    "/events",
                    "{\"events\":[{\"id\":\"x\",\"user\":\""
                        + FAN2
                        + "\",\"item\":\"v\",\"kind\":\"STARE\",\"at\":\"2026-10-15T12:00:00Z\"}]}")
                .statusCode())
        .isEqualTo(400);
  }

  // ── helpers ──────────────────────────────────────────────────────────────────────────────────

  private static String token(String tenant) {
    return new ServiceTokenProvider(SECRET, tenant).token();
  }

  private HttpResponse<String> call(String token, String method, String path, String body)
      throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/v1/recommendations" + path))
            .header("Content-Type", "application/json")
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body));
    if (token != null) {
      builder.header("Authorization", "Bearer " + token);
    }
    return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private JsonNode post(String token, String path, String body) throws Exception {
    HttpResponse<String> response = call(token, "POST", path, body);
    assertThat(response.statusCode()).as(path + " " + response.body()).isEqualTo(200);
    return JSON.readTree(response.body());
  }

  private JsonNode get(String token, String path) throws Exception {
    HttpResponse<String> response = call(token, "GET", path, null);
    assertThat(response.statusCode()).as(path + " " + response.body()).isEqualTo(200);
    if (path.startsWith("/for-you") || path.startsWith("/similar")) {
      assertThat(response.headers().firstValue("X-Sovkora-Request-Id")).isPresent();
    }
    return JSON.readTree(response.body());
  }

  private static List<String> ids(JsonNode feed) {
    List<String> ids = new ArrayList<>();
    feed.get("items").forEach(i -> ids.add(i.get("id").asString()));
    return ids;
  }

  private static String item(
      String id, String creator, String title, String description, String tag) {
    return "{\"id\":\""
        + id
        + "\",\"creator\":\""
        + creator
        + "\",\"title\":\""
        + title
        + "\",\"description\":\""
        + description
        + "\",\"tags\":[\""
        + tag
        + "\"],\"language\":\"en\",\"durationSeconds\":20,\"publishedAt\":\"2026-10-14T10:00:00Z\"}";
  }

  private static String itemsJson(String... items) {
    return "{\"items\":[" + String.join(",", items) + "]}";
  }

  private static String event(String id, String user, String item, String kind, Integer pct) {
    return "{\"id\":\""
        + id
        + "\",\"user\":\""
        + user
        + "\",\"item\":\""
        + item
        + "\",\"kind\":\""
        + kind
        + "\",\"watchedPct\":"
        + pct
        + ",\"at\":\"2026-10-15T09:00:00Z\"}";
  }

  private static String eventsJson(String... events) {
    return "{\"events\":[" + String.join(",", events) + "]}";
  }

  /** Same keys, same kinds of values, recursively; a null or an empty list matches anything. */
  private static void expectShape(JsonNode live, JsonNode recorded) {
    compatible(live, recorded, "$");
  }

  private static void compatible(JsonNode live, JsonNode recorded, String path) {
    if (live == null || recorded == null || live.isNull() || recorded.isNull()) {
      return;
    }
    assertThat(live.getNodeType()).as(path).isEqualTo(recorded.getNodeType());
    if (live.isObject()) {
      List<String> liveKeys = new ArrayList<>();
      live.properties().forEach(e -> liveKeys.add(e.getKey()));
      List<String> recordedKeys = new ArrayList<>();
      recorded.properties().forEach(e -> recordedKeys.add(e.getKey()));
      assertThat(liveKeys).as(path).containsExactlyInAnyOrderElementsOf(recordedKeys);
      for (String key : recordedKeys) {
        compatible(live.get(key), recorded.get(key), path + "." + key);
      }
    } else if (live.isArray() && !live.isEmpty() && !recorded.isEmpty()) {
      compatible(live.get(0), recorded.get(0), path + "[0]");
    }
  }

  /**
   * A stand-in for the platform's embedding model: each word adds weight to a dimension chosen by
   * its hash, so texts that share words point the same way.
   */
  private static HttpServer embeddingStub() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/api/v1/embeddings",
          exchange -> {
            EMBEDDING_CALLERS.add(exchange.getRequestHeaders().getFirst("Authorization"));
            JsonNode body = JSON.readTree(exchange.getRequestBody().readAllBytes());
            StringBuilder data = new StringBuilder();
            int index = 0;
            for (JsonNode text : body.get("input")) {
              float[] v = new float[1024];
              for (String word : text.asString().toLowerCase(Locale.ROOT).split("[^a-z]+")) {
                if (word.length() > 2) {
                  v[Math.floorMod(word.hashCode(), 1024)] += 1f;
                }
              }
              v[1023] += 0.05f;
              StringBuilder vec = new StringBuilder("[");
              for (int i = 0; i < v.length; i++) {
                vec.append(i == 0 ? "" : ",").append(v[i]);
              }
              data.append(index == 0 ? "" : ",")
                  .append("{\"index\":")
                  .append(index++)
                  .append(",\"embedding\":")
                  .append(vec)
                  .append("]}");
            }
            byte[] out =
                ("{\"runId\":\"run-stub\",\"model\":\"stub-embedding\",\"dimensions\":1024,\"data\":["
                        + data
                        + "],\"usage\":{\"promptTokens\":1}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
              os.write(out);
            }
          });
      server.start();
      return server;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
