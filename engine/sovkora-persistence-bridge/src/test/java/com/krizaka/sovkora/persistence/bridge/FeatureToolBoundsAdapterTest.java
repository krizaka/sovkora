package com.krizaka.sovkora.persistence.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.core.domain.ports.outbound.ToolLoopBounds;
import com.krizaka.sovkora.persistence.domain.ports.inbound.FeatureToolBoundManager;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class FeatureToolBoundsAdapterTest {

  private final FeatureToolBoundManager manager = mock(FeatureToolBoundManager.class);
  private final FeatureToolBoundsAdapter adapter = new FeatureToolBoundsAdapter(manager);

  @Test
  void theBoundIsTheManagersAnswer() {
    when(manager.maxToolRounds("agent.code.issue-to-pr")).thenReturn(OptionalInt.of(3));

    assertEquals(3, adapter.maxRounds("agent.code.issue-to-pr"));
  }

  @Test
  void withoutAnyRowTheSafeDefaultApplies() {
    when(manager.maxToolRounds("x")).thenReturn(OptionalInt.empty());

    assertEquals(ToolLoopBounds.DEFAULT_MAX_ROUNDS, adapter.maxRounds("x"));
  }
}
