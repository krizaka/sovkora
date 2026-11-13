package com.krizaka.sovkora.edge.infrastructure.adapter.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.krizaka.sovkora.edge.infrastructure.config.EdgeRoutesProperties;
import com.sun.net.httpserver.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The edge fails fast (krizaka-internal#2132): a backend that is slow, down or failing is answered
 * at once with a coded 503 and a {@code Retry-After}, and its circuit breaker stops sending it
 * traffic until a probe finds it healthy again. Each scenario has its own stub backend, so its own
 * breaker.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "management.server.port=0",
      "sovkora.edge.backend.timeouts.connect=1s",
      "sovkora.edge.backend.timeouts.response=400ms",
      "sovkora.edge.backend.breaker.failure-rate-percent=50",
      "sovkora.edge.backend.breaker.slow-call-threshold=10s",
      "sovkora.edge.backend.breaker.slow-call-rate-percent=100",
      "sovkora.edge.backend.breaker.window-calls=4",
      "sovkora.edge.backend.breaker.minimum-calls=3",
      "sovkora.edge.backend.breaker.open-duration=1s",
      "sovkora.edge.backend.breaker.half-open-calls=1",
      "sovkora.edge.backend.retry-after=7s"
    })
class ProxyFastFailTest {

  private static final Duration RESPONSE_TIMEOUT = Duration.ofMillis(400);

  /** The route table's key, read from its binding so the test follows a rename of the prefix. */
  private static final String ROUTES =
      EdgeRoutesProperties.class.getAnnotation(ConfigurationProperties.class).prefix() + ".routes";

  private static HttpServer slowBackend;
  private static HttpServer failingBackend;
  private static HttpServer recoveringBackend;
  private static final AtomicInteger failingCalls = new AtomicInteger();
  private static final AtomicInteger recoveringCalls = new AtomicInteger();
  private static final AtomicBoolean recovered = new AtomicBoolean();

  @LocalServerPort private int edgePort;
  @Autowired private MeterRegistry meters;

  private final HttpClient client = HttpClient.newHttpClient();

  @BeforeAll
  static void startBackends() throws IOException {
    slowBackend = stub();
    slowBackend.createContext("/slow", exchange -> answerAfter(exchange, Duration.ofSeconds(3)));
    slowBackend.createContext("/stream", ProxyFastFailTest::streamPastTheTimeout);
    failingBackend = stub();
    failingBackend.createContext(
        "/",
        exchange -> {
          failingCalls.incrementAndGet();
          answer(exchange, 503, "<html>backend down</html>");
        });
    recoveringBackend = stub();
    recoveringBackend.createContext(
        "/",
        exchange -> {
          recoveringCalls.incrementAndGet();
          answer(exchange, recovered.get() ? 200 : 503, "{\"from\":\"backend\"}");
        });
  }

  @AfterAll
  static void stopBackends() {
    slowBackend.stop(0);
    failingBackend.stop(0);
    recoveringBackend.stop(0);
  }

  @DynamicPropertySource
  static void routes(DynamicPropertyRegistry registry) {
    route(registry, 0, "/", slowBackend);
    route(registry, 1, "/failing", failingBackend);
    route(registry, 2, "/recovering", recoveringBackend);
  }

  @Test
  @DisplayName(
      "A backend that does not answer in time gets a 503 BACKEND_TIMEOUT with Retry-After, inside the timeout")
  void slowBackendIsATimeout() throws Exception {
    long started = System.nanoTime();
    HttpResponse<String> response = get("/slow/api/v1/anything");
    Duration took = Duration.ofNanos(System.nanoTime() - started);

    assertThat(response.statusCode()).isEqualTo(503);
    assertThat(took).isLessThan(RESPONSE_TIMEOUT.plusMillis(800));
    assertThat(response.headers().firstValue("Retry-After")).contains("7");
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
    assertThat(response.body())
        .contains("\"status\":503")
        .contains("\"code\":\"BACKEND_TIMEOUT\"")
        .contains("\"retryAfterSeconds\":7");
  }

  @Test
  @DisplayName("The response timeout bounds the wait for the headers, never an open stream")
  void openStreamOutlivesTheResponseTimeout() throws Exception {
    HttpResponse<String> response = get("/stream/api/v1/jobs/stream");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("data: 1").contains("data: 4");
  }

  @Test
  @DisplayName(
      "After the failures the breaker opens: the next call is a 503 BACKEND_UNAVAILABLE that never reaches the backend")
  void openBreakerRejectsWithoutCallingTheBackend() throws Exception {
    for (int call = 0; call < 3; call++) {
      HttpResponse<String> failed = get("/failing/api/v1/anything");
      assertThat(failed.statusCode()).isEqualTo(503);
    }
    assertThat(failingCalls).hasValue(3);

    HttpResponse<String> rejected = get("/failing/api/v1/anything");

    assertThat(failingCalls).hasValue(3);
    assertThat(rejected.statusCode()).isEqualTo(503);
    assertThat(rejected.headers().firstValue("Retry-After")).contains("1");
    assertThat(rejected.body())
        .contains("\"code\":\"BACKEND_UNAVAILABLE\"")
        .doesNotContain("backend down");
    String backend = "127.0.0.1:" + failingBackend.getAddress().getPort();
    assertThat(meters.get("edge.backend.rejections").tag("backend", backend).counter().count())
        .isEqualTo(1.0);
    assertThat(
            meters
                .get("edge.backend.breaker.state")
                .tags("backend", backend, "state", "open")
                .gauge()
                .value())
        .isEqualTo(1.0);
  }

  @Test
  @DisplayName("After the open duration a half-open probe that succeeds closes the breaker")
  void halfOpenProbeClosesTheBreaker() throws Exception {
    for (int call = 0; call < 3; call++) {
      get("/recovering/api/v1/anything");
    }
    assertThat(get("/recovering/api/v1/anything").body()).contains("BACKEND_UNAVAILABLE");
    assertThat(recoveringCalls).hasValue(3);
    recovered.set(true);

    await()
        .atMost(Duration.ofSeconds(3))
        .pollInterval(Duration.ofMillis(100))
        .until(() -> get("/recovering/api/v1/anything").statusCode() == 200);

    assertThat(recoveringCalls).hasValue(4);
    assertThat(get("/recovering/api/v1/anything").statusCode()).isEqualTo(200);
    assertThat(recoveringCalls).hasValue(5);
    String backend = "127.0.0.1:" + recoveringBackend.getAddress().getPort();
    assertThat(
            meters
                .get("edge.backend.breaker.state")
                .tags("backend", backend, "state", "closed")
                .gauge()
                .value())
        .isEqualTo(1.0);
  }

  private HttpResponse<String> get(String path) throws IOException, InterruptedException {
    return client.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + edgePort + path)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private static HttpServer stub() throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    server.start();
    return server;
  }

  private static void route(
      DynamicPropertyRegistry registry, int index, String prefix, HttpServer backend) {
    registry.add(ROUTES + "[" + index + "].path-prefix", () -> prefix);
    registry.add(
        ROUTES + "[" + index + "].target",
        () -> "http://127.0.0.1:" + backend.getAddress().getPort());
  }

  private static void answerAfter(HttpExchange exchange, Duration delay) throws IOException {
    try {
      Thread.sleep(delay);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    answer(exchange, 200, "late");
  }

  private static void answer(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }

  /** Headers at once, then four frames over about twice the response timeout. */
  private static void streamPastTheTimeout(HttpExchange exchange) throws IOException {
    exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
    exchange.sendResponseHeaders(200, 0);
    try (OutputStream out = exchange.getResponseBody()) {
      for (int frame = 1; frame <= 4; frame++) {
        out.write(("data: " + frame + "\n\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
        Thread.sleep(Duration.ofMillis(250));
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
