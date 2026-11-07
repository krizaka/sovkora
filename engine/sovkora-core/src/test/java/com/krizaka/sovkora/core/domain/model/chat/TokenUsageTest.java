package com.krizaka.sovkora.core.domain.model.chat;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenUsageTest {

  @Test
  @DisplayName("a provider that reported nothing is unmeasured, not zero-consumption")
  void noneIsUnreported() {
    TokenUsage usage = TokenUsage.none();

    assertFalse(usage.reported());
    assertEquals(0, usage.kilotokens().compareTo(BigDecimal.ZERO));
  }

  @Test
  @DisplayName("a genuine zero is measured — the distinction decides release versus floor billing")
  void aReportedZeroIsStillReported() {
    TokenUsage usage = TokenUsage.reported(0, 0, 0);

    assertTrue(usage.reported());
    assertEquals(0, usage.kilotokens().compareTo(BigDecimal.ZERO));
  }

  @Test
  @DisplayName("kilotokens is what chat is priced in")
  void kilotokens() {
    assertEquals(
        0, TokenUsage.reported(1200, 2200, 3400).kilotokens().compareTo(new BigDecimal("3.4")));
  }

  @Test
  @DisplayName("a missing total is summed from the halves")
  void sumsWhenTheProviderOmitsTheTotal() {
    TokenUsage usage = TokenUsage.reported(120, 80, null);

    assertEquals(200, usage.totalTokens());
  }

  @Test
  @DisplayName("a provider's own total wins — some count tokens in neither half")
  void keepsTheProvidersTotalWhenItIsNotTheSum() {
    // Cached and reasoning tokens show up in the total without appearing in prompt or completion;
    // re-deriving the sum here would silently under-bill them.
    TokenUsage usage = TokenUsage.reported(120, 80, 500);

    assertEquals(500, usage.totalTokens());
  }

  @Test
  @DisplayName("a half the provider omitted counts as zero, not as unreported")
  void defaultsMissingHalvesToZero() {
    TokenUsage usage = TokenUsage.reported(null, 80, 80);

    assertTrue(usage.reported());
    assertEquals(0, usage.promptTokens());
  }

  @Test
  void rejectsNegativeCounts() {
    assertThrows(IllegalArgumentException.class, () -> new TokenUsage(-2, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new TokenUsage(0, -5, 0));
    assertThrows(IllegalArgumentException.class, () -> new TokenUsage(0, 0, -3));
  }

  @Test
  @DisplayName("a response with no usage still answers, so no call site has to null-check")
  void responseDefaultsToNone() {
    InternalChatResponse response = new InternalChatResponse("hi", "conv-1", null, null);

    assertFalse(response.tokenUsage().reported());
    assertTrue(response.metadata().isEmpty());
  }

  @Test
  @DisplayName("the three-argument overload keeps every non-inference call site working")
  void legacyOverloadYieldsNone() {
    InternalChatResponse response =
        new InternalChatResponse("chunk", "conv-1", java.util.Map.of("provider", "ollama"));

    assertFalse(response.tokenUsage().reported());
    assertEquals("ollama", response.metadata().get("provider"));
  }

  @Test
  @DisplayName("cached tokens are carried beside the total, and the three-argument form has none")
  void carriesCachedTokens() {
    TokenUsage usage = TokenUsage.reported(1000, 50, 1050, 800L, 120L);

    assertEquals(800, usage.cachedReadTokens());
    assertEquals(120, usage.cacheWriteTokens());
    assertEquals(1050, usage.totalTokens());
    assertEquals(0, TokenUsage.reported(10, 5, 15).cachedReadTokens());
    assertEquals(0, new TokenUsage(10, 5, 15).cacheWriteTokens());
  }

  @Test
  @DisplayName("cached counts a provider omitted are zero, and billing is unchanged by them")
  void cachedTokensDoNotChangeWhatIsBilled() {
    assertEquals(0, TokenUsage.reported(10, 5, 15, null, null).cachedReadTokens());
    assertEquals(
        0,
        TokenUsage.reported(1000, 50, 1050, 800L, 0L)
            .kilotokens()
            .compareTo(TokenUsage.reported(1000, 50, 1050).kilotokens()));
  }

  @Test
  @DisplayName("an unreported turn reports no cached tokens, and negative counts are refused")
  void cachedTokensAreCountable() {
    assertEquals(0, TokenUsage.none().cachedReadTokens());
    assertThrows(IllegalArgumentException.class, () -> new TokenUsage(1, 1, 2, -1, 0));
    assertThrows(IllegalArgumentException.class, () -> new TokenUsage(1, 1, 2, 0, -1));
  }

  @Test
  @DisplayName("two turns add up their cached tokens as well")
  void plusAddsCachedTokens() {
    TokenUsage sum =
        TokenUsage.reported(10, 5, 15, 4L, 1L).plus(TokenUsage.reported(20, 5, 25, 6L, 2L));

    assertEquals(30, sum.promptTokens());
    assertEquals(40, sum.totalTokens());
    assertEquals(10, sum.cachedReadTokens());
    assertEquals(3, sum.cacheWriteTokens());
    assertEquals(sum, TokenUsage.none().plus(sum));
    assertEquals(sum, sum.plus(TokenUsage.none()));
  }

  @Test
  void cachedTokensAreMeasuredInTheUnitChatIsPricedIn() {
    TokenUsage usage = TokenUsage.reported(3000, 500, 3500, 2000L, 1000L);

    assertEquals(0, usage.cachedReadKilotokens().compareTo(new BigDecimal("2")));
    assertEquals(0, usage.cacheWriteKilotokens().compareTo(new BigDecimal("1")));
    assertEquals(0, usage.kilotokens().compareTo(new BigDecimal("3.5")));
  }

  @Test
  void anUnreportedTurnHasNoCachedShare() {
    assertEquals(0, TokenUsage.none().cachedReadKilotokens().compareTo(BigDecimal.ZERO));
  }
}
