package com.krizaka.sovkora.edge.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResponseTimeoutHttpClientTest {

  private HttpServer backend;
  private HttpClient client;

  @BeforeEach
  void start() throws IOException {
    backend = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    backend.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    backend.createContext(
        "/late",
        exchange -> {
          pause(Duration.ofSeconds(2));
          exchange.sendResponseHeaders(204, -1);
          exchange.close();
        });
    backend.createContext(
        "/stream",
        exchange -> {
          exchange.sendResponseHeaders(200, 0);
          try (OutputStream out = exchange.getResponseBody()) {
            for (int frame = 1; frame <= 3; frame++) {
              out.write(("data: " + frame + "\n\n").getBytes(StandardCharsets.UTF_8));
              out.flush();
              pause(Duration.ofMillis(200));
            }
          }
        });
    backend.start();
    client = new ResponseTimeoutHttpClient(HttpClient.newHttpClient(), Duration.ofMillis(300));
  }

  @AfterEach
  void stop() {
    backend.stop(0);
    client.close();
  }

  @Test
  @DisplayName("Headers that do not come within the response timeout fail the request")
  void lateHeadersTimeOut() {
    assertThatExceptionOfType(HttpTimeoutException.class)
        .isThrownBy(() -> client.send(request("/late"), HttpResponse.BodyHandlers.discarding()));
  }

  @Test
  @DisplayName("A body that streams longer than the response timeout is read to its end")
  void streamingBodyIsNotCut() throws Exception {
    HttpResponse<String> response =
        client.send(request("/stream"), HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("data: 1").contains("data: 3");
  }

  @Test
  @DisplayName("Everything but the timeout is the wrapped client's")
  void delegatesTheRest() {
    assertThat(client.version()).isEqualTo(HttpClient.Version.HTTP_2);
    assertThat(client.followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
    assertThat(client.connectTimeout()).isEmpty();
  }

  private HttpRequest request(String path) {
    return HttpRequest.newBuilder(
            URI.create("http://127.0.0.1:" + backend.getAddress().getPort() + path))
        .GET()
        .build();
  }

  private static void pause(Duration duration) {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
