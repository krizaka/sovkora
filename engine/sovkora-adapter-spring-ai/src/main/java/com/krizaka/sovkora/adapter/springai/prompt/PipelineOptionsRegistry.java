package com.krizaka.sovkora.adapter.springai.prompt;

import java.util.List;
import java.util.Objects;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * Spring-managed registry that delegates to registered {@link PipelineOptionsStrategy} instances
 * for building provider-specific options.
 */
public class PipelineOptionsRegistry {

  private final List<PipelineOptionsStrategy> strategies;

  /**
   * Constructs the registry.
   *
   * @param strategies The list of autowired PipelineOptionsStrategy beans.
   */
  public PipelineOptionsRegistry(List<PipelineOptionsStrategy> strategies) {
    this.strategies =
        List.copyOf(Objects.requireNonNull(strategies, "Strategies list must not be null"));
  }

  /**
   * Resolves the matching strategy and builds options.
   *
   * @param provider The provider name.
   * @param modelName The model name.
   * @param temp The temperature.
   * @return Provider-specific ChatOptions.
   */
  public ChatOptions build(String provider, String modelName, Double temp) {
    return strategies.stream()
        .filter(s -> s.supports(provider))
        .findFirst()
        .map(s -> s.buildOptions(modelName, temp))
        .orElseThrow(() -> new IllegalArgumentException("Unsupported AI provider: " + provider));
  }
}
