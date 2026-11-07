package com.krizaka.sovkora.automationservice.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A stand-in for the Studio service at the HTTP boundary: it records what the automation service
 * sends and answers as the test says. Nothing of the automation service is mocked.
 */
public final class StudioStub {

  /**
   * One request the stub received.
   *
   * @param path the request path
   * @param authorization the {@code Authorization} header
   * @param idempotencyKey the {@code Idempotency-Key} header
   * @param body the request body
   */
  public record Received(String path, String authorization, String idempotencyKey, String body) {}

  private final HttpServer server;
  private final List<Received> received = new CopyOnWriteArrayList<>();
  private final AtomicInteger starts = new AtomicInteger();
  private volatile int status = 202;
  private volatile long delayMillis;
  private volatile String runStatus = "RUNNING";

  /** Starts the stub on a free port. */
  public StudioStub() {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    server.setExecutor(Executors.newCachedThreadPool());
    server.createContext("/api/v1/studios/runs/", this::read);
    server.createContext("/api/v1/studios/", this::start);
    server.start();
  }

  private void start(HttpExchange exchange) throws IOException {
    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    received.add(
        new Received(
            exchange.getRequestURI().getPath(),
            exchange.getRequestHeaders().getFirst("Authorization"),
            exchange.getRequestHeaders().getFirst("Idempotency-Key"),
            body));
    starts.incrementAndGet();
    try {
      Thread.sleep(delayMillis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    respond(
        exchange,
        status,
        status < 300 ? "{\"id\":\"" + UUID.randomUUID() + "\"}" : "{\"error\":\"no\"}");
  }

  private void read(HttpExchange exchange) throws IOException {
    respond(exchange, 200, "{\"status\":\"" + runStatus + "\"}");
  }

  private static void respond(HttpExchange exchange, int code, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(code, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }

  /**
   * The base URL to give the service.
   *
   * @return {@code http://127.0.0.1:<port>}
   */
  public String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  /**
   * The requests received so far.
   *
   * @return a snapshot
   */
  public List<Received> received() {
    return List.copyOf(received);
  }

  /**
   * How many Studio runs were asked for.
   *
   * @return the count
   */
  public int starts() {
    return starts.get();
  }

  /** Forgets what was received and answers 202 again. */
  public void reset() {
    received.clear();
    starts.set(0);
    status = 202;
    delayMillis = 0;
    runStatus = "RUNNING";
  }

  /**
   * Makes the next starts answer with this status.
   *
   * @param httpStatus the status
   */
  public void answer(int httpStatus) {
    this.status = httpStatus;
  }

  /**
   * Makes the starts slow, to prove no database connection is held meanwhile.
   *
   * @param millis the delay
   */
  public void delay(long millis) {
    this.delayMillis = millis;
  }

  /**
   * What reading a Studio run answers.
   *
   * @param value {@code RUNNING}, {@code SUCCEEDED}, {@code FAILED}…
   */
  public void runStatus(String value) {
    this.runStatus = value;
  }

  /**
   * The request bodies grouped by path, for assertions.
   *
   * @return path to bodies
   */
  public Map<String, List<String>> bodiesByPath() {
    return received.stream()
        .collect(
            java.util.stream.Collectors.groupingBy(
                Received::path,
                java.util.stream.Collectors.mapping(
                    Received::body, java.util.stream.Collectors.toList())));
  }
}
