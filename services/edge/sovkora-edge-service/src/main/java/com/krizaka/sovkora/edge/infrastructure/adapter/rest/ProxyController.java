package com.krizaka.sovkora.edge.infrastructure.adapter.rest;

import com.krizaka.sovkora.edge.infrastructure.config.filter.ApiKeyProduct;
import com.krizaka.sovkora.edge.infrastructure.config.filter.RequestIdFilter;
import com.krizaka.sovkora.edge.infrastructure.support.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Streaming reverse proxy of the edge gateway: forwards every request to the backend resolved by
 * the route table and streams the response back chunk-by-chunk.
 *
 * <p>The per-chunk {@code flushBuffer()} on a virtual thread is the SSE pass-through:
 * token-by-token frames flow unbuffered for {@code text/event-stream} responses, so interactive
 * chat streaming stays synchronous through the edge (AGENTS.md §6 rule survives the migration).
 *
 * <p>Transport only: hop-by-hop headers and the platform's reserved {@code X-Sovkora-*} headers are
 * stripped, {@code X-Forwarded-*} is appended, status and body pass through verbatim —
 * authorization stays with the backend that owns the resource.
 *
 * <p>Fail fast (krizaka-internal#2132): every call passes the backend's circuit breaker. A backend
 * whose circuit is open, that refuses the connection, or that does not send its headers within the
 * response timeout is answered at once with a coded 503 and a {@code Retry-After} ({@link
 * BackendRefusal}), never with the backend's text and never by holding the request open.
 */
@RestController
public class ProxyController {

  private static final Logger logger = LoggerFactory.getLogger(ProxyController.class);

  /** Hop-by-hop request/response headers that must not be forwarded (RFC 9110 §7.6.1). */
  private static final Set<String> HOP_BY_HOP_HEADERS =
      Set.of(
          "connection",
          "keep-alive",
          "proxy-authenticate",
          "proxy-authorization",
          "te",
          "trailer",
          "transfer-encoding",
          "upgrade",
          "expect",
          "host",
          "content-length");

  /**
   * Request headers the platform reserves for itself (ADR-091): whatever names an identity between
   * services is written by a service, never by a client, so a client's copy is dropped here. Who is
   * calling is the session token the edge forwards (or the one it exchanged an API key for), which
   * each service verifies itself.
   */
  private static final String RESERVED_HEADER_PREFIX = "x-sovkora-";

  private static final int STREAM_BUFFER_BYTES = 8192;

  private final RestClient proxyRestClient;
  private final RouteResolver routeResolver;
  private final BackendBreaker backendBreaker;

  public ProxyController(
      RestClient proxyRestClient, RouteResolver routeResolver, BackendBreaker backendBreaker) {
    this.proxyRestClient = Objects.requireNonNull(proxyRestClient, "RestClient cannot be null");
    this.routeResolver = Objects.requireNonNull(routeResolver, "RouteResolver cannot be null");
    this.backendBreaker = Objects.requireNonNull(backendBreaker, "BackendBreaker cannot be null");
  }

  /** Catch-all proxy entry point — everything not served by the edge itself is forwarded. */
  @RequestMapping("/**")
  public void proxy(HttpServletRequest request, HttpServletResponse response) throws IOException {
    URI backend = routeResolver.resolve(request.getRequestURI());
    URI target = buildTargetUri(backend, request);
    BackendAdmission admission = backendBreaker.admit(backend);
    switch (admission) {
      case BackendAdmission.Refused(Duration retryAfter) ->
          BackendRefusal.UNAVAILABLE.write(response, retryAfter);
      case BackendAdmission.Granted(BackendCall call) ->
          forward(request, response, backend, target, call);
    }
  }

  /** Forwards one admitted call and reports its outcome to the backend's circuit. */
  private void forward(
      HttpServletRequest request,
      HttpServletResponse response,
      URI backend,
      URI target,
      BackendCall call)
      throws IOException {
    try {
      RestClient.RequestBodySpec spec =
          proxyRestClient
              .method(HttpMethod.valueOf(request.getMethod()))
              .uri(target)
              .headers(headers -> copyRequestHeaders(request, headers::add));
      if (hasBody(request)) {
        spec.body(outputStream -> request.getInputStream().transferTo(outputStream));
      }
      spec.exchange(
          (clientRequest, clientResponse) -> {
            call.answered(clientResponse.getStatusCode().value());
            relayResponse(clientResponse, response);
            return null;
          });
    } catch (ResourceAccessException e) {
      call.failed(e);
      BackendRefusal refusal =
          isResponseTimeout(e) ? BackendRefusal.TIMEOUT : BackendRefusal.UNAVAILABLE;
      if (refusal == BackendRefusal.TIMEOUT) {
        logger.warn("Backend timed out for {} {}", request.getMethod(), target, e);
      } else {
        logger.warn("Backend unreachable for {} {}", request.getMethod(), target, e);
      }
      if (!response.isCommitted()) {
        refusal.write(response, backendBreaker.retryAfter(backend));
      }
    } catch (UncheckedIOException e) {
      // Client disconnected mid-stream (SSE tab closed) — nothing left to relay.
      logger.debug("client closed the stream for {} {}", request.getMethod(), target, e);
    } finally {
      call.released();
    }
  }

  /** A response timeout, as opposed to a connect timeout, which is a backend not reachable. */
  private static boolean isResponseTimeout(ResourceAccessException e) {
    for (Throwable cause = e; cause != null; cause = cause.getCause()) {
      if (cause instanceof HttpConnectTimeoutException) {
        return false;
      }
      if (cause instanceof HttpTimeoutException) {
        return true;
      }
    }
    return false;
  }

  private static URI buildTargetUri(URI backend, HttpServletRequest request) {
    String query = request.getQueryString();
    String pathAndQuery = request.getRequestURI() + (query != null ? ("?" + query) : "");
    String base = backend.toString();
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    return URI.create(base + pathAndQuery);
  }

  private void copyRequestHeaders(HttpServletRequest request, HeaderSink sink) {
    for (String name : Collections.list(request.getHeaderNames())) {
      if (HOP_BY_HOP_HEADERS.contains(name.toLowerCase(Locale.ROOT))
          || RequestIdFilter.HEADER.equalsIgnoreCase(name)
          || name.toLowerCase(Locale.ROOT).startsWith(RESERVED_HEADER_PREFIX)) {
        continue;
      }
      for (String value : Collections.list(request.getHeaders(name))) {
        sink.add(name, value);
      }
    }
    // The one id of this request (RequestIdFilter): the caller's when well-formed, else generated.
    Object requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE);
    if (requestId != null) {
      sink.add(RequestIdFilter.HEADER, requestId.toString());
    }
    // The product the exchanged key declared, set by the edge itself: the loop above dropped any
    // x-sovkora-* header a client sent, so this is the only way one reaches a backend.
    if (request.getAttribute(ApiKeyProduct.ATTRIBUTE) instanceof String product) {
      sink.add(ApiKeyProduct.HEADER, product);
    }
    String existingForwardedFor = request.getHeader("X-Forwarded-For");
    sink.add(
        "X-Forwarded-For",
        existingForwardedFor != null
            ? (existingForwardedFor + ", " + request.getRemoteAddr())
            : request.getRemoteAddr());
    sink.add("X-Forwarded-Proto", request.getScheme());
    sink.add("X-Forwarded-Host", request.getServerName() + ":" + request.getServerPort());
  }

  private void relayResponse(
      org.springframework.http.client.ClientHttpResponse clientResponse,
      HttpServletResponse response)
      throws IOException {
    response.setStatus(clientResponse.getStatusCode().value());
    clientResponse
        .getHeaders()
        .forEach(
            (name, values) -> {
              if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase(Locale.ROOT))
                  && !RequestIdFilter.HEADER.equalsIgnoreCase(name)) {
                values.forEach(value -> response.addHeader(name, value));
              }
            });
    try (InputStream in = clientResponse.getBody()) {
      byte[] buffer = new byte[STREAM_BUFFER_BYTES];
      int read;
      while ((read = in.read(buffer)) != -1) {
        writeToClient(response, buffer, read);
      }
    }
  }

  /**
   * Writes one upstream chunk to the client. A failure here is the client leaving (a closed tab, a
   * Broken pipe), not the backend: it is raised as {@link UncheckedIOException} so the exchange
   * does not wrap it as a {@code ResourceAccessException} and the caller does not report the
   * backend as unreachable (krizaka-internal#2597).
   */
  private static void writeToClient(HttpServletResponse response, byte[] buffer, int length) {
    try {
      response.getOutputStream().write(buffer, 0, length);
      // Unbuffered relay: each upstream chunk (an SSE frame, a token) reaches the
      // client immediately instead of waiting for the response buffer to fill.
      response.flushBuffer();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static boolean hasBody(HttpServletRequest request) {
    return request.getContentLengthLong() > 0 || request.getHeader("Transfer-Encoding") != null;
  }

  /** Local functional sink so header copying stays testable without Spring types. */
  @FunctionalInterface
  interface HeaderSink {
    void add(String name, String value);
  }
}
