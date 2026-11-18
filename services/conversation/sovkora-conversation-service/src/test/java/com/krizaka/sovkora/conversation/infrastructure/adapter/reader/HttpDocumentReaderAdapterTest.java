package com.krizaka.sovkora.conversation.infrastructure.adapter.reader;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.conversation.domain.model.reader.DocumentPage;
import com.krizaka.sovkora.conversation.domain.model.reader.PageKind;
import com.krizaka.sovkora.conversation.domain.model.reader.ReadRefusal;
import com.krizaka.sovkora.conversation.domain.model.reader.ReadResult;
import com.krizaka.sovkora.conversation.domain.model.reader.ReaderRouting;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The one wiring test of the reader client (krizaka-internal#4198): against a JDK {@link
 * HttpServer} standing for {@code workers/reader}, it proves the method, the path, the body (the
 * pages and the routing of the call) and the service token, and how each answer of the worker
 * becomes a {@link ReadResult}.
 */
class HttpDocumentReaderAdapterTest {

  private static final String READING =
      """
      {"readings":[{"text":"TOTAL 1234.56","lines":[{"text":"TOTAL 1234.56",
       "box":{"left":0.1,"top":0.4,"right":0.5,"bottom":0.5},"confidence":0.97}],
       "reader":"tesseract","version":"tesseract-5.5.0","generative":false,
       "pageHash":"%1$s","key":"%1$s:tesseract:tesseract-5.5.0","costMicros":3,"latencyMs":41,
       "fallbackFrom":"paddleocr"}]}"""
          .formatted("a".repeat(64));

  private static final ReaderRouting ROUTING =
      new ReaderRouting(
          Map.of("reader.default.photo", "paddleocr", "reader.fallback", "tesseract"));

  private final AtomicReference<String> method = new AtomicReference<>();
  private final AtomicReference<String> path = new AtomicReference<>();
  private final AtomicReference<String> authorization = new AtomicReference<>();
  private final AtomicReference<String> body = new AtomicReference<>();
  private @Nullable HttpServer server;

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  @DisplayName("POST /v1/document-reads with the pages, the routing and the service token")
  void sendsThePagesTheRoutingAndTheToken() {
    HttpDocumentReaderAdapter client = client(200, READING, "s3cret");

    ReadResult result = client.read(List.of(new DocumentPage("UEFHRQ==", PageKind.PHOTO)), ROUTING);

    assertThat(method.get()).isEqualTo("POST");
    assertThat(path.get()).isEqualTo("/v1/document-reads");
    assertThat(authorization.get()).isEqualTo("Bearer s3cret");
    JsonNode sent = JsonMapper.builder().build().readTree(body.get());
    assertThat(sent.path("pages").get(0).path("image").asString()).isEqualTo("UEFHRQ==");
    assertThat(sent.path("pages").get(0).path("kind").asString()).isEqualTo("photo");
    assertThat(sent.path("routing").path("reader.default.photo").asString()).isEqualTo("paddleocr");
    assertThat(result)
        .isInstanceOfSatisfying(
            ReadResult.Readings.class,
            read -> {
              assertThat(read.readings()).hasSize(1);
              var reading = read.readings().getFirst();
              assertThat(reading.reader()).isEqualTo("tesseract");
              assertThat(reading.version()).isEqualTo("tesseract-5.5.0");
              assertThat(reading.costMicros()).isEqualTo(3);
              assertThat(reading.latencyMs()).isEqualTo(41);
              assertThat(reading.fallbackFrom()).isEqualTo("paddleocr");
              assertThat(reading.lines().getFirst().left()).isEqualTo(0.1);
            });
  }

  @Test
  @DisplayName("without a token configured, no Authorization header is sent")
  void noTokenNoHeader() {
    client(200, READING, "").read(List.of(new DocumentPage("UEFHRQ==", PageKind.PHOTO)), ROUTING);
    assertThat(authorization.get()).isNull();
  }

  @Test
  @DisplayName("a coded refusal of the worker is that refusal")
  void aCodedRefusalIsThatRefusal() {
    ReadResult result =
        client(413, "{\"code\":\"BUDGET_EXHAUSTED\"}", "t")
            .read(List.of(new DocumentPage("UEFHRQ==", PageKind.PHOTO)), ROUTING);
    assertThat(result).isEqualTo(new ReadResult.Refused(ReadRefusal.BUDGET_EXHAUSTED));
  }

  @Test
  @DisplayName("a routing out of its bounds is ROUTING_INVALID, an operator's fault")
  void aRoutingOutOfBoundsIsRoutingInvalid() {
    ReadResult result =
        client(500, "{\"code\":\"ROUTING_INVALID\",\"key\":\"reader.page-budget\"}", "t")
            .read(List.of(new DocumentPage("UEFHRQ==", PageKind.PHOTO)), ROUTING);
    assertThat(result).isEqualTo(new ReadResult.Refused(ReadRefusal.ROUTING_INVALID));
  }

  @Test
  @DisplayName("an answer without a known code, or the worker's token refusal, is unavailable")
  void anUncodedAnswerIsUnavailable() {
    assertThat(
            client(401, "{\"code\":\"UNAUTHORISED\"}", "wrong")
                .read(List.of(new DocumentPage("UEFHRQ==", PageKind.PHOTO)), ROUTING))
        .isEqualTo(new ReadResult.Refused(ReadRefusal.READER_UNAVAILABLE));
    stop();
    assertThat(
            client(502, "<html>bad gateway</html>", "t")
                .read(List.of(new DocumentPage("UEFHRQ==", PageKind.PHOTO)), ROUTING))
        .isEqualTo(new ReadResult.Refused(ReadRefusal.READER_UNAVAILABLE));
  }

  @Test
  @DisplayName(
      "an answer missing a field, or a reading per page, is unavailable: nothing is guessed")
  void anIncompleteAnswerIsUnavailable() {
    assertThat(
            client(200, "{\"readings\":[{\"text\":\"x\"}]}", "t")
                .read(List.of(new DocumentPage("UEFHRQ==", PageKind.PHOTO)), ROUTING))
        .isEqualTo(new ReadResult.Refused(ReadRefusal.READER_UNAVAILABLE));
    stop();
    assertThat(
            client(200, "{\"readings\":[]}", "t")
                .read(List.of(new DocumentPage("UEFHRQ==", PageKind.PHOTO)), ROUTING))
        .isEqualTo(new ReadResult.Refused(ReadRefusal.READER_UNAVAILABLE));
  }

  @Test
  @DisplayName("a worker that does not answer is unavailable, never an exception")
  void aWorkerThatIsDownIsUnavailable() {
    HttpDocumentReaderAdapter client = client(200, READING, "t");
    stop();
    server = null;
    assertThat(client.read(List.of(new DocumentPage("UEFHRQ==", PageKind.PHOTO)), ROUTING))
        .isEqualTo(new ReadResult.Refused(ReadRefusal.READER_UNAVAILABLE));
  }

  /** The adapter over a proxy built as the {@code reader} group builds it, on a stub worker. */
  private HttpDocumentReaderAdapter client(int status, String answer, String token) {
    try {
      server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    server.createContext(
        "/",
        exchange -> {
          method.set(exchange.getRequestMethod());
          path.set(exchange.getRequestURI().getPath());
          authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    RestClient.Builder builder =
        RestClient.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort());
    new ReaderHttpGroupConfigurer(token, Duration.ofSeconds(5)).configure(builder);
    ReaderExchange exchange =
        HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.build()))
            .build()
            .createClient(ReaderExchange.class);
    return new HttpDocumentReaderAdapter(exchange);
  }
}
