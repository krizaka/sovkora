package com.krizaka.sovkora.adapter.springai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.krizaka.sovkora.adapter.springai.chat.SpringAiChatInvocation;
import com.krizaka.sovkora.adapter.springai.chat.SpringAiSpeechSynthesis;
import com.krizaka.sovkora.adapter.springai.client.ChatGeneratorClientImpl;
import com.krizaka.sovkora.adapter.springai.client.EmbeddingClientImpl;
import com.krizaka.sovkora.adapter.springai.image.SpringAiImageGeneratorClient;
import com.krizaka.sovkora.adapter.springai.memory.SpringAiToolAnswerCodec;
import com.krizaka.sovkora.adapter.springai.prompt.PipelineOptionsRegistry;
import com.krizaka.sovkora.adapter.springai.provider.SpringAiDynamicChatModelFactory;
import com.krizaka.sovkora.adapter.springai.routing.DynamicModelRouter;
import com.krizaka.sovkora.adapter.springai.routing.RoutedChatModel;
import com.krizaka.sovkora.adapter.springai.routing.RoutedTextToSpeechModel;
import com.krizaka.sovkora.adapter.springai.routing.UniversalProxyChatProvider;
import com.krizaka.sovkora.adapter.springai.spend.SpendGuard;
import com.krizaka.sovkora.core.application.engine.Engine;
import com.krizaka.sovkora.core.application.routing.DynamicChatModelFactory;
import com.krizaka.sovkora.core.domain.ports.outbound.ChatGeneratorClient;
import com.krizaka.sovkora.core.domain.ports.outbound.ChatInvocation;
import com.krizaka.sovkora.core.domain.ports.outbound.EmbeddingClient;
import com.krizaka.sovkora.core.domain.ports.outbound.ImageGeneratorClient;
import com.krizaka.sovkora.core.domain.ports.outbound.ModelEndpointResolver;
import com.krizaka.sovkora.core.domain.ports.outbound.SpeechSynthesis;
import com.krizaka.sovkora.core.domain.ports.outbound.ToolAnswerCodec;
import com.krizaka.sovkora.core.infrastructure.config.CoreProperties;
import com.krizaka.sovkora.persistence.domain.ports.inbound.CatalogModelManager;
import org.junit.jupiter.api.Test;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * What the adapter wires: the inference clients and the routing cluster, unless the host brings its
 * own.
 */
class SpringAiAdapterAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(SpringAiAdapterAutoConfiguration.class))
          .withBean(Engine.class, () -> mock(Engine.class))
          .withBean(PipelineOptionsRegistry.class, () -> mock(PipelineOptionsRegistry.class))
          .withBean(ModelEndpointResolver.class, () -> mock(ModelEndpointResolver.class))
          .withBean(SpendGuard.class, SpendGuard::unlimited)
          .withBean(CoreProperties.class, () -> mock(CoreProperties.class))
          .withBean(CatalogModelManager.class, () -> mock(CatalogModelManager.class));

  @Test
  void theChatAndEmbeddingPortsAreWired() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(ChatGeneratorClient.class);
          assertThat(context)
              .getBean(ChatGeneratorClient.class)
              .isInstanceOf(ChatGeneratorClientImpl.class);
          assertThat(context).hasSingleBean(EmbeddingClient.class);
          assertThat(context)
              .getBean(EmbeddingClient.class)
              .isInstanceOf(EmbeddingClientImpl.class);
        });
  }

  @Test
  void thePromptAssemblyIsRegisteredWithItsOptionsRegistry() {
    runner.run(
        context -> {
          assertThat(context)
              .hasSingleBean(com.krizaka.sovkora.core.domain.ports.outbound.PromptAssembly.class);
          assertThat(context)
              .getBean(com.krizaka.sovkora.core.domain.ports.outbound.PromptAssembly.class)
              .isInstanceOf(
                  com.krizaka.sovkora.adapter.springai.prompt.SpringAiPromptAssembly.class);
        });
  }

  @Test
  void theToolResolutionIsRegisteredAndAHostOneWins() {
    runner.run(
        context ->
            assertThat(context)
                .getBean(com.krizaka.sovkora.core.domain.ports.outbound.ToolResolution.class)
                .isInstanceOf(
                    com.krizaka.sovkora.adapter.springai.tool.SpringAiToolResolution.class));
    var own = mock(com.krizaka.sovkora.core.domain.ports.outbound.ToolResolution.class);
    runner
        .withBean(com.krizaka.sovkora.core.domain.ports.outbound.ToolResolution.class, () -> own)
        .run(
            context ->
                assertThat(context)
                    .getBean(com.krizaka.sovkora.core.domain.ports.outbound.ToolResolution.class)
                    .isSameAs(own));
  }

  @Test
  void aHostPortWins() {
    ChatGeneratorClient own = mock(ChatGeneratorClient.class);
    runner
        .withBean(ChatGeneratorClient.class, () -> own)
        .run(context -> assertThat(context).getBean(ChatGeneratorClient.class).isSameAs(own));
  }

  @Test
  void theRoutingClusterIsRegisteredAsThePrimaryModels() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(UniversalProxyChatProvider.class);
          assertThat(context).hasSingleBean(DynamicModelRouter.class);
          assertThat(context.getBean(ChatModel.class)).isInstanceOf(RoutedChatModel.class);
          assertThat(context.getBean(TextToSpeechModel.class))
              .isInstanceOf(RoutedTextToSpeechModel.class);
        });
  }

  @Test
  void theHostsObservationRegistryIsAcceptedButNeverRequired() {
    runner
        .withBean(
            io.micrometer.observation.ObservationRegistry.class,
            io.micrometer.observation.ObservationRegistry::create)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(UniversalProxyChatProvider.class);
            });
    runner.run(context -> assertThat(context).hasNotFailed());
  }

  @Test
  void theRoutingClusterBacksOffForTheRouterTheHostBrings() {
    var own = mock(DynamicModelRouter.class);
    runner
        .withBean(DynamicModelRouter.class, () -> own)
        .run(context -> assertThat(context.getBean(DynamicModelRouter.class)).isSameAs(own));
  }

  @Test
  void theDynamicChatModelFactoryIsWiredOverTheThreeProviders() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(DynamicChatModelFactory.class);
          assertThat(context.getBean(DynamicChatModelFactory.class))
              .isInstanceOf(SpringAiDynamicChatModelFactory.class);
          var model = context.getBean(DynamicChatModelFactory.class);
          assertThat(model.createChatModel("anthropic", "claude-x", "k")).isNotNull();
          assertThat(model.createChatModel("gemini", "gemini-x", "k")).isNotNull();
          assertThat(model.createChatModel("openai", "gpt-x", "k")).isNotNull();
        });
  }

  @Test
  void theChatInvocationPortIsWiredOverThePrimaryModel() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(ChatInvocation.class);
          assertThat(context.getBean(ChatInvocation.class))
              .isInstanceOf(SpringAiChatInvocation.class);
        });
  }

  @Test
  void theSpeechSynthesisPortIsWiredOverTheRoutedModel() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(SpeechSynthesis.class);
          assertThat(context.getBean(SpeechSynthesis.class))
              .isInstanceOf(SpringAiSpeechSynthesis.class);
        });
  }

  @Test
  void aHostSpeechSynthesisWins() {
    var own = mock(SpeechSynthesis.class);
    runner
        .withBean(SpeechSynthesis.class, () -> own)
        .run(context -> assertThat(context.getBean(SpeechSynthesis.class)).isSameAs(own));
  }

  @Test
  void theToolAnswerCodecAndTheImageClientAreWiredAndAHostOneWins() {
    runner.run(
        context -> {
          assertThat(context.getBean(ToolAnswerCodec.class))
              .isInstanceOf(SpringAiToolAnswerCodec.class);
          assertThat(context.getBean(ImageGeneratorClient.class))
              .isInstanceOf(SpringAiImageGeneratorClient.class);
        });
    var own = mock(ImageGeneratorClient.class);
    runner
        .withBean(ImageGeneratorClient.class, () -> own)
        .run(context -> assertThat(context.getBean(ImageGeneratorClient.class)).isSameAs(own));
  }

  @Test
  void aHostChatInvocationWins() {
    var own = mock(ChatInvocation.class);
    runner
        .withBean(ChatInvocation.class, () -> own)
        .run(context -> assertThat(context.getBean(ChatInvocation.class)).isSameAs(own));
  }

  @Test
  void aHostFactoryWins() {
    var own = mock(DynamicChatModelFactory.class);
    runner
        .withBean(DynamicChatModelFactory.class, () -> own)
        .run(context -> assertThat(context.getBean(DynamicChatModelFactory.class)).isSameAs(own));
  }
}
