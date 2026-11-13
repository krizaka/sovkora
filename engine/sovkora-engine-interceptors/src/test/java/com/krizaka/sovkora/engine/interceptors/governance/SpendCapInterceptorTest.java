package com.krizaka.sovkora.engine.interceptors.governance;

import static org.assertj.core.api.Assertions.*;

import com.krizaka.sovkora.engine.core.application.pipeline.PipelineShortCircuitException;
import com.krizaka.sovkora.engine.core.domain.model.PromptContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.*;

@DisplayName("SpendCapInterceptor — the monthly provider-spend cap")
class SpendCapInterceptorTest {

  private static final SpendScope VERIKORA = new SpendScope("dev", "verikora");
  private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);
  private static final Clock IN_NOVEMBER =
      Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneOffset.UTC);

  private final InMemorySpendLedger ledger = new InMemorySpendLedger();
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

  private SpendCapInterceptor interceptor(Clock clock) {
    return new SpendCapInterceptor(
        ledger, new SpendCapProperties("dev"), new SpendMetrics(registry), clock);
  }

  private static PromptContext turn() {
    return new PromptContext("hello", Map.of(SpendScope.PRODUCT_DECLARATION, "verikora"));
  }

  @BeforeEach
  void caps() {
    ledger.caps.put(
        VERIKORA, new SpendCap(VERIKORA, new BigDecimal("10"), SpendCapAction.REFUSE, null));
  }

  @Test
  @DisplayName("under the cap the turn passes untouched, and the ratio is published")
  void underTheCapPasses() {
    ledger.spend.put("dev/verikora@" + NOVEMBER, new BigDecimal("4"));

    PromptContext out = interceptor(IN_NOVEMBER).beforeExecution(turn());

    assertThat(out).isEqualTo(turn());
    assertThat(registry.get("spend.cap.ratio").tag("scope", "dev/verikora").gauge().value())
        .isEqualTo(0.4);
  }

  @Test
  @DisplayName("at 100 % a REFUSE cap stops the turn with the typed code")
  void atTheCapRefuses() {
    ledger.spend.put("dev/verikora@" + NOVEMBER, new BigDecimal("10"));

    assertThatThrownBy(() -> interceptor(IN_NOVEMBER).beforeExecution(turn()))
        .isInstanceOfSatisfying(
            PipelineShortCircuitException.class,
            refusal -> assertThat(refusal.reason()).isEqualTo("provider_spend_cap_reached"));
    assertThat(registry.get("spend.cap.refused").counter().count()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("at 100 % a DOWNGRADE cap serves the cheaper model and tells the router to leave it")
  void atTheCapDowngrades() {
    ledger.caps.put(
        VERIKORA, new SpendCap(VERIKORA, new BigDecimal("10"), SpendCapAction.DOWNGRADE, "small"));
    ledger.spend.put("dev/verikora@" + NOVEMBER, new BigDecimal("12"));

    PromptContext out = interceptor(IN_NOVEMBER).beforeExecution(turn());

    assertThat(out.routedProvider()).isEqualTo("small");
    assertThat(out.systemMetadata()).containsEntry(SpendCapInterceptor.DOWNGRADED_MARKER, true);
  }

  @Test
  @DisplayName("a counter that cannot be read refuses the turn: the control fails closed")
  void unreadableCounterRefuses() {
    ledger.unreadable = true;

    assertThatThrownBy(() -> interceptor(IN_NOVEMBER).beforeExecution(turn()))
        .isInstanceOfSatisfying(
            PipelineShortCircuitException.class,
            refusal ->
                assertThat(refusal.reason()).isEqualTo("provider_spend_counter_unavailable"));
    assertThat(interceptor(IN_NOVEMBER).failsClosed()).isTrue();
  }

  @Test
  @DisplayName("a scope with no cap row is refused, never served uncapped")
  void noCapRowRefuses() {
    ledger.caps.clear();

    assertThatThrownBy(() -> interceptor(IN_NOVEMBER).beforeExecution(turn()))
        .isInstanceOf(PipelineShortCircuitException.class);
  }

  @Test
  @DisplayName("a new calendar month starts from zero: last month's spend does not count")
  void monthBoundaryResets() {
    ledger.spend.put("dev/verikora@" + NOVEMBER, new BigDecimal("10"));
    Clock inDecember = Clock.fixed(Instant.parse("2026-12-01T00:00:01Z"), ZoneOffset.UTC);

    assertThatThrownBy(() -> interceptor(IN_NOVEMBER).beforeExecution(turn()))
        .isInstanceOf(PipelineShortCircuitException.class);
    assertThat(interceptor(inDecember).beforeExecution(turn())).isEqualTo(turn());
  }

  @Test
  @DisplayName("a turn whose producer declared no product is counted in the unattributed scope")
  void undeclaredProductIsUnattributed() {
    SpendScope unattributed = new SpendScope("dev", SpendScope.UNATTRIBUTED);
    ledger.caps.put(
        unattributed, new SpendCap(unattributed, BigDecimal.ONE, SpendCapAction.REFUSE, null));
    ledger.spend.put("dev/unattributed@" + NOVEMBER, BigDecimal.ONE);

    assertThatThrownBy(
            () -> interceptor(IN_NOVEMBER).beforeExecution(new PromptContext("hello", Map.of())))
        .isInstanceOf(PipelineShortCircuitException.class);
  }
}
