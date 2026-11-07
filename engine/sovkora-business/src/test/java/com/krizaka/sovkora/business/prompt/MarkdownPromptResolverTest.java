package com.krizaka.sovkora.business.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MarkdownPromptResolver}: cache semantics, graceful degradation on missing
 * or unreadable files, eviction and constructor invariants. The source is a plain lambda — the
 * module has no Spring.
 */
class MarkdownPromptResolverTest {

  private final List<String> reads = new ArrayList<>();
  private final Map<String, String> files =
      Map.of(
          "classpath:prompts/welcome_agent.md", "# Welcome Agent\nYou are Sovkora.",
          "classpath:prompts/alpha.md", "A",
          "classpath:prompts/beta.md", "B");

  private MarkdownPromptResolver resolver;

  @BeforeEach
  void setUp() {
    resolver =
        new MarkdownPromptResolver(
            location -> {
              reads.add(location);
              if (location.endsWith("/broken.md")) {
                throw new UncheckedIOException(new IOException("Simulated read failure"));
              }
              return Optional.ofNullable(files.get(location));
            });
  }

  @Test
  @DisplayName("resolve() returns prompt content for an existing resource")
  void resolve_existingPrompt_returnsContent() {
    assertThat(resolver.resolve("welcome_agent")).hasValue("# Welcome Agent\nYou are Sovkora.");
    assertThat(resolver.cacheSize()).isEqualTo(1);
  }

  @Test
  @DisplayName("resolve() returns empty for a non-existent prompt file")
  void resolve_missingPrompt_returnsEmpty() {
    assertThat(resolver.resolve("nonexistent")).isEmpty();
  }

  @Test
  @DisplayName("resolve() returns empty when the read fails")
  void resolve_ioFailure_returnsEmpty() {
    assertThat(resolver.resolve("broken")).isEmpty();
  }

  @Test
  @DisplayName("resolve() serves from cache on the second call")
  void resolve_cachedHit_noSecondIO() {
    resolver.resolve("alpha");
    resolver.resolve("alpha");

    assertThat(reads).containsExactly("classpath:prompts/alpha.md");
  }

  @Test
  @DisplayName("evict() removes a single entry and forces a reload on next access")
  void evict_removesEntry_forcesReload() {
    resolver.resolve("alpha");
    resolver.evict("alpha");
    assertThat(resolver.cacheSize()).isZero();

    resolver.resolve("alpha");
    assertThat(reads).hasSize(2);
  }

  @Test
  @DisplayName("evictAll() clears the entire cache")
  void evictAll_clearsAllEntries() {
    resolver.resolve("alpha");
    resolver.resolve("beta");
    assertThat(resolver.cacheSize()).isEqualTo(2);

    resolver.evictAll();
    assertThat(resolver.cacheSize()).isZero();
  }

  @Test
  @DisplayName("resolve() returns empty for a null or blank prompt name")
  void resolve_nullOrBlankName_returnsEmpty() {
    assertThat(resolver.resolve(null)).isEmpty();
    assertThat(resolver.resolve("   ")).isEmpty();
    assertThat(reads).isEmpty();
  }

  @Test
  @DisplayName("constructor rejects a null source")
  void constructor_nullSource_throws() {
    assertThatThrownBy(() -> new MarkdownPromptResolver(null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
