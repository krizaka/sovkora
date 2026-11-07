package com.krizaka.sovkora.business.prompt;

import java.io.UncheckedIOException;
import java.util.Optional;

/**
 * Outbound port of the prompt resolver: reads the text behind a resource location.
 *
 * <p>The business module is pure Java; the adapter decides where the text lives (the classpath
 * today, through Spring's resource loader).
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface PromptTextSource {

  /**
   * Reads the UTF-8 text at the location.
   *
   * @param location a resource location such as {@code classpath:prompts/welcome.md}
   * @return the text, or empty when nothing exists there
   * @throws UncheckedIOException when the resource exists but cannot be read
   */
  Optional<String> read(String location);
}
