package com.krizaka.sovkora.tools.infrastructure.adapter.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.tools.domain.model.research.FetchLimits;
import com.krizaka.sovkora.tools.domain.model.research.FetchRefusal;
import com.krizaka.sovkora.tools.domain.port.HostResolver;
import com.krizaka.sovkora.tools.domain.port.SafeHttp;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The SSRF guard against a real local server (ADR-087 §4). The server listens on loopback, so the
 * tests that must reach it use a policy that admits loopback — and treat 10/8 as "the inside"; the
 * production policy is tested on its own: it refuses loopback before any byte is sent.
 */
class PinnedHttpClientTest {

  private static final Predicate<InetAddress> LOOPBACK_ONLY = InetAddress::isLoopbackAddress;
  private static final FetchLimits LIMITS =
      new FetchLimits(1_500, 1_024, 2, 30, List.of("text/html"), "SovkoraResearch/0.1", 60);

  private HttpServer server;
  private int port;
  private final AtomicInteger hits = new AtomicInteger();
  private final List<String> cookiesSeen = new ArrayList<>();
  private final List<String> hostsSeen = new ArrayList<>();

  /** A resolver that answers from a table and counts its calls per host. */
  private static final class TableResolver implements HostResolver {
    private final Map<String, List<List<InetAddress>>> answers = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();

    TableResolver answer(String host, String... literals) {
      List<InetAddress> one = new ArrayList<>();
      for (String literal : literals) {
        try {
          one.add(InetAddress.getByName(literal));
        } catch (UnknownHostException e) {
          throw new IllegalArgumentException(e);
        }
      }
      answers.computeIfAbsent(host, h -> new ArrayList<>()).add(one);
      return this;
    }

    int calls(String host) {
      return calls.getOrDefault(host, new AtomicInteger()).get();
    }

    @Override
    public List<InetAddress> resolve(String host) throws UnknownHostException {
      int n = calls.computeIfAbsent(host, h -> new AtomicInteger()).getAndIncrement();
      List<List<InetAddress>> seq = answers.get(host);
      if (seq == null) {
        throw new UnknownHostException(host);
      }
      return seq.get(Math.min(n, seq.size() - 1));
    }
  }

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/page",
        e ->
            reply(e, 200, "text/html; charset=utf-8", "<p>hello</p>", Map.of("Set-Cookie", "s=1")));
    server.createContext("/again", e -> reply(e, 200, "text/html", "<p>again</p>", Map.of()));
    server.createContext("/big", e -> reply(e, 200, "text/html", "x".repeat(4_096), Map.of()));
    server.createContext(
        "/slow",
        e -> {
          try {
            Thread.sleep(3_000);
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
          }
          reply(e, 200, "text/html", "late", Map.of());
        });
    server.createContext(
        "/to-inside",
        e ->
            reply(
                e,
                302,
                "text/html",
                "",
                Map.of("Location", "http://inside.test:" + port + "/page")));
    server.createContext("/loop", e -> reply(e, 302, "text/html", "", Map.of("Location", "/loop")));
    server.createContext(
        "/trickle",
        e -> {
          // One byte well inside the socket timeout, for far longer than the whole-call cap.
          hits.incrementAndGet();
          e.getResponseHeaders().add("Content-Type", "text/html");
          e.sendResponseHeaders(200, 0);
          try (OutputStream out = e.getResponseBody()) {
            for (int i = 0; i < 40; i++) {
              out.write('x');
              out.flush();
              Thread.sleep(250);
            }
          } catch (IOException | InterruptedException gone) {
            Thread.currentThread().interrupt();
          }
        });
    server.start();
    port = server.getAddress().getPort();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private void reply(
      HttpExchange e, int status, String type, String body, Map<String, String> headers)
      throws IOException {
    hits.incrementAndGet();
    hostsSeen.add(e.getRequestHeaders().getFirst("Host"));
    String cookie = e.getRequestHeaders().getFirst("Cookie");
    if (cookie != null) {
      cookiesSeen.add(cookie);
    }
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    e.getResponseHeaders().add("Content-Type", type);
    headers.forEach((k, v) -> e.getResponseHeaders().add(k, v));
    e.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
    if (bytes.length > 0) {
      try (OutputStream out = e.getResponseBody()) {
        out.write(bytes);
      }
    }
    e.close();
  }

  private URI uri(String host, String path) {
    return URI.create("http://" + host + ":" + port + path);
  }

  private static SafeHttp.Outcome get(SafeHttp http, URI uri) {
    return http.get(uri, LIMITS, hop -> Optional.empty());
  }

  @Test
  @DisplayName("the production policy refuses loopback before a byte is sent")
  void productionPolicyRefusesLoopback() {
    TableResolver resolver = new TableResolver().answer("site.test", "127.0.0.1");
    SafeHttp.Outcome outcome = get(new PinnedHttpClient(resolver), uri("site.test", "/page"));
    assertThat(outcome.refusal()).contains(FetchRefusal.SSRF);
    assertThat(hits).hasValue(0);
  }

  @Test
  @DisplayName("a host with one inside address among public ones is refused")
  void oneInsideAddressRefusesTheHost() {
    TableResolver resolver = new TableResolver().answer("mixed.test", "127.0.0.1", "10.0.0.7");
    SafeHttp.Outcome outcome =
        get(new PinnedHttpClient(resolver, LOOPBACK_ONLY), uri("mixed.test", "/page"));
    assertThat(outcome.refusal()).contains(FetchRefusal.SSRF);
    assertThat(hits).hasValue(0);
  }

  @Test
  @DisplayName("a page is read on the checked address, with its host name, without cookies")
  void readsWithoutCookies() {
    TableResolver resolver = new TableResolver().answer("site.test", "127.0.0.1");
    PinnedHttpClient http = new PinnedHttpClient(resolver, LOOPBACK_ONLY);
    SafeHttp.Outcome first = get(http, uri("site.test", "/page"));
    SafeHttp.Outcome second = get(http, uri("site.test", "/again"));
    assertThat(first.response()).isPresent();
    assertThat(first.response().get().status()).isEqualTo(200);
    assertThat(first.response().get().contentType()).isEqualTo("text/html");
    assertThat(first.response().get().charset()).isEqualTo("utf-8");
    assertThat(second.response().get().charset()).isNull();
    assertThat(new String(first.response().get().body(), StandardCharsets.UTF_8))
        .isEqualTo("<p>hello</p>");
    assertThat(second.response()).isPresent();
    assertThat(cookiesSeen).isEmpty();
    assertThat(hostsSeen).allMatch(h -> h.startsWith("site.test"));
  }

  @Test
  @DisplayName("rebinding: resolved once per hop, the connection uses the address that was checked")
  void rebindingCannotReachTheConnection() {
    // First answer is the one checked (loopback, admitted by the test policy); any later answer
    // would be "the inside". The client must never ask again for the same hop.
    TableResolver resolver =
        new TableResolver().answer("rebind.test", "127.0.0.1").answer("rebind.test", "10.0.0.7");
    SafeHttp.Outcome outcome =
        get(new PinnedHttpClient(resolver, LOOPBACK_ONLY), uri("rebind.test", "/page"));
    assertThat(outcome.response()).isPresent();
    assertThat(resolver.calls("rebind.test")).isEqualTo(1);
  }

  @Test
  @DisplayName("rebinding the other way: an inside first answer is refused, nothing is sent")
  void insideFirstAnswerIsRefused() {
    TableResolver resolver =
        new TableResolver().answer("rebind2.test", "10.0.0.7").answer("rebind2.test", "127.0.0.1");
    SafeHttp.Outcome outcome =
        get(new PinnedHttpClient(resolver, LOOPBACK_ONLY), uri("rebind2.test", "/page"));
    assertThat(outcome.refusal()).contains(FetchRefusal.SSRF);
    assertThat(hits).hasValue(0);
  }

  @Test
  @DisplayName("a redirect to the inside is checked again and refused")
  void redirectToTheInsideIsRefused() {
    TableResolver resolver =
        new TableResolver().answer("site.test", "127.0.0.1").answer("inside.test", "10.0.0.7");
    SafeHttp.Outcome outcome =
        get(new PinnedHttpClient(resolver, LOOPBACK_ONLY), uri("site.test", "/to-inside"));
    assertThat(outcome.refusal()).contains(FetchRefusal.SSRF);
    assertThat(hits).hasValue(1);
  }

  @Test
  @DisplayName("the caller's hop check runs on every hop, the first included")
  void hopCheckOnEveryHop() {
    TableResolver resolver = new TableResolver().answer("site.test", "127.0.0.1");
    List<String> checked = new ArrayList<>();
    SafeHttp.Outcome outcome =
        new PinnedHttpClient(resolver, LOOPBACK_ONLY)
            .get(
                uri("site.test", "/page"),
                LIMITS,
                hop -> {
                  checked.add(hop.getPath());
                  return Optional.of(FetchRefusal.ALLOWLIST);
                });
    assertThat(outcome.refusal()).contains(FetchRefusal.ALLOWLIST);
    assertThat(checked).containsExactly("/page");
    assertThat(hits).hasValue(0);
  }

  @Test
  @DisplayName("too many redirects, too large, too slow")
  void caps() {
    TableResolver resolver = new TableResolver().answer("site.test", "127.0.0.1");
    PinnedHttpClient http = new PinnedHttpClient(resolver, LOOPBACK_ONLY);
    assertThat(get(http, uri("site.test", "/loop")).refusal()).contains(FetchRefusal.REDIRECTS);
    assertThat(get(http, uri("site.test", "/big")).refusal()).contains(FetchRefusal.SIZE);
    assertThat(get(http, uri("site.test", "/slow")).refusal()).contains(FetchRefusal.TIMEOUT);
  }

  @Test
  @DisplayName(
      "a body that trickles in stops at the whole-call deadline, not at the socket timeout")
  void slowTrickleStopsAtTheDeadline() {
    TableResolver resolver = new TableResolver().answer("site.test", "127.0.0.1");
    long started = System.nanoTime();
    SafeHttp.Outcome outcome =
        get(new PinnedHttpClient(resolver, LOOPBACK_ONLY), uri("site.test", "/trickle"));
    long elapsedMs = (System.nanoTime() - started) / 1_000_000;
    assertThat(outcome.refusal()).contains(FetchRefusal.TIMEOUT);
    // The cap is 1.5 s; the server would have kept the read busy for 10 s.
    assertThat(elapsedMs).isLessThan(5_000);
  }

  @Test
  @DisplayName("an unknown scheme is refused without resolving")
  void schemes() {
    TableResolver resolver = new TableResolver();
    PinnedHttpClient http = new PinnedHttpClient(resolver, LOOPBACK_ONLY);
    assertThat(get(http, URI.create("file:///etc/passwd")).refusal()).contains(FetchRefusal.SCHEME);
    assertThat(get(http, URI.create("gopher://site.test/")).refusal())
        .contains(FetchRefusal.SCHEME);
  }
}
