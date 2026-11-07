package com.krizaka.sovkora.adapter.springai.structured;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.sovkora.core.domain.model.structured.StructuredOutputInvalidException;
import com.krizaka.sovkora.core.domain.model.structured.StructuredOutputRequest;
import com.krizaka.sovkora.core.domain.ports.outbound.StructuredOutputBounds;
import io.micrometer.observation.ObservationRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.StructuredOutputChatOptions;

class SpringAiStructuredOutputClientTest {

  record Verdict(String label, int score) {}

  /**
   * A stub model that answers the scripted texts in order (the last one repeats) and records
   * prompts.
   */
  private static final class ScriptedModel implements ChatModel {
    final List<Prompt> prompts = new ArrayList<>();
    private final List<String> answers;
    private final ChatOptions defaults;

    ScriptedModel(String... answers) {
      this(ChatOptions.builder().build(), answers);
    }

    ScriptedModel(ChatOptions defaults, String... answers) {
      this.defaults = defaults;
      this.answers = List.of(answers);
    }

    @Override
    public ChatOptions getOptions() {
      return defaults;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
      prompts.add(prompt);
      String text = answers.get(Math.min(prompts.size(), answers.size()) - 1);
      return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
  }

  private static StructuredOutputRequest<Verdict> ask() {
    return new StructuredOutputRequest<>("Judge.", "Is it fine?", Verdict.class);
  }

  private static SpringAiStructuredOutputClient client(ChatModel model, int attempts) {
    return new SpringAiStructuredOutputClient(
        model, StructuredOutputBounds.fixed(attempts), ObservationRegistry.NOOP);
  }

  @Test
  void aValidAnswerIsReturnedTypedInOneAttemptAndTheFormatIsAskedFor() {
    var model = new ScriptedModel("{\"label\":\"ok\",\"score\":7}");

    Verdict verdict = client(model, 2).generate(ask());

    assertThat(verdict).isEqualTo(new Verdict("ok", 7));
    assertThat(model.prompts).hasSize(1);
    assertThat(model.prompts.getFirst().getContents()).contains("Judge.").contains("Is it fine?");
    assertThat(model.prompts.getFirst().getContents()).contains("score");
  }

  @Test
  void anInvalidAnswerThenAValidOneYieldsTheObjectInTwoAttempts() {
    var model =
        new ScriptedModel(
            "{\"label\":\"ok\",\"score\":\"high\"}", "{\"label\":\"ok\",\"score\":7}");

    Verdict verdict = client(model, 2).generate(ask());

    assertThat(verdict).isEqualTo(new Verdict("ok", 7));
    assertThat(model.prompts).hasSize(2);
    // the second ask names what was wrong
    assertThat(model.prompts.get(1).getContents()).contains("score");
  }

  @Test
  void aPartialAnswerThenAValidOneIsCorrected() {
    var model = new ScriptedModel("{\"label\":\"ok\",\"sco", "{\"label\":\"ok\",\"score\":3}");

    assertThat(client(model, 2).generate(ask())).isEqualTo(new Verdict("ok", 3));
    assertThat(model.prompts).hasSize(2);
  }

  @Test
  void alwaysInvalidFailsWithTheTypedErrorAfterTheBoundNeverADefault() {
    var model = new ScriptedModel("not json at all");

    assertThatThrownBy(() -> client(model, 2).generate(ask()))
        .isInstanceOfSatisfying(
            StructuredOutputInvalidException.class,
            e -> {
              assertThat(e.attempts()).isEqualTo(2);
              assertThat(e.type()).isEqualTo("Verdict");
            })
        .isInstanceOf(IllegalStateException.class);
    assertThat(model.prompts).hasSize(2);
  }

  @Test
  void aBoundOfOneAsksOnce() {
    var model = new ScriptedModel("not json at all");

    assertThatThrownBy(() -> client(model, 1).generate(ask()))
        .isInstanceOf(StructuredOutputInvalidException.class);
    assertThat(model.prompts).hasSize(1);
  }

  @Test
  void theBoundIsReadForTheFeatureOfTheCall() {
    var model = new ScriptedModel("not json at all");
    var bounds = (StructuredOutputBounds) key -> "agent.code.review".equals(key) ? 3 : 1;
    var client = new SpringAiStructuredOutputClient(model, bounds, ObservationRegistry.NOOP);

    assertThatThrownBy(
            () ->
                client.generate(
                    new StructuredOutputRequest<>(
                        "", "Is it fine?", Verdict.class, "agent.code.review")))
        .isInstanceOfSatisfying(
            StructuredOutputInvalidException.class, e -> assertThat(e.attempts()).isEqualTo(3));
    assertThat(model.prompts).hasSize(3);

    var other = new ScriptedModel("not json at all");
    var otherClient = new SpringAiStructuredOutputClient(other, bounds, ObservationRegistry.NOOP);
    assertThatThrownBy(() -> otherClient.generate(ask()))
        .isInstanceOf(StructuredOutputInvalidException.class);
    assertThat(other.prompts).hasSize(1);
  }

  @Test
  void aModelThatTakesAJsonSchemaIsAskedInItsNativeMode() {
    var model =
        new ScriptedModel(
            StructuredOutputChatOptions.builder().build(), "{\"label\":\"ok\",\"score\":7}");

    assertThat(client(model, 2).generate(ask())).isEqualTo(new Verdict("ok", 7));
    assertThat(model.prompts.getFirst().getOptions())
        .isInstanceOfSatisfying(
            StructuredOutputChatOptions.class,
            options -> assertThat(options.getOutputSchema()).contains("score"));
  }

  @Test
  void aModelWithoutNativeModeFallsBackToTheAdvisorAndTheFormatInThePrompt() {
    var model = new ScriptedModel("{\"label\":\"ok\",\"score\":7}");

    assertThat(client(model, 2).generate(ask())).isEqualTo(new Verdict("ok", 7));
    assertThat(model.prompts.getFirst().getOptions())
        .isNotInstanceOf(StructuredOutputChatOptions.class);
    assertThat(model.prompts.getFirst().getContents()).contains("score");
  }
}
