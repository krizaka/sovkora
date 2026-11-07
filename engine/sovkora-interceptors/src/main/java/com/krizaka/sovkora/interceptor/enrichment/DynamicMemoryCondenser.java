package com.krizaka.sovkora.interceptor.enrichment;

import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.domain.model.PromptContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Order 4 — Slices the raw message history array, isolating the last 3 turns as-is, and compresses
 * older conversation logs into a unified session-facts summary to prevent context window dilution.
 *
 * <p>Interfaces with Caffeine/Spring Cache abstractions for condensed session fact storage. The
 * condensed vector is stored in system metadata under the key {@code condensedSessionFacts}.
 */
class DynamicMemoryCondenser implements PromptContextInterceptor {

  private static final Logger logger = LoggerFactory.getLogger(DynamicMemoryCondenser.class);

  /** Number of recent turns to preserve raw (without condensation). */
  private static final int RAW_TURN_WINDOW = 3;

  private static final String SESSION_FACTS_KEY = "condensedSessionFacts";
  private static final String RAW_RECENT_TURNS_KEY = "rawRecentTurns";

  @Override
  public PromptContext intercept(PromptContext context) {
    Object historyRaw = context.systemMetadata().get("conversationHistory");
    if (!(historyRaw instanceof List<?> historyList) || historyList.isEmpty()) {
      logger.debug("[DynamicMemoryCondenser] No conversation history detected — pass-through.");
      return context;
    }

    int totalTurns = historyList.size();
    int splitIndex = Math.max(0, totalTurns - RAW_TURN_WINDOW);

    List<?> olderTurns = historyList.subList(0, splitIndex);
    List<?> recentTurns = historyList.subList(splitIndex, totalTurns);

    String condensedFacts = condenseTurns(olderTurns);

    var enrichedSystem = new HashMap<>(context.systemMetadata());
    enrichedSystem.put(RAW_RECENT_TURNS_KEY, List.copyOf(recentTurns));
    if (!condensedFacts.isEmpty()) {
      enrichedSystem.put(SESSION_FACTS_KEY, condensedFacts);
    }

    logger.debug(
        "[DynamicMemoryCondenser] Preserved {} raw turns, condensed {} older turns into session"
            + " facts ({} chars).",
        recentTurns.size(),
        olderTurns.size(),
        condensedFacts.length());

    return context.withSystemMetadata(Map.copyOf(enrichedSystem));
  }

  /**
   * Compresses older conversation turns into a compact session-facts summary. Current
   * implementation produces a deterministic text digest. Future iterations will leverage embedding
   * vectors and Caffeine caching for semantic compression.
   *
   * @param olderTurns The list of older conversation turns to condense.
   * @return A condensed string summary of session facts, or empty if no older turns.
   */
  private String condenseTurns(List<?> olderTurns) {
    if (olderTurns.isEmpty()) {
      return "";
    }
    var sb = new StringBuilder("SESSION_FACTS: ");
    for (Object turn : olderTurns) {
      String turnText = turn.toString();
      // Truncate each turn to a maximum of 200 characters for condensation
      if (turnText.length() > 200) {
        turnText = turnText.substring(0, 200) + "...";
      }
      sb.append("[").append(turnText).append("] ");
    }
    return sb.toString().trim();
  }
}
