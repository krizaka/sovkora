package com.krizaka.sovkora.assets.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.assets.domain.port.DurableAssetStore;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The Bunny adapter against an in-process fake of the storage API (PUT / GET / DELETE on a path).
 */
class BunnyAssetStoreTest extends DurableAssetStoreContract {

  private static final String ZONE = "sovkora-test";
  private static final String KEY = "secret-zone-password";

  private HttpServer server;
  private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
  private final List<String> keysSeen = new ArrayList<>();

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          keysSeen.add(String.valueOf(exchange.getRequestHeaders().getFirst("AccessKey")));
          String path = exchange.getRequestURI().getPath();
          String prefix = "/" + ZONE + "/";
          String key = path.substring(prefix.length());
          int status;
          byte[] body = new byte[0];
          if (!KEY.equals(exchange.getRequestHeaders().getFirst("AccessKey"))) {
            status = 401;
          } else {
            switch (exchange.getRequestMethod()) {
              case "PUT" -> {
                objects.put(key, exchange.getRequestBody().readAllBytes());
                status = 201;
              }
              case "GET" -> {
                byte[] found = key.endsWith("/") || key.isEmpty() ? list(key) : objects.get(key);
                status = found == null ? 404 : 200;
                body = found == null ? body : found;
              }
              case "DELETE" -> {
                if (key.endsWith("/")) {
                  new TreeMap<>(objects)
                      .keySet().stream().filter(k -> k.startsWith(key)).forEach(objects::remove);
                  status = 200;
                } else {
                  status = objects.remove(key) == null ? 404 : 200;
                }
              }
              default -> status = 405;
            }
          }
          exchange.sendResponseHeaders(status, status == 200 && body.length > 0 ? body.length : -1);
          if (status == 200 && body.length > 0) {
            exchange.getResponseBody().write(body);
          }
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  /** Bunny's directory listing: a JSON array of the direct children, 404 when there are none. */
  private byte[] list(String dir) {
    java.util.Set<String> seen = new java.util.TreeSet<>();
    StringBuilder json = new StringBuilder("[");
    objects.forEach(
        (k, v) -> {
          if (!k.startsWith(dir)) {
            return;
          }
          String rest = k.substring(dir.length());
          int slash = rest.indexOf('/');
          String name = slash < 0 ? rest : rest.substring(0, slash);
          if (!seen.add(name)) {
            return;
          }
          if (json.length() > 1) {
            json.append(',');
          }
          json.append("{\"ObjectName\":\"")
              .append(name)
              .append("\",\"IsDirectory\":")
              .append(slash >= 0)
              .append(",\"LastChanged\":\"2026-10-17T21:09:16.911\",\"Path\":\"/")
              .append(ZONE)
              .append("/")
              .append(dir)
              .append("\"}");
        });
    return seen.isEmpty() ? null : json.append(']').toString().getBytes();
  }

  @Override
  DurableAssetStore store() {
    return new BunnyAssetStore("http://127.0.0.1:" + server.getAddress().getPort(), ZONE, KEY);
  }

  @Test
  void aWrongAccessKeyIsRefusedByName_withoutLeakingTheKey() throws IOException {
    DurableAssetStore wrong =
        new BunnyAssetStore("http://127.0.0.1:" + server.getAddress().getPort(), ZONE, "nope");
    assertThatThrownBy(() -> wrong.put("t/x/u/1/jobs/j/f", file("a", "x")))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("HTTP 401")
        .hasMessageNotContaining("nope");
  }

  @Test
  void everyRequestCarriesTheAccessKey() throws IOException {
    store().put("t/x/u/1/jobs/j/f", file("a", "x"));
    assertThat(keysSeen).containsOnly(KEY);
  }
}
