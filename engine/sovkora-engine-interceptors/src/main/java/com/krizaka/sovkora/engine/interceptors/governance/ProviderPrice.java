package com.krizaka.sovkora.engine.interceptors.governance;

import com.krizaka.sovkora.engine.core.domain.model.chat.TokenUsage;
import java.math.BigDecimal;

/**
 * What a provider charges for one model, the row the spend is derived from.
 *
 * @param modelKey the provider key the engine routes a turn by
 * @param capability the capability the model serves ({@code CHAT})
 * @param inputUsdPerMillionTokens USD per million prompt tokens
 * @param outputUsdPerMillionTokens USD per million generated tokens
 */
public record ProviderPrice(
    String modelKey,
    String capability,
    BigDecimal inputUsdPerMillionTokens,
    BigDecimal outputUsdPerMillionTokens) {

  private static final BigDecimal MILLION = new BigDecimal("1000000");

  public ProviderPrice {
    if (modelKey == null || modelKey.isBlank() || capability == null || capability.isBlank()) {
      throw new IllegalArgumentException("ProviderPrice requires a model key and a capability");
    }
    if (inputUsdPerMillionTokens == null
        || outputUsdPerMillionTokens == null
        || inputUsdPerMillionTokens.signum() < 0
        || outputUsdPerMillionTokens.signum() < 0) {
      throw new IllegalArgumentException("prices must be present and >= 0");
    }
  }

  /**
   * The cost of one measured inference.
   *
   * @param usage what the provider reported
   * @return the cost in USD; zero when nothing was reported
   */
  public BigDecimal costOf(TokenUsage usage) {
    if (!usage.reported()) {
      return BigDecimal.ZERO;
    }
    return BigDecimal.valueOf(usage.promptTokens())
        .multiply(inputUsdPerMillionTokens)
        .add(BigDecimal.valueOf(usage.completionTokens()).multiply(outputUsdPerMillionTokens))
        .divide(MILLION, 8, java.math.RoundingMode.HALF_UP);
  }
}
