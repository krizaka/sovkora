package com.krizaka.sovkora.persistence.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.core.domain.ports.outbound.StructuredOutputBounds;
import com.krizaka.sovkora.persistence.domain.ports.inbound.FeatureStructuredBoundManager;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class FeatureStructuredBoundsAdapterTest {

  private final FeatureStructuredBoundManager manager = mock(FeatureStructuredBoundManager.class);
  private final FeatureStructuredBoundsAdapter adapter =
      new FeatureStructuredBoundsAdapter(manager);

  @Test
  void theBoundIsTheManagersAnswer() {
    when(manager.maxAttempts("agent.code.issue-to-pr")).thenReturn(OptionalInt.of(3));

    assertEquals(3, adapter.maxAttempts("agent.code.issue-to-pr"));
  }

  @Test
  void withoutAnyRowTheSafeDefaultApplies() {
    when(manager.maxAttempts("x")).thenReturn(OptionalInt.empty());

    assertEquals(StructuredOutputBounds.DEFAULT_MAX_ATTEMPTS, adapter.maxAttempts("x"));
  }
}
