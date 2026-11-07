package com.krizaka.sovkora.tools.infrastructure.adapter.http;

import com.krizaka.sovkora.tools.domain.model.research.FetchLimits;
import com.krizaka.sovkora.tools.domain.model.research.FetchRefusal;
import com.krizaka.sovkora.tools.domain.model.research.PublicAddresses;
import com.krizaka.sovkora.tools.domain.port.HostResolver;
import com.krizaka.sovkora.tools.domain.port.SafeHttp;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The research HTTP client that cannot be pointed at the inside (ADR-087 §4).
 *
 * <p>Per hop: the host is resolved <b>once</b>; every address must be public; then the caller's hop
 * check runs (scheme, the source's patterns, robots.txt); then the connection goes to exactly the
 * addresses that were checked — the client's resolver answers from that list and nothing else, so a
 * DNS answer that changes between the check and the connection (rebinding) cannot reach the
 * connection. TLS still verifies the certificate against the host name. Redirects are not followed
 * by the library: each one is a new hop, checked again. No cookies are sent or kept.
 *
 * <p>Time is one cap for the whole call: {@code timeoutMs} starts at the first hop and ends the
 * read wherever it stands — connecting, waiting for headers, or reading a body that arrives a byte
 * at a time. The socket and response timeouts bound inactivity within what is left of it; the
 * deadline bounds the total, so a server that trickles stays within the limit, not below the radar.
 * The body is capped at {@code maxBytes}.
 */
@Component
public class PinnedHttpClient implements SafeHttp {

  private final HostResolver resolver;
  private final Predicate<InetAddress> addressPolicy;

  /**
   * The production client: public addresses only.
   *
   * @param resolver the host resolver
   */
  @Autowired
  public PinnedHttpClient(HostResolver resolver) {
    this(resolver, PublicAddresses::isPublic);
  }

  /**
   * A client with another address policy — for tests that serve pages on a local port only.
   *
   * @param resolver the host resolver
   * @param addressPolicy which addresses may be connected to
   */
  public PinnedHttpClient(HostResolver resolver, Predicate<InetAddress> addressPolicy) {
    this.resolver = Objects.requireNonNull(resolver, "HostResolver cannot be null");
    this.addressPolicy = Objects.requireNonNull(addressPolicy, "address policy cannot be null");
  }

  @Override
  public Outcome get(URI uri, FetchLimits limits, Function<URI, Optional<FetchRefusal>> hopCheck) {
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(limits.timeoutMs());
    URI hop = uri;
    for (int hops = 0; hops <= limits.maxRedirects(); hops++) {
      String scheme = hop.getScheme() == null ? "" : hop.getScheme().toLowerCase(Locale.ROOT);
      if (!("https".equals(scheme) || "http".equals(scheme)) || hop.getHost() == null) {
        return refused(FetchRefusal.SCHEME);
      }
      List<InetAddress> addresses;
      try {
        addresses = resolver.resolve(hop.getHost());
      } catch (UnknownHostException unknown) {
        return refused(FetchRefusal.HTTP_ERROR);
      }
      if (addresses.isEmpty() || !addresses.stream().allMatch(addressPolicy)) {
        return refused(FetchRefusal.SSRF);
      }
      Optional<FetchRefusal> refusal = hopCheck.apply(hop);
      if (refusal.isPresent()) {
        return refused(refusal.get());
      }
      HopAnswer answer;
      try {
        answer = read(hop, addresses, limits, deadline);
      } catch (InterruptedIOException slow) {
        return refused(FetchRefusal.TIMEOUT);
      } catch (TooLargeException tooLarge) {
        return refused(FetchRefusal.SIZE);
      } catch (IOException failed) {
        return refused(FetchRefusal.HTTP_ERROR);
      }
      if (answer.status() >= 300 && answer.status() < 400) {
        if (answer.location() == null) {
          return refused(FetchRefusal.REDIRECTS);
        }
        try {
          hop = hop.resolve(answer.location()).normalize();
        } catch (IllegalArgumentException badLocation) {
          return refused(FetchRefusal.REDIRECTS);
        }
        continue;
      }
      return new Outcome(Optional.of(answer.response(hop)), Optional.empty());
    }
    return refused(FetchRefusal.REDIRECTS);
  }

  private static Outcome refused(FetchRefusal reason) {
    return new Outcome(Optional.empty(), Optional.of(reason));
  }

  private record HopAnswer(
      int status,
      String contentType,
      String charset,
      byte[] body,
      String location,
      String etag,
      String lastModified) {
    @Override
    public boolean equals(Object other) {
      return other instanceof HopAnswer that
          && status == that.status
          && Arrays.equals(body, that.body)
          && Objects.equals(contentType, that.contentType)
          && Objects.equals(charset, that.charset)
          && Objects.equals(location, that.location)
          && Objects.equals(etag, that.etag)
          && Objects.equals(lastModified, that.lastModified);
    }

    @Override
    public int hashCode() {
      return 31 * Objects.hash(status, contentType, charset, location, etag, lastModified)
          + Arrays.hashCode(body);
    }

    @Override
    public String toString() {
      return "HopAnswer[status="
          + status
          + ", body="
          + (body == null ? 0 : body.length)
          + " bytes]";
    }

    Response response(URI finalUri) {
      return new Response(finalUri, status, contentType, charset, body, etag, lastModified);
    }
  }

  private static final class TooLargeException extends IOException {
    private static final long serialVersionUID = 1L;

    TooLargeException() {
      super("body larger than the research limit");
    }
  }

  /** Answers only for the checked host, and only with the checked addresses. */
  private record PinnedResolver(String host, List<InetAddress> addresses) implements DnsResolver {
    @Override
    public InetAddress[] resolve(String name) throws UnknownHostException {
      if (!name.equalsIgnoreCase(host)) {
        throw new UnknownHostException(name);
      }
      return addresses.toArray(new InetAddress[0]);
    }

    @Override
    public String resolveCanonicalHostname(String name) {
      return name;
    }
  }

  private HopAnswer read(URI hop, List<InetAddress> checked, FetchLimits limits, long deadline)
      throws IOException {
    long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
    if (remainingMs <= 0) {
      throw new InterruptedIOException("the research deadline passed before this hop");
    }
    String host = hop.getHost();
    DnsResolver pinnedResolver = new PinnedResolver(host, List.copyOf(checked));
    // Inactivity is bounded by what is left of the whole call, never by more.
    Timeout timeout = Timeout.of(Math.min(limits.timeoutMs(), remainingMs), TimeUnit.MILLISECONDS);
    try (CloseableHttpClient client =
        HttpClients.custom()
            .setConnectionManager(
                PoolingHttpClientConnectionManagerBuilder.create()
                    .setDnsResolver(pinnedResolver)
                    .setDefaultConnectionConfig(
                        ConnectionConfig.custom()
                            .setConnectTimeout(timeout)
                            .setSocketTimeout(timeout)
                            .build())
                    .build())
            .setDefaultRequestConfig(
                RequestConfig.custom()
                    .setResponseTimeout(timeout)
                    .setRedirectsEnabled(false)
                    .build())
            .disableRedirectHandling()
            .disableCookieManagement()
            .disableAuthCaching()
            .setUserAgent(limits.userAgent())
            .build()) {
      HttpGet get = new HttpGet(hop);
      get.setHeader("Accept", String.join(", ", limits.contentTypes()) + ", */*;q=0.1");
      return client.execute(get, response -> answer(get, response, limits.maxBytes(), deadline));
    }
  }

  private static HopAnswer answer(
      HttpGet get, ClassicHttpResponse response, int maxBytes, long deadline) throws IOException {
    HttpEntity entity = response.getEntity();
    if (entity != null && entity.getContentLength() > maxBytes) {
      // Abort before the close: a closed response drains what is left of the body first.
      get.abort();
      throw new TooLargeException();
    }
    byte[] body =
        entity == null ? new byte[0] : capped(get, entity.getContent(), maxBytes, deadline);
    String type = entity == null || entity.getContentType() == null ? "" : entity.getContentType();
    int semicolon = type.indexOf(';');
    String mediaType =
        (semicolon < 0 ? type : type.substring(0, semicolon)).trim().toLowerCase(Locale.ROOT);
    return new HopAnswer(
        response.getCode(),
        mediaType,
        charsetOf(semicolon < 0 ? "" : type.substring(semicolon + 1)),
        body,
        header(response, "Location"),
        header(response, "ETag"),
        header(response, "Last-Modified"));
  }

  /** The {@code charset} parameter of a Content-Type, as the server wrote it, or {@code null}. */
  private static String charsetOf(String parameters) {
    for (String parameter : parameters.split(";")) {
      int equals = parameter.indexOf('=');
      if (equals > 0 && "charset".equalsIgnoreCase(parameter.substring(0, equals).trim())) {
        String value = parameter.substring(equals + 1).trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
          value = value.substring(1, value.length() - 1);
        }
        return value.isEmpty() ? null : value;
      }
    }
    return null;
  }

  private static String header(ClassicHttpResponse response, String name) {
    Header header = response.getFirstHeader(name);
    return header == null ? null : header.getValue();
  }

  /**
   * Reads the body within the size cap and the deadline. A cap that trips <b>aborts the request</b>
   * before the stream is closed: closing a chunked stream drains the rest of the body to keep the
   * connection reusable, which is exactly the wait a trickling server would impose.
   */
  private static byte[] capped(HttpGet get, InputStream in, int maxBytes, long deadline)
      throws IOException {
    try (InputStream stream = in) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      int total = 0;
      int read;
      while ((read = stream.read(buffer)) != -1) {
        if (System.nanoTime() > deadline) {
          // A body that arrives a byte at a time never trips the socket timeout: the deadline does.
          get.abort();
          throw new InterruptedIOException("the research deadline passed while the body was read");
        }
        total += read;
        if (total > maxBytes) {
          get.abort();
          throw new TooLargeException();
        }
        out.write(buffer, 0, read);
      }
      return out.toByteArray();
    }
  }
}
