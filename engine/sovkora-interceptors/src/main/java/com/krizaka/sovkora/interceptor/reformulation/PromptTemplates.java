package com.krizaka.sovkora.interceptor.reformulation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.io.Resource;

/** Reads the interceptors' prompt files and fills their {@code {name}} placeholders. */
final class PromptTemplates {

  private PromptTemplates() {}

  /** The text of a prompt file. */
  static String read(Resource resource) {
    try (var in = resource.getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read the prompt " + resource, e);
    }
  }

  /** The text of a prompt file with each {@code {name}} replaced by its value. */
  static String render(Resource resource, Map<String, String> variables) {
    String text = read(resource);
    for (var variable : variables.entrySet()) {
      text = text.replace("{" + variable.getKey() + "}", variable.getValue());
    }
    return text;
  }
}
