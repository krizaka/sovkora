package com.krizaka.sovkora.core.application.engine;

import static com.krizaka.sovkora.test.TestConstants.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.core.domain.model.CapabilityDescriptor;
import com.krizaka.sovkora.core.domain.model.NodeState.Active;
import com.krizaka.sovkora.core.domain.model.NodeState.Invisible;
import com.krizaka.sovkora.core.domain.model.NodeState.Locked;
import com.krizaka.sovkora.core.domain.model.OperationGraph;
import com.krizaka.sovkora.core.domain.model.OperationNode;
import com.krizaka.sovkora.core.domain.ports.outbound.AdminRegistry;
import com.krizaka.sovkora.core.domain.ports.outbound.CapabilityProvider;
import com.krizaka.sovkora.core.domain.ports.outbound.InfrastructureStatusProvider;
import com.krizaka.sovkora.core.domain.ports.outbound.ModelCatalogProvider;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Unit tests verifying GraphEngine compilation and short-circuit logic. */
class GraphEngineTest {

  private AdminRegistry adminRegistry;
  private ModelCatalogProvider modelCatalogProvider;
  private CapabilityProvider capabilityProvider;

  @BeforeEach
  void setUp() {
    adminRegistry = new AdminRegistry();
    modelCatalogProvider = Mockito.mock(ModelCatalogProvider.class);
    Mockito.when(modelCatalogProvider.getActiveChatModel()).thenReturn(Optional.of("llama3.1:8b"));
    capabilityProvider = Mockito.mock(CapabilityProvider.class);
  }

  @Test
  void compileGraphShortCircuit() {
    CapabilityDescriptor textFeature = new CapabilityDescriptor("orazaka.core.chat.text", true);
    CapabilityDescriptor imageFeature = new CapabilityDescriptor("orazaka.core.chat.image", false);
    Mockito.when(capabilityProvider.findAll()).thenReturn(List.of(textFeature, imageFeature));

    // Lock the disabled feature
    adminRegistry.lock("orazaka.core.chat.image", "Locked by admin");

    GraphEngine engine =
        new GraphEngine(capabilityProvider, adminRegistry, offlineProber(), modelCatalogProvider);
    OperationGraph graph = engine.compileGraph();

    assertThat(graph.nodes()).hasSize(2);

    OperationNode textNode =
        graph.nodes().stream()
            .filter(n -> "orazaka.core.chat.text".equals(n.id()))
            .findFirst()
            .orElseThrow();
    OperationNode imageNode =
        graph.nodes().stream()
            .filter(n -> "orazaka.core.chat.image".equals(n.id()))
            .findFirst()
            .orElseThrow();

    // Text node is Active
    assertThat(textNode.state()).isInstanceOf(Active.class);

    // Image node is Invisible (short-circuited and ignored lock)
    assertThat(imageNode.state()).isInstanceOf(Invisible.class);
  }

  @Test
  void compileGraphAdminLocked() {
    CapabilityDescriptor textFeature = new CapabilityDescriptor("orazaka.core.chat.text", true);
    Mockito.when(capabilityProvider.findAll()).thenReturn(List.of(textFeature));

    adminRegistry.lock("orazaka.core.chat.text", "Maintenance mode");

    GraphEngine engine =
        new GraphEngine(capabilityProvider, adminRegistry, offlineProber(), modelCatalogProvider);
    OperationGraph graph = engine.compileGraph();

    assertThat(graph.nodes()).hasSize(1);
    OperationNode textNode = graph.nodes().getFirst();

    // State is Locked
    assertThat(textNode.state()).isInstanceOf(Locked.class);
    Locked lockedState = (Locked) textNode.state();
    assertThat(lockedState.reason()).isEqualTo("Maintenance mode");
    assertThat(lockedState.lockedAt()).isNotNull();
  }

  @Test
  void printSampleJsonPayload() throws Exception {
    CapabilityDescriptor textConfig = new CapabilityDescriptor("orazaka.core.chat.text", true);
    CapabilityDescriptor imageConfig = new CapabilityDescriptor("orazaka.core.chat.image", true);
    CapabilityDescriptor speechConfig =
        new CapabilityDescriptor("orazaka.core.media.speech", false);
    Mockito.when(capabilityProvider.findAll())
        .thenReturn(List.of(textConfig, imageConfig, speechConfig));

    adminRegistry.lock("orazaka.core.chat.image", "Out of API credits");

    GraphEngine engine =
        new GraphEngine(capabilityProvider, adminRegistry, offlineProber(), modelCatalogProvider);
    OperationGraph graph = engine.compileGraph();

    // Jackson 3: ObjectMapper is immutable (configure via builder); java.time support is built in.
    JsonMapper mapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    String json = mapper.writeValueAsString(graph);

    // Verify serializable and structurally sound
    assertThat(json).isNotBlank().contains("\"nodes\"");
    assertThat(graph.nodes()).hasSize(3);
  }

  @Test
  void compileGraphActiveModelNull() {
    CapabilityDescriptor textFeature = new CapabilityDescriptor("orazaka.core.chat.text", true);
    Mockito.when(capabilityProvider.findAll()).thenReturn(List.of(textFeature));
    Mockito.when(modelCatalogProvider.getActiveChatModel()).thenReturn(Optional.empty());

    GraphEngine engine =
        new GraphEngine(
            capabilityProvider,
            adminRegistry,
            Mockito.mock(InfrastructureStatusProvider.class),
            modelCatalogProvider);
    OperationGraph graph = engine.compileGraph();

    assertThat(graph.nodes()).hasSize(1);
    assertThat(graph.nodes().getFirst().state()).isInstanceOf(Locked.class);
    assertThat(((Locked) graph.nodes().getFirst().state()).reason()).contains("Capability missing");
  }

  @Test
  void compileGraphVideoFeatureProbing() {
    CapabilityDescriptor videoFeature = new CapabilityDescriptor("orazaka.core.media.video", true);
    Mockito.when(capabilityProvider.findAll()).thenReturn(List.of(videoFeature));

    InfrastructureStatusProvider mockProber = Mockito.mock(InfrastructureStatusProvider.class);
    // Case 1: Both offline
    Mockito.when(mockProber.isVideoEngineOnline()).thenReturn(false);
    Mockito.when(mockProber.isImageEngineOnline()).thenReturn(false);
    Mockito.when(mockProber.getVideoProbePort()).thenReturn(8188);
    Mockito.when(mockProber.getImageProbePort()).thenReturn(8085);

    GraphEngine engine =
        new GraphEngine(capabilityProvider, adminRegistry, mockProber, modelCatalogProvider);
    OperationGraph graph = engine.compileGraph();
    assertThat(((Locked) graph.nodes().getFirst().state()).reason())
        .contains("Video engine offline on port 8188");

    // Case 2: Video online
    engine.invalidateCache();
    Mockito.when(mockProber.isVideoEngineOnline()).thenReturn(true);
    OperationGraph graph2 = engine.compileGraph();
    assertThat(graph2.nodes().getFirst().state()).isInstanceOf(Active.class);

    // Case 3: Image online (fallback)
    engine.invalidateCache();
    Mockito.when(mockProber.isVideoEngineOnline()).thenReturn(false);
    Mockito.when(mockProber.isImageEngineOnline()).thenReturn(true);
    OperationGraph graph3 = engine.compileGraph();
    assertThat(graph3.nodes().getFirst().state()).isInstanceOf(Active.class);
  }

  /** A prober that has seen nothing online, on the default probe ports. */
  private static InfrastructureStatusProvider offlineProber() {
    return new InfrastructureStatusProvider() {
      @Override
      public boolean isVideoEngineOnline() {
        return false;
      }

      @Override
      public boolean isImageEngineOnline() {
        return false;
      }

      @Override
      public int getVideoProbePort() {
        return 8188;
      }

      @Override
      public int getImageProbePort() {
        return 8085;
      }
    };
  }
}
