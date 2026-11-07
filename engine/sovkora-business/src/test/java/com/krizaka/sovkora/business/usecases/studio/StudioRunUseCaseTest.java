package com.krizaka.sovkora.business.usecases.studio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.business.api.Capability;
import com.krizaka.sovkora.business.api.PlanningMode;
import com.krizaka.sovkora.business.api.StudioPayload;
import com.krizaka.sovkora.business.api.UseCaseContext;
import com.krizaka.sovkora.studio.domain.port.StudioRunClient;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StudioRunUseCaseTest {

  private static final UUID INSTALLATION = UUID.fromString("9f1c0a10-0000-4000-8000-000000000010");

  private final StudioRunClient studioRunClient = mock(StudioRunClient.class);
  private final StudioRunUseCase useCase = new StudioRunUseCase(studioRunClient);

  private static UseCaseContext context() {
    return new UseCaseContext("intent-1", "actor-1", "session-1", Set.of(), Map.of());
  }

  @Test
  @DisplayName("A Studio run is registered under Capability.STUDIO, so the registry can route it")
  void descriptorRoutesByCapability() {
    assertThat(useCase.descriptor().capability()).isEqualTo(Capability.STUDIO);
    assertThat(useCase.descriptor().id()).isEqualTo("studio.run");
  }

  @Test
  @DisplayName("The plan is the blueprint — nothing here asks a model what to do next")
  void planningIsDeterministic() {
    assertThat(useCase.descriptor().planning()).isEqualTo(PlanningMode.DETERMINISTIC);
  }

  @Test
  @DisplayName("It returns the run id, not a result — a run outlives the request")
  void returnsTheRunId() {
    when(studioRunClient.start(eq(INSTALLATION), any())).thenReturn("run-7");

    String runId = useCase.execute(context(), new StudioPayload(INSTALLATION, Map.of("a", "b")));

    assertThat(runId).isEqualTo("run-7");
  }

  @Test
  @DisplayName("A refusal from the marketplace surfaces rather than being swallowed")
  void refusalSurfaces() {
    when(studioRunClient.start(any(), any()))
        .thenThrow(new IllegalStateException("the Studio marketplace is not deployed"));

    assertThatThrownBy(() -> useCase.execute(context(), new StudioPayload(INSTALLATION, Map.of())))
        .isInstanceOf(IllegalStateException.class);
  }
}
