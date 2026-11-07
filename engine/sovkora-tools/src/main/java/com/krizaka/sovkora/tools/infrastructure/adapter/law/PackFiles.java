package com.krizaka.sovkora.tools.infrastructure.adapter.law;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the files of the rulesets repository on its base branch, through the GitHub contents API.
 * Shared by the pack reader and the promotion publisher.
 */
final class PackFiles {

  static final JsonMapper MAPPER = JsonMapper.builder().build();

  private final RestClient github;
  private final String repo;
  private final String baseBranch;

  PackFiles(RestClient github, String owner, String repository, String baseBranch) {
    this.github = Objects.requireNonNull(github, "github");
    this.repo = "/repos/" + requireText(owner, "owner") + "/" + requireText(repository, "repo");
    this.baseBranch = requireText(baseBranch, "baseBranch");
  }

  String repo() {
    return repo;
  }

  String baseBranch() {
    return baseBranch;
  }

  RestClient github() {
    return github;
  }

  /** A file's JSON content, or empty when the base branch has none at that path. */
  Optional<JsonNode> json(String path) {
    return content(path).map(MAPPER::readTree);
  }

  /** A file's text, or empty when the base branch has none at that path. */
  Optional<String> content(String path) {
    Map<?, ?> file = get(path, Map.class);
    if (file == null) {
      return Optional.empty();
    }
    String encoded = String.valueOf(file.get("content")).replaceAll("\\s", "");
    return Optional.of(new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8));
  }

  /** A directory's listing, or empty when the base branch has none at that path. */
  JsonNode listing(String path) {
    JsonNode listing = get(path, JsonNode.class);
    return listing == null ? MAPPER.createArrayNode() : listing;
  }

  private <T> T get(String path, Class<T> type) {
    try {
      return github
          .get()
          .uri(repo + "/contents/" + path + "?ref={ref}", baseBranch)
          .retrieve()
          .body(type);
    } catch (HttpClientErrorException.NotFound absent) {
      return null;
    }
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value.trim();
  }
}
