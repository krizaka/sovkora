package com.krizaka.sovkora.core.infrastructure.checks;

import com.krizaka.sovkora.core.domain.ports.outbound.CheckHttp;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;

/** The JDK HTTP client as the providers' door. It follows no redirect and never logs a body. */
public final class JdkCheckHttp implements CheckHttp {

  private final HttpClient client =
      HttpClient.newBuilder()
          .followRedirects(HttpClient.Redirect.NEVER)
          .connectTimeout(Duration.ofSeconds(5))
          .build();

  @Override
  public Reply post(String url, Map<String, String> headers, String body, Duration timeout) {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(url))
            .timeout(timeout)
            .POST(HttpRequest.BodyPublishers.ofString(body));
    headers.forEach(request::header);
    try {
      HttpResponse<String> reply =
          client.send(request.build(), HttpResponse.BodyHandlers.ofString());
      return new Reply(reply.statusCode(), reply.body());
    } catch (HttpTimeoutException slow) {
      throw new TimeoutException();
    } catch (IOException failure) {
      // The cause may name the URL or the payload: nothing of it travels.
      throw new IllegalStateException("the provider could not be reached");
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while waiting for the provider");
    }
  }
}
