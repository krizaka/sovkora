package com.krizaka.sovkora.core.domain.model.chat;

import java.io.Serial;

/**
 * The model kept asking for tools past the bound of its feature: the turn is stopped, with this
 * reason (never a silent cut of the loop). The streamed turn ends on it as an error event.
 */
public class ToolRoundsExceededException extends IllegalStateException {

  @Serial private static final long serialVersionUID = 1L;

  private final String featureKey;
  private final int maxRounds;

  /**
   * @param featureKey the turn's feature, or {@code null} when it had none
   * @param maxRounds the bound that was reached
   */
  public ToolRoundsExceededException(String featureKey, int maxRounds) {
    super(
        "The model kept asking for tools after "
            + maxRounds
            + " tool rounds; the turn is stopped"
            + (featureKey == null ? "" : " (feature " + featureKey + ")"));
    this.featureKey = featureKey;
    this.maxRounds = maxRounds;
  }

  /** The turn's feature, or {@code null}. */
  public String featureKey() {
    return featureKey;
  }

  /** The bound that was reached. */
  public int maxRounds() {
    return maxRounds;
  }
}
