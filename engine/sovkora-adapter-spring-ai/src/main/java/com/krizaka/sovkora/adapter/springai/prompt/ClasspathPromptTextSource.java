package com.krizaka.sovkora.adapter.springai.prompt;

import com.krizaka.sovkora.business.prompt.PromptTextSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * Reads prompt text through Spring's {@link ResourceLoader} (the {@link PromptTextSource} port).
 */
public final class ClasspathPromptTextSource implements PromptTextSource {

  private final ResourceLoader resourceLoader;

  public ClasspathPromptTextSource(ResourceLoader resourceLoader) {
    if (resourceLoader == null) {
      throw new IllegalArgumentException("ResourceLoader must not be null");
    }
    this.resourceLoader = resourceLoader;
  }

  @Override
  public Optional<String> read(String location) {
    Resource resource = resourceLoader.getResource(location);
    if (!resource.exists()) {
      return Optional.empty();
    }
    try (InputStream is = resource.getInputStream()) {
      return Optional.of(new String(is.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
