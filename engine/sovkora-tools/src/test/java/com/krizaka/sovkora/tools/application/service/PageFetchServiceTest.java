package com.krizaka.sovkora.tools.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.persistence.domain.ports.inbound.RuntimeConfigProvider;
import com.krizaka.sovkora.tools.domain.model.research.*;
import com.krizaka.sovkora.tools.domain.port.HostResolver;
import com.krizaka.sovkora.tools.domain.port.ResearchStore;
import com.krizaka.sovkora.tools.domain.port.TextExtractor;
import com.krizaka.sovkora.tools.infrastructure.adapter.http.PinnedHttpClient;
import com.krizaka.sovkora.tools.infrastructure.research.TestRateGate;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Every guard of a research fetch, in order, each one leaving its audit line (ADR-087 §4). */
class PageFetchServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");
  private static final UUID SITE = UUID.randomUUID();
  private static final UUID OTHER = UUID.randomUUID();

  private HttpServer server;
  private int port;
  private final AtomicInteger pageHits = new AtomicInteger();
  private String robots = "User-agent: *\nDisallow: /blocked\n";
  private String wwwRobots = "User-agent: *\nDisallow: /doc\n";
  private int robotsStatus = 200;
  private boolean robotsRedirectsToWww;

  private final Map<String, String> settings = new HashMap<>();
  private final InMemoryStore store = new InMemoryStore();
  private final TestRateGate rates = new TestRateGate();

  /** The resolver's next answers; loopback when the queue is empty. */
  private final Deque<InetAddress> nextAnswers = new ArrayDeque<>();

  private Instant now = NOW;
  private final Clock clock =
      new Clock() {
        @Override
        public ZoneId getZone() {
          return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
          return this;
        }

        @Override
        public Instant instant() {
          return now;
        }
      };

  /** Sources, documents and the audit, in memory — documents under their source, as the table. */
  private static final class InMemoryStore implements ResearchStore {
    final Map<String, ResearchSource> sources = new HashMap<>();
    final Map<String, FetchedDocument> documents = new HashMap<>();
    final List<FetchAudit> audit = new ArrayList<>();

    @Override
    public Optional<ResearchSource> source(String sourceKey) {
      return Optional.ofNullable(sources.get(sourceKey));
    }

    @Override
    public Optional<FetchedDocument> freshDocument(UUID sourceId, String url, Instant notBefore) {
      return Optional.ofNullable(documents.get(sourceId + "|" + url))
          .filter(d -> !d.fetchedAt().isBefore(notBefore));
    }

    @Override
    public Optional<FetchedDocument> document(UUID sourceId, String url, String contentHash) {
      return Optional.ofNullable(documents.get(sourceId + "|" + url))
          .filter(d -> d.contentHash().equals(contentHash));
    }

    @Override
    public void saveDocument(FetchedDocument document, String requestedUrl, UUID sourceId) {
      documents.put(sourceId + "|" + requestedUrl, document);
      documents.put(sourceId + "|" + document.url(), document);
    }

    @Override
    public void audit(FetchAudit audit) {
      this.audit.add(audit);
    }

    List<FetchAudit> fetches() {
      return audit.stream().filter(a -> "FETCH".equals(a.operation())).toList();
    }
  }

  private final class Settings implements RuntimeConfigProvider {
    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
      return settings.containsKey(key) ? Boolean.parseBoolean(settings.get(key)) : defaultValue;
    }

    @Override
    public int getInt(String key, int defaultValue) {
      return settings.containsKey(key) ? Integer.parseInt(settings.get(key)) : defaultValue;
    }

    @Override
    public String getString(String key, String defaultValue) {
      return settings.getOrDefault(key, defaultValue);
    }
  }

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/robots.txt",
        e -> {
          if (robotsRedirectsToWww) {
            e.getResponseHeaders().add("Location", url("www.site.test", "/robots-www.txt"));
            e.sendResponseHeaders(302, -1);
            e.close();
            return;
          }
          respond(e, robotsStatus, "text/plain", robots);
        });
    server.createContext("/robots-www.txt", e -> respond(e, 200, "text/plain", wwwRobots));
    server.createContext(
        "/doc",
        e -> {
          pageHits.incrementAndGet();
          respond(e, 200, "text/html", "<p>Article 1904.</p>");
        });
    server.createContext(
        "/blocked",
        e -> {
          pageHits.incrementAndGet();
          respond(e, 200, "text/html", "<p>no</p>");
        });
    server.createContext("/json", e -> respond(e, 200, "application/json", "{}"));
    server.createContext("/gone", e -> respond(e, 404, "text/html", "missing"));
    server.createContext(
        "/redir",
        e -> {
          e.getResponseHeaders().add("Location", "/doc");
          e.sendResponseHeaders(302, -1);
          e.close();
        });
    server.createContext(
        "/to-other",
        e -> {
          e.getResponseHeaders().add("Location", url("other.test", "/doc"));
          e.sendResponseHeaders(302, -1);
          e.close();
        });
    server.start();
    port = server.getAddress().getPort();
    settings.put("research.enabled", "true");
    settings.put("research.fetch.timeout-ms", "2000");
    store.sources.put(
        "site", source(SITE, "site", true, List.of("site.test/**"), List.of(), 3600, 10));
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  private static void respond(
      com.sun.net.httpserver.HttpExchange e, int status, String type, String body)
      throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    e.getResponseHeaders().add("Content-Type", type);
    e.sendResponseHeaders(status, bytes.length);
    try (OutputStream out = e.getResponseBody()) {
      out.write(bytes);
    }
    e.close();
  }

  private static ResearchSource source(
      UUID id,
      String key,
      boolean enabled,
      List<String> allow,
      List<String> deny,
      int freshness,
      int rate) {
    return new ResearchSource(
        id, key, "FETCH", "http", allow, deny, true, freshness, rate, enabled);
  }

  private PageFetchService service() {
    TextExtractor extractor =
        (body, type, charset, base) ->
            new TextExtractor.Extracted("t", new String(body, StandardCharsets.UTF_8));
    HostResolver resolver =
        host -> {
          InetAddress next = nextAnswers.poll();
          return List.of(next == null ? InetAddress.getLoopbackAddress() : next);
        };
    return new PageFetchService(
        new Settings(),
        store,
        new PinnedHttpClient(resolver, InetAddress::isLoopbackAddress),
        extractor,
        rates,
        clock);
  }

  private String url(String host, String path) {
    return "http://" + host + ":" + port + path;
  }

  private String url(String path) {
    return url("site.test", path);
  }

  private FetchResult fetch(PageFetchService service, String sourceKey, String url) {
    return service.fetch(new FetchRequest(sourceKey, url, "actor-1", null, null));
  }

  private FetchResult fetch(String url) {
    return fetch(service(), "site", url);
  }

  private static InetAddress inside() {
    try {
      return InetAddress.getByName("10.0.0.7");
    } catch (UnknownHostException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  @Test
  @DisplayName("research off: nothing is read, and the refusal is audited")
  void offByDefault() {
    settings.remove("research.enabled");
    assertThat(fetch(url("/doc")).refusal()).contains(FetchRefusal.DISABLED);
    assertThat(pageHits).hasValue(0);
    assertThat(store.fetches())
        .singleElement()
        .extracting(FetchAudit::refusal)
        .isEqualTo(FetchRefusal.DISABLED);
  }

  @Test
  @DisplayName("a disabled or unknown source reads nothing")
  void disabledSource() {
    store.sources.put(
        "site", source(SITE, "site", false, List.of("site.test/**"), List.of(), 3600, 10));
    assertThat(fetch(url("/doc")).refusal()).contains(FetchRefusal.DISABLED);
    assertThat(fetch(service(), "nope", url("/doc")).refusal()).contains(FetchRefusal.DISABLED);
  }

  @Test
  @DisplayName("http only where the source allows it")
  void httpOnlyWhenAllowed() {
    store.sources.put(
        "site",
        new ResearchSource(
            SITE,
            "site",
            "FETCH",
            "http",
            List.of("site.test/**"),
            List.of(),
            false,
            3600,
            10,
            true));
    assertThat(fetch(url("/doc")).refusal()).contains(FetchRefusal.SCHEME);
  }

  @Test
  @DisplayName("outside the allow list, nothing is read")
  void allowList() {
    store.sources.put(
        "site", source(SITE, "site", true, List.of("site.test/other/**"), List.of(), 3600, 10));
    assertThat(fetch(url("/doc")).refusal()).contains(FetchRefusal.ALLOWLIST);
    assertThat(pageHits).hasValue(0);
  }

  @Test
  @DisplayName(
      "a page is read, stored under its source and hash; the audit has a path hash, never the query")
  void readsStoresAndAudits() {
    FetchResult result = fetch(url("/doc?q=secret-query"));
    assertThat(result.document()).isPresent();
    FetchedDocument doc = result.document().get();
    assertThat(doc.text()).isEqualTo("<p>Article 1904.</p>");
    assertThat(doc.url()).isEqualTo(url("/doc?q=secret-query"));
    assertThat(doc.contentHash()).hasSize(64);
    assertThat(doc.expiresAt()).isEqualTo(NOW.plusSeconds(3600));
    assertThat(store.documents).containsKey(SITE + "|" + url("/doc?q=secret-query"));
    FetchAudit line = store.fetches().getLast();
    assertThat(line.refusal()).isNull();
    assertThat(line.httpStatus()).isEqualTo(200);
    assertThat(line.pathHash()).hasSize(64).doesNotContain("secret");
    assertThat(line.host()).isEqualTo("site.test");
    assertThat(line.toString()).doesNotContain("secret-query");
  }

  @Test
  @DisplayName("within freshness, a second read through the same source makes no HTTP call")
  void cacheHit() {
    fetch(url("/doc"));
    FetchResult again = fetch(url("/doc"));
    assertThat(again.cacheHit()).isTrue();
    assertThat(pageHits).hasValue(1);
    assertThat(store.fetches().getLast().cacheHit()).isTrue();
  }

  @Test
  @DisplayName("a hit is served only through the source that read the document")
  void cacheIsScopedToTheSource() {
    store.sources.put(
        "other", source(OTHER, "other", true, List.of("site.test/**"), List.of(), 3600, 10));
    fetch(url("/doc"));
    FetchResult throughOther = fetch(service(), "other", url("/doc"));
    assertThat(throughOther.cacheHit()).isFalse();
    assertThat(throughOther.document()).isPresent();
    assertThat(pageHits).hasValue(2);
  }

  @Test
  @DisplayName(
      "a hit is served only within the source's current freshness, not the one it was stored with")
  void cacheHonoursTheSourcesCurrentFreshness() {
    fetch(url("/doc"));
    now = NOW.plusSeconds(3_601);
    assertThat(fetch(url("/doc")).cacheHit()).as("older than the source's freshness").isFalse();
    assertThat(pageHits).hasValue(2);

    // The administrator shortens the freshness: what was fresh a moment ago is not any more.
    store.sources.put(
        "site", source(SITE, "site", true, List.of("site.test/**"), List.of(), 60, 10));
    now = now.plusSeconds(30);
    assertThat(fetch(url("/doc")).cacheHit()).isTrue();
    now = now.plusSeconds(31);
    assertThat(fetch(url("/doc")).cacheHit()).isFalse();
    assertThat(pageHits).hasValue(3);
  }

  @Test
  @DisplayName("a hit's final URL passes the source's current policy, or the page is read again")
  void cacheHitFollowsTheSourcesCurrentPolicy() {
    FetchResult redirected = fetch(url("/redir"));
    assertThat(redirected.document()).map(FetchedDocument::url).contains(url("/doc"));
    assertThat(pageHits).hasValue(1);

    // The administrator now denies /doc: the cached document read through /redir no longer
    // serves, and the live redirect to /doc is refused on its hop.
    store.sources.put(
        "site",
        source(SITE, "site", true, List.of("site.test/**"), List.of("site.test/doc"), 3600, 10));
    FetchResult again = fetch(url("/redir"));
    assertThat(again.cacheHit()).isFalse();
    assertThat(again.refusal()).contains(FetchRefusal.ALLOWLIST);
    assertThat(pageHits).hasValue(1);
  }

  @Test
  @DisplayName("a redirected document keeps its final URL and is a hit under both URLs")
  void redirectedDocumentKeepsItsFinalUrl() {
    FetchResult viaRedirect = fetch(url("/redir"));
    assertThat(viaRedirect.document()).map(FetchedDocument::url).contains(url("/doc"));
    FetchResult direct = fetch(url("/doc"));
    assertThat(direct.cacheHit()).isTrue();
    assertThat(direct.document()).map(FetchedDocument::url).contains(url("/doc"));
    assertThat(fetch(url("/redir")).cacheHit()).isTrue();
    assertThat(pageHits).hasValue(1);
  }

  @Test
  @DisplayName("robots.txt is honoured; a 5xx robots.txt allows nothing; a 404 allows everything")
  void robots() {
    assertThat(fetch(url("/blocked")).refusal()).contains(FetchRefusal.ROBOTS);
    assertThat(pageHits).hasValue(0);
    assertThat(store.audit).anyMatch(a -> "ROBOTS".equals(a.operation()));

    robotsStatus = 503;
    assertThat(fetch(url("/doc")).refusal()).contains(FetchRefusal.ROBOTS);

    robotsStatus = 404;
    assertThat(fetch(url("/doc")).document()).isPresent();
  }

  @Test
  @DisplayName(
      "a robots.txt that resolves to the inside allows nothing: the page is not read without it")
  void robotsRefusedForSsrfAllowsNothing() {
    // The page's host answers public (the loopback the test admits); robots.txt's answers inside.
    nextAnswers.add(InetAddress.getLoopbackAddress());
    nextAnswers.add(inside());
    assertThat(fetch(url("/doc")).refusal()).contains(FetchRefusal.ROBOTS);
    assertThat(pageHits).hasValue(0);
    assertThat(store.audit)
        .anyMatch(a -> "ROBOTS".equals(a.operation()) && a.refusal() == FetchRefusal.SSRF);
  }

  @Test
  @DisplayName(
      "the robots rules are cached per product token: a changed user agent reads them again")
  void robotsRulesAreKeyedByProductToken() {
    robots = "User-agent: Alpha\nDisallow: /doc\n\nUser-agent: *\nDisallow:\n";
    PageFetchService one = service();
    assertThat(fetch(one, "site", url("/doc")).document()).isPresent();

    settings.put("research.user-agent", "Alpha/1.0 (+https://www.sovkora.com)");
    assertThat(fetch(one, "site", url("/doc?x=1")).refusal()).contains(FetchRefusal.ROBOTS);
    assertThat(pageHits).hasValue(1);
  }

  @Test
  @DisplayName(
      "robots.txt may redirect across origins (RFC 9309 §2.3.1.2); the rules apply to the page's origin")
  void robotsFollowsRedirectsAcrossOrigins() {
    robotsRedirectsToWww = true;
    assertThat(fetch(url("/doc")).refusal()).contains(FetchRefusal.ROBOTS);
    assertThat(pageHits).hasValue(0);

    wwwRobots = "User-agent: *\nDisallow:\n";
    assertThat(fetch(url("/doc")).document()).isPresent();
  }

  @Test
  @DisplayName(
      "the host's budget is spent per hop: a redirect to an exhausted host, or on the same host, is RATE")
  void hostBudgetIsSpentPerHop() {
    settings.put("research.fetch.per-host-per-minute", "2");
    PageFetchService one = service();
    assertThat(fetch(one, "site", url("/redir")).document()).isPresent();
    assertThat(fetch(one, "site", url("/gone")).refusal()).contains(FetchRefusal.RATE);

    store.sources.put(
        "both",
        source(OTHER, "both", true, List.of("site.test/**", "other.test/**"), List.of(), 3600, 10));
    settings.put("research.fetch.per-host-per-minute", "1");
    now = NOW.plusSeconds(60);
    assertThat(fetch(one, "both", url("other.test", "/doc")).document()).isPresent();
    FetchResult toExhausted = fetch(one, "both", url("site.test", "/to-other"));
    assertThat(toExhausted.refusal()).contains(FetchRefusal.RATE);
    assertThat(pageHits).hasValue(2);
  }

  @Test
  @DisplayName(
      "percent-escaped paths are compared in canonical form, by the patterns and by robots.txt")
  void encodedPathsCompareInCanonicalForm() {
    store.sources.put(
        "site",
        source(
            SITE,
            "site",
            true,
            List.of("site.test/**"),
            List.of("site.test/private/**"),
            3600,
            10));
    assertThat(fetch(url("/%70rivate/x")).refusal()).contains(FetchRefusal.ALLOWLIST);
    assertThat(fetch(url("/%62locked")).refusal()).contains(FetchRefusal.ROBOTS);
    assertThat(pageHits).hasValue(0);
  }

  @Test
  @DisplayName("the source's rate is a hard stop")
  void rate() {
    store.sources.put(
        "site", source(SITE, "site", true, List.of("site.test/**"), List.of(), 3600, 1));
    PageFetchService one = service();
    assertThat(fetch(one, "site", url("/doc")).document()).isPresent();
    assertThat(fetch(one, "site", url("/gone")).refusal()).contains(FetchRefusal.RATE);
  }

  @Test
  @DisplayName("an error status or an unread content type is refused and not stored")
  void statusAndType() {
    assertThat(fetch(url("/gone")).refusal()).contains(FetchRefusal.HTTP_ERROR);
    assertThat(fetch(url("/json")).refusal()).contains(FetchRefusal.CONTENT_TYPE);
    assertThat(store.documents).isEmpty();
  }
}
