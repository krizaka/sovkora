package com.krizaka.sovkora.assets.infrastructure.storage;

import com.krizaka.sovkora.assets.domain.port.DurableAssetStore;
import com.krizaka.sovkora.assets.domain.port.StoredObject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps the second copy in a Bunny Storage zone (private: no pull zone is attached, so the only way
 * in is this adapter's {@code AccessKey} and the only way out to a user is the authenticated asset
 * route).
 *
 * <p>Plain {@code java.net.http}: the storage API is three verbs on a path, and a client library
 * would be the largest thing in this module. The key is never in a log line or an exception.
 */
public final class BunnyAssetStore implements DurableAssetStore {

  private final HttpClient http;
  private final String base;
  private final String accessKey;

  /**
   * @param endpoint the storage host, e.g. {@code storage.bunnycdn.com}, or a full base URL (tests)
   * @param zone the storage zone name
   * @param accessKey the zone's storage password
   */
  public BunnyAssetStore(String endpoint, String zone, String accessKey) {
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    String host = endpoint.contains("://") ? endpoint : "https://" + endpoint;
    this.base = (host.endsWith("/") ? host : host + "/") + zone + "/";
    this.accessKey = accessKey;
  }

  @Override
  public boolean enabled() {
    return true;
  }

  @Override
  public void put(String key, Path file) throws IOException {
    HttpRequest request =
        request(key)
            .header("Content-Type", "application/octet-stream")
            .PUT(HttpRequest.BodyPublishers.ofFile(file))
            .build();
    HttpResponse<Void> response = send(request, HttpResponse.BodyHandlers.discarding());
    if (response.statusCode() / 100 != 2) {
      throw new IOException("durable store refused PUT: HTTP " + response.statusCode());
    }
  }

  @Override
  public boolean fetch(String key, Path target) throws IOException {
    Path parent = target.toAbsolutePath().getParent();
    Files.createDirectories(parent);
    Path temp = Files.createTempFile(parent, ".orz-", ".tmp");
    try {
      HttpResponse<Path> response =
          send(request(key).GET().build(), HttpResponse.BodyHandlers.ofFile(temp));
      if (response.statusCode() == 404) {
        return false;
      }
      if (response.statusCode() / 100 != 2) {
        throw new IOException("durable store refused GET: HTTP " + response.statusCode());
      }
      Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
      return true;
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  @Override
  public void delete(String key) throws IOException {
    HttpResponse<Void> response =
        send(request(key).DELETE().build(), HttpResponse.BodyHandlers.discarding());
    if (response.statusCode() / 100 != 2 && response.statusCode() != 404) {
      throw new IOException("durable store refused DELETE: HTTP " + response.statusCode());
    }
  }

  @Override
  public List<StoredObject> list(String prefix) throws IOException {
    HttpResponse<String> response =
        send(
            request(prefix).header("Accept", "application/json").GET().build(),
            HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() == 404) {
      return List.of();
    }
    if (response.statusCode() / 100 != 2) {
      throw new IOException("durable store refused LIST: HTTP " + response.statusCode());
    }
    List<StoredObject> children = new ArrayList<>();
    Matcher entry = ENTRY.matcher(response.body());
    while (entry.find()) {
      String object = entry.group();
      Matcher name = OBJECT_NAME.matcher(object);
      if (!name.find()) {
        continue;
      }
      boolean directory = IS_DIRECTORY.matcher(object).find();
      Matcher changed = LAST_CHANGED.matcher(object);
      children.add(
          new StoredObject(
              prefix + name.group(1) + (directory ? "/" : ""),
              changed.find() ? parse(changed.group(1)) : Instant.now()));
    }
    return children;
  }

  // The listing is a flat JSON array of flat objects: three fields are read, no JSON library.
  private static final Pattern ENTRY = Pattern.compile("\\{[^{}]*}");
  private static final Pattern OBJECT_NAME = Pattern.compile("\"ObjectName\"\\s*:\\s*\"([^\"]*)\"");
  private static final Pattern IS_DIRECTORY = Pattern.compile("\"IsDirectory\"\\s*:\\s*true");
  private static final Pattern LAST_CHANGED =
      Pattern.compile("\"LastChanged\"\\s*:\\s*\"([^\"]*)\"");

  /**
   * Bunny reports UTC without an offset; an unreadable date counts as now, so nothing expires
   * early.
   */
  private static Instant parse(String value) {
    try {
      return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
    } catch (DateTimeParseException unreadable) {
      return Instant.now();
    }
  }

  private HttpRequest.Builder request(String key) {
    return HttpRequest.newBuilder(URI.create(base + key))
        .timeout(Duration.ofMinutes(5))
        .header("AccessKey", accessKey);
  }

  private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
      throws IOException {
    try {
      return http.send(request, handler);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IOException("interrupted talking to the durable store", interrupted);
    }
  }
}
