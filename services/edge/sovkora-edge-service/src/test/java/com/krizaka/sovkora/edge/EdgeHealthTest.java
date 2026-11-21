package com.krizaka.sovkora.edge;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * The edge's health (krizaka-restricted#348): the deploy health check reads {@code /healthz} on the
 * public port, which the edge answers itself with its status alone; no health answer of the edge
 * carries a component or a detail.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "management.server.port=0")
class EdgeHealthTest {

  @LocalServerPort private int serverPort;
  @LocalManagementPort private int managementPort;

  private final HttpClient client = HttpClient.newHttpClient();

  private HttpResponse<String> get(int port, String path) throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  @Test
  @DisplayName("[r348] /healthz on the public port is the edge's own status, nothing else")
  void healthzIsTheStatusAlone() throws Exception {
    HttpResponse<String> response = get(serverPort, "/healthz");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo("{\"status\":\"UP\"}");
  }

  @Test
  @DisplayName("[r348] the management health carries no component and no detail")
  void managementHealthHasNoDetail() throws Exception {
    HttpResponse<String> response = get(managementPort, "/actuator/health");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).doesNotContain("components", "details");
  }
}
