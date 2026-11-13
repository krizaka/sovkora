package com.krizaka.sovkora.edge.infrastructure.config;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/**
 * The proxy's JDK client with a response timeout on every request (krizaka-internal#2132).
 *
 * <p>The JDK's request timeout bounds the wait for the response headers and stops there: a body
 * already streaming (an SSE stream of chat tokens or job progress) is never cut. Spring's {@code
 * JdkClientHttpRequestFactory.setReadTimeout} cannot be used for this, because it also closes the
 * body when the time is up. The factory gives no other way to set the request's own timeout, so
 * this client sets it on each request it sends and delegates everything else.
 */
final class ResponseTimeoutHttpClient extends HttpClient {

  private final HttpClient delegate;
  private final Duration responseTimeout;

  ResponseTimeoutHttpClient(HttpClient delegate, Duration responseTimeout) {
    this.delegate = delegate;
    this.responseTimeout = responseTimeout;
  }

  private HttpRequest withTimeout(HttpRequest request) {
    return HttpRequest.newBuilder(request, (name, value) -> true).timeout(responseTimeout).build();
  }

  @Override
  public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
      throws IOException, InterruptedException {
    return delegate.send(withTimeout(request), handler);
  }

  @Override
  public <T> CompletableFuture<HttpResponse<T>> sendAsync(
      HttpRequest request, HttpResponse.BodyHandler<T> handler) {
    return delegate.sendAsync(withTimeout(request), handler);
  }

  @Override
  public <T> CompletableFuture<HttpResponse<T>> sendAsync(
      HttpRequest request,
      HttpResponse.BodyHandler<T> handler,
      HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
    return delegate.sendAsync(withTimeout(request), handler, pushPromiseHandler);
  }

  @Override
  public Optional<CookieHandler> cookieHandler() {
    return delegate.cookieHandler();
  }

  @Override
  public Optional<Duration> connectTimeout() {
    return delegate.connectTimeout();
  }

  @Override
  public Redirect followRedirects() {
    return delegate.followRedirects();
  }

  @Override
  public Optional<ProxySelector> proxy() {
    return delegate.proxy();
  }

  @Override
  public SSLContext sslContext() {
    return delegate.sslContext();
  }

  @Override
  public SSLParameters sslParameters() {
    return delegate.sslParameters();
  }

  @Override
  public Optional<Authenticator> authenticator() {
    return delegate.authenticator();
  }

  @Override
  public Version version() {
    return delegate.version();
  }

  @Override
  public Optional<Executor> executor() {
    return delegate.executor();
  }

  @Override
  public void shutdown() {
    delegate.shutdown();
  }

  @Override
  public void shutdownNow() {
    delegate.shutdownNow();
  }

  @Override
  public boolean awaitTermination(Duration duration) throws InterruptedException {
    return delegate.awaitTermination(duration);
  }

  @Override
  public boolean isTerminated() {
    return delegate.isTerminated();
  }

  @Override
  public void close() {
    delegate.close();
  }
}
