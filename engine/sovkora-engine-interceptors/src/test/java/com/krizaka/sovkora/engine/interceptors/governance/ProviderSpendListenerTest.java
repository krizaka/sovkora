package com.krizaka.sovkora.engine.interceptors.governance;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.engine.core.domain.event.ChatCompletedEvent;
import com.krizaka.sovkora.engine.core.domain.model.Context;
import com.krizaka.sovkora.engine.core.domain.model.chat.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

@DisplayName("ProviderSpendListener — the one writer of provider spend")
class ProviderSpendListenerTest {

  private static final SpendScope VERIKORA = new SpendScope("dev", "verikora");
  private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);
  private static final Clock IN_NOVEMBER =
      Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneOffset.UTC);

  private final InMemorySpendLedger ledger = new InMemorySpendLedger();
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final JobSpendTally jobs = new JobSpendTally();
  private ProviderSpendListener listener;

  @BeforeEach
  void wire() {
    // 10 USD a month; a model at 2 USD per million tokens in and 4 out.
    ledger.caps.put(
        VERIKORA, new SpendCap(VERIKORA, new BigDecimal("10"), SpendCapAction.REFUSE, null));
    ledger.prices.put(
        "big", new ProviderPrice("big", "CHAT", new BigDecimal("2"), new BigDecimal("4")));
    listener =
        new ProviderSpendListener(
            ledger, new SpendCapProperties("dev"), new SpendMetrics(registry), jobs, IN_NOVEMBER);
  }

  private static ChatCompletedEvent turn(String model, TokenUsage usage) {
    InternalChatRequest request =
        new InternalChatRequest(
            "hello",
            List.of(),
            null,
            new Context(
                "actor", "conv", Map.of(SpendScope.PRODUCT_DECLARATION, "verikora"), Set.of()));
    return new ChatCompletedEvent(
        request, new InternalChatResponse("hi", "c", usage, Map.of("provider", model)));
  }

  private static ChatCompletedEvent turnOf(String conversation, Map<String, Object> declarations) {
    InternalChatRequest request =
        new InternalChatRequest(
            "hello", List.of(), null, new Context("actor", conversation, declarations, Set.of()));
    return new ChatCompletedEvent(
        request,
        new InternalChatResponse(
            "hi",
            "c",
            TokenUsage.reported(1_000_000, 500_000, 1_500_000),
            Map.of("provider", "big")));
  }

  @Test
  @DisplayName("[3368] the cost of an open job is the sum of its calls, tokens × price")
  void openJobCollectsItsCost() {
    jobs.open("job-1");
    listener.onChatCompleted(turnOf("job-1", Map.of(SpendScope.PRODUCT_DECLARATION, "verikora")));
    listener.onChatCompleted(turnOf("job-1", Map.of(SpendScope.PRODUCT_DECLARATION, "verikora")));

    // 2 × (1,000,000 in × 2 + 500,000 out × 4) = 8 USD
    assertThat(jobs.close("job-1")).isEqualByComparingTo("8");
    assertThat(jobs.close("job-1")).isZero();
  }

  @Test
  @DisplayName("[3368] a turn of no open job leaves nothing behind")
  void unopenedJobCollectsNothing() {
    listener.onChatCompleted(turnOf("chat-9", Map.of(SpendScope.PRODUCT_DECLARATION, "verikora")));

    assertThat(jobs.close("chat-9")).isZero();
  }

  @Test
  @DisplayName("[3368] a turn that declares no product is counted as unattributed, not elsewhere")
  void noProductIsUnattributed() {
    listener.onChatCompleted(turnOf("conv", Map.of()));

    assertThat(registry.get("ai.cost").tags("product", SpendScope.UNATTRIBUTED).counter().count())
        .isEqualTo(4.0);
    assertThat(ledger.spentIn(VERIKORA, NOVEMBER)).isZero();
  }

  @Test
  @DisplayName("a priced turn adds tokens × price to the scope's month and to ai_cost_total")
  void addsTheCost() {
    // 1,000,000 in × 2 + 500,000 out × 4 = 4 USD
    listener.onChatCompleted(turn("big", TokenUsage.reported(1_000_000, 500_000, 1_500_000)));

    assertThat(ledger.spentIn(VERIKORA, NOVEMBER)).isEqualByComparingTo("4");
    assertThat(
            registry
                .get("ai.cost")
                .tags("product", "verikora", "capability", "CHAT", "model", "big")
                .counter()
                .count())
        .isEqualTo(4.0);
  }

  @Test
  @DisplayName("the 50 % alert fires once however many turns follow it")
  void alertFiresOnce() {
    listener.onChatCompleted(
        turn("big", TokenUsage.reported(1_000_000, 750_000, 1_750_000))); // 5 USD
    listener.onChatCompleted(turn("big", TokenUsage.reported(100_000, 0, 100_000))); // 5.2

    assertThat(registry.get("spend.cap.alert").tag("threshold", "50").counter().count())
        .isEqualTo(1.0);
    assertThat(registry.find("spend.cap.alert").tag("threshold", "80").counter()).isNull();
  }

  @Test
  @DisplayName("crossing 100 % fires the 50, 80 and 100 % alerts, each once")
  void crossingTheCapFiresEveryThreshold() {
    listener.onChatCompleted(turn("big", TokenUsage.reported(5_000_000, 0, 5_000_000))); // 10 USD

    for (String threshold : List.of("50", "80", "100")) {
      assertThat(registry.get("spend.cap.alert").tag("threshold", threshold).counter().count())
          .isEqualTo(1.0);
    }
    assertThat(registry.get("spend.cap.ratio").gauge().value()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("a model with no price row adds nothing and is counted as unpriced")
  void unpricedModelIsVisible() {
    listener.onChatCompleted(turn("mystery", TokenUsage.reported(1000, 1000, 2000)));

    assertThat(ledger.spentIn(VERIKORA, NOVEMBER)).isZero();
    assertThat(registry.get("ai.cost.unpriced").tag("model", "mystery").counter().count())
        .isEqualTo(1.0);
  }

  @Test
  @DisplayName("a turn whose provider reported no usage adds nothing")
  void unreportedUsageAddsNothing() {
    listener.onChatCompleted(turn("big", TokenUsage.none()));

    assertThat(ledger.spentIn(VERIKORA, NOVEMBER)).isZero();
  }

  @Test
  @DisplayName("a failing ledger never loses the turn: it is counted as a failed record")
  void failureIsCountedNotThrown() {
    ProviderSpendListener failing =
        new ProviderSpendListener(
            new InMemorySpendLedger() {
              {
                prices.putAll(ledger.prices);
              }

              @Override
              public BigDecimal add(SpendScope scope, YearMonth month, BigDecimal usd) {
                throw new IllegalStateException("down");
              }
            },
            new SpendCapProperties("dev"),
            new SpendMetrics(registry),
            jobs,
            IN_NOVEMBER);
    failing.onChatCompleted(turn("big", TokenUsage.reported(1_000_000, 0, 1_000_000)));

    assertThat(registry.get("spend.record.failed").counter().count()).isEqualTo(1.0);
  }
}
