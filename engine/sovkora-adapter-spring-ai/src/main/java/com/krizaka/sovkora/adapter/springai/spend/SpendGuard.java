package com.krizaka.sovkora.adapter.springai.spend;

import com.krizaka.sovkora.core.domain.model.ModelEndpoint;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Global daily budget circuit breaker for <b>paid</b> providers (hosted chat, embeddings, remote
 * video). Every call that would reach a paid provider first {@linkplain #authorize reserves} its
 * estimated cost; once the day's budget is spent the call is refused with a {@link
 * SpendBudgetExceededException} ({@code 429}) and an alert is logged once per day. Local providers
 * (Ollama, LocalAI, a loopback or private address) cost nothing and are never refused.
 *
 * <p>The cost of a call is an <i>estimate</i> (flat per kind, configurable): a guard that waited
 * for the provider's invoice would trip after the money is gone. The budget resets at 00:00 UTC and
 * is kept in memory — a restart starts the day again, which is why the budget is a ceiling on a
 * running instance, not an accounting. {@code orazaka.spend.daily-budget-usd} (env {@code
 * AI_SPEND_DAILY_BUDGET_USD}, default 5) is the ceiling; a negative value switches the guard off
 * (production, where billing is the control).
 */
public class SpendGuard {

  private static final Logger logger = LoggerFactory.getLogger(SpendGuard.class);

  /** What a call is, for its estimated cost. */
  public enum Kind {
    CHAT,
    EMBEDDING,
    VIDEO
  }

  private static final Set<String> LOCAL_PROVIDERS =
      Set.of("ollama", "localai", "localai-image", "comfyui", "llama.cpp", "sd-server", "local");
  private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "host.docker.internal");

  private final double budgetUsd;
  private final double chatUsd;
  private final double embeddingUsd;
  private final double videoUsd;
  private final Clock clock;

  private LocalDate day;
  private double spentUsd;
  private boolean alerted;

  public SpendGuard(double budgetUsd, double chatUsd, double embeddingUsd, double videoUsd) {
    this(budgetUsd, chatUsd, embeddingUsd, videoUsd, Clock.systemUTC());
  }

  public SpendGuard(
      double budgetUsd, double chatUsd, double embeddingUsd, double videoUsd, Clock clock) {
    this.budgetUsd = budgetUsd;
    this.chatUsd = chatUsd;
    this.embeddingUsd = embeddingUsd;
    this.videoUsd = videoUsd;
    this.clock = clock;
  }

  /** A guard that never refuses (tests, deployments that meter elsewhere). */
  public static SpendGuard unlimited() {
    return new SpendGuard(-1, 0, 0, 0, Clock.systemUTC());
  }

  /** Reserves the estimated cost of one call; refuses it when the day's budget would be passed. */
  public void authorize(Kind kind, ModelEndpoint endpoint) {
    if (budgetUsd < 0 || isLocal(endpoint)) {
      return;
    }
    double cost = costOf(kind);
    synchronized (this) {
      LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
      if (!today.equals(day)) {
        day = today;
        spentUsd = 0;
        alerted = false;
      }
      if (spentUsd + cost > budgetUsd) {
        if (!alerted) {
          alerted = true;
          logger.error(
              "SPEND ALERT: daily budget of {} USD for paid providers reached ({} spent);"
                  + " refusing paid calls until 00:00 UTC (first refused: {} on '{}')",
              String.format(Locale.ROOT, "%.2f", budgetUsd),
              String.format(Locale.ROOT, "%.2f", spentUsd),
              kind,
              endpoint.providerName());
        }
        throw new SpendBudgetExceededException(endpoint.providerName(), budgetUsd);
      }
      spentUsd += cost;
    }
  }

  /** Estimated spend of the current UTC day, for the operator's view. */
  public synchronized double spentTodayUsd() {
    return LocalDate.now(clock.withZone(ZoneOffset.UTC)).equals(day) ? spentUsd : 0;
  }

  private double costOf(Kind kind) {
    return switch (kind) {
      case CHAT -> chatUsd;
      case EMBEDDING -> embeddingUsd;
      case VIDEO -> videoUsd;
    };
  }

  /** A provider on this machine or its private network costs nothing per call. */
  static boolean isLocal(ModelEndpoint endpoint) {
    if (LOCAL_PROVIDERS.contains(endpoint.providerName().toLowerCase(Locale.ROOT))) {
      return true;
    }
    try {
      String host = URI.create(endpoint.baseUrl()).getHost();
      if (host == null) {
        return false;
      }
      String h = host.toLowerCase(Locale.ROOT);
      return LOCAL_HOSTS.contains(h) || h.endsWith(".local") || isPrivateAddress(h);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  /** An IP literal (never a name: no DNS lookup) that is loopback, private or link-local. */
  private static boolean isPrivateAddress(String host) {
    String literal =
        host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
    if (!literal.matches("[0-9.]+") && !literal.contains(":")) {
      return false;
    }
    try {
      java.net.InetAddress address = java.net.InetAddress.getByName(literal);
      return address.isLoopbackAddress()
          || address.isSiteLocalAddress()
          || address.isLinkLocalAddress()
          || address.isAnyLocalAddress();
    } catch (java.net.UnknownHostException e) {
      return false;
    }
  }
}
