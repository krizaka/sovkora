package com.krizaka.sovkora.edge.infrastructure.adapter.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.edge.infrastructure.config.EdgeRoutesProperties;
import com.krizaka.sovkora.edge.infrastructure.config.filter.ApiKeyProduct;
import com.krizaka.sovkora.edge.infrastructure.support.*;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.client.RestClient;

/** krizaka-internal#3368: the product a key declares reaches the backends, and only that one. */
class ProxyProductHeaderTest {

  private final Map<String, String> seen = new ConcurrentHashMap<>();
  private HttpServer backend;

  @AfterEach
  void stop() {
    backend.stop(0);
  }

  @Test
  @DisplayName("[3368] the product the exchanged key declared is the header a backend reads")
  void declaredProductIsForwarded() throws Exception {
    ProxyController controller = start();
    MockHttpServletRequest request = request();
    request.setAttribute(ApiKeyProduct.ATTRIBUTE, "verikora");

    controller.proxy(request, new MockHttpServletResponse());

    assertThat(seen.get(ApiKeyProduct.HEADER.toLowerCase())).isEqualTo("verikora");
  }

  @Test
  @DisplayName("[3368] a client's own product header is dropped, never forwarded as the product")
  void clientHeaderIsNotAProduct() throws Exception {
    ProxyController controller = start();
    MockHttpServletRequest request = request();
    request.addHeader(ApiKeyProduct.HEADER, "folioviv");

    controller.proxy(request, new MockHttpServletResponse());

    assertThat(seen).doesNotContainKey(ApiKeyProduct.HEADER.toLowerCase());
  }

  private static MockHttpServletRequest request() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/studios");
    request.setRequestURI("/api/v1/studios");
    return request;
  }

  private ProxyController start() throws IOException {
    backend = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    backend.createContext(
        "/",
        exchange -> {
          exchange
              .getRequestHeaders()
              .forEach((name, values) -> seen.put(name.toLowerCase(), values.getFirst()));
          exchange.sendResponseHeaders(204, -1);
          exchange.getResponseBody().close();
        });
    backend.start();
    URI base = URI.create("http://127.0.0.1:" + backend.getAddress().getPort());
    RouteResolver resolver =
        new RouteResolver(
            new EdgeRoutesProperties(List.of(new EdgeRoutesProperties.Route("/", base))));
    return new ProxyController(RestClient.builder().build(), resolver, new ClosedBreaker());
  }

  private static final class ClosedBreaker implements BackendBreaker, BackendCall {
    @Override
    public BackendAdmission admit(URI backend) {
      return new BackendAdmission.Granted(this);
    }

    @Override
    public Duration retryAfter(URI backend) {
      return Duration.ofSeconds(1);
    }

    @Override
    public void answered(int status) {
      // the outcome is not what this test looks at
    }

    @Override
    public void failed(Throwable cause) {
      // the outcome is not what this test looks at
    }

    @Override
    public void released() {
      // the outcome is not what this test looks at
    }
  }
}
