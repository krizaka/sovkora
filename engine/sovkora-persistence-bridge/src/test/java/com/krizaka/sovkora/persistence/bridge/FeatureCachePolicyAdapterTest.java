package com.krizaka.sovkora.persistence.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.core.domain.model.chat.CacheStrategy;
import com.krizaka.sovkora.core.domain.ports.outbound.PromptCachePolicy;
import com.krizaka.sovkora.persistence.domain.ports.inbound.FeatureCachePolicyManager;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FeatureCachePolicyAdapterTest {

  private final FeatureCachePolicyManager manager = mock(FeatureCachePolicyManager.class);
  private final FeatureCachePolicyAdapter adapter = new FeatureCachePolicyAdapter(manager);

  @Test
  void theStrategyIsTheManagersAnswer() {
    when(manager.strategy("chat.support")).thenReturn(Optional.of("NONE"));

    assertEquals(CacheStrategy.NONE, adapter.strategy("chat.support"));
  }

  @Test
  void withoutAnyRowOrWithAnUnknownValueTheSafeDefaultApplies() {
    when(manager.strategy("x")).thenReturn(Optional.empty());
    when(manager.strategy("y")).thenReturn(Optional.of("FUTURE_MODE"));

    assertEquals(PromptCachePolicy.DEFAULT_STRATEGY, adapter.strategy("x"));
    assertEquals(PromptCachePolicy.DEFAULT_STRATEGY, adapter.strategy("y"));
  }
}
