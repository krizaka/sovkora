package com.krizaka.sovkora.interceptor.validation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.krizaka.sovkora.core.domain.model.AssertionContract;
import com.krizaka.sovkora.core.domain.model.ValidationStepType;
import com.krizaka.sovkora.core.domain.model.control.JudgeUnavailableException;
import com.krizaka.sovkora.core.domain.ports.outbound.ProviderChatClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link ModelTestShaperAdapter}. */
@ExtendWith(MockitoExtension.class)
class ModelTestShaperAdapterTest {

  @Mock private ProviderChatClient chatModel;

  @Test
  @DisplayName("Generates valid assertion contract from well-formed JSON response")
  void generateAssertions_validJson() {
    String modelResponse =
        """
        {
          "schemaName": "code-review",
          "assertions": [
            "Response must contain a code block",
            "Response must not contain placeholder values like TODO"
          ]
        }
        """;
    when(chatModel.completeWithDefault(any(), any())).thenReturn(modelResponse);

    var adapter = new ModelTestShaperAdapter(chatModel);
    AssertionContract contract = adapter.generateAssertions("Write a function", "def foo(): pass");

    assertThat(contract.schemaName()).isEqualTo("code-review");
    assertThat(contract.assertions()).hasSize(2);
    assertThat(contract.assertions().getFirst()).contains("code block");
    assertThat(contract.hasAssertions()).isTrue();
    assertThat(contract.generatedAt()).isNotBlank();
  }

  @Test
  @DisplayName("Generates assertions from markdown-fenced JSON response")
  void generateAssertions_markdownFencedJson() {
    String modelResponse =
        """
        Here are the assertions:
        ```json
        {
          "schemaName": "fenced",
          "assertions": ["check one"]
        }
        ```
        """;
    when(chatModel.completeWithDefault(any(), any())).thenReturn(modelResponse);

    var adapter = new ModelTestShaperAdapter(chatModel);
    AssertionContract contract = adapter.generateAssertions("prompt", "response");

    assertThat(contract.schemaName()).isEqualTo("fenced");
    assertThat(contract.assertions()).containsExactly("check one");
  }

  @Test
  @DisplayName("Throws unavailable (never an empty contract) when the model returns null")
  void generateAssertions_nullResponse() {
    when(chatModel.completeWithDefault(any(), any())).thenReturn(null);

    var adapter = new ModelTestShaperAdapter(chatModel);

    assertThatThrownBy(() -> adapter.generateAssertions("prompt", "response"))
        .isInstanceOf(JudgeUnavailableException.class)
        .extracting(e -> ((JudgeUnavailableException) e).step())
        .isEqualTo(ValidationStepType.TDR_D);
  }

  @Test
  @DisplayName("Throws unavailable when the model returns empty text")
  void generateAssertions_emptyText() {
    when(chatModel.completeWithDefault(any(), any())).thenReturn("");

    var adapter = new ModelTestShaperAdapter(chatModel);

    assertThatThrownBy(() -> adapter.generateAssertions("prompt", "response"))
        .isInstanceOf(JudgeUnavailableException.class)
        .extracting(e -> ((JudgeUnavailableException) e).step())
        .isEqualTo(ValidationStepType.TDR_D);
  }

  @Test
  @DisplayName("Throws unavailable when the model returns invalid JSON")
  void generateAssertions_invalidJson() {
    when(chatModel.completeWithDefault(any(), any())).thenReturn("{invalid json");

    var adapter = new ModelTestShaperAdapter(chatModel);

    assertThatThrownBy(() -> adapter.generateAssertions("prompt", "response"))
        .isInstanceOf(JudgeUnavailableException.class)
        .extracting(e -> ((JudgeUnavailableException) e).step())
        .isEqualTo(ValidationStepType.TDR_D);
  }

  @Test
  @DisplayName("Throws unavailable when the model returns plain text (no JSON)")
  void generateAssertions_plainText() {
    when(chatModel.completeWithDefault(any(), any()))
        .thenReturn("I cannot generate assertions for this request.");

    var adapter = new ModelTestShaperAdapter(chatModel);

    assertThatThrownBy(() -> adapter.generateAssertions("prompt", "response"))
        .isInstanceOf(JudgeUnavailableException.class)
        .extracting(e -> ((JudgeUnavailableException) e).step())
        .isEqualTo(ValidationStepType.TDR_D);
  }

  @Test
  @DisplayName("Throws unavailable when the model call throws")
  void generateAssertions_runtimeException() {
    when(chatModel.completeWithDefault(any(), any()))
        .thenThrow(new RuntimeException("Model unavailable"));

    var adapter = new ModelTestShaperAdapter(chatModel);

    assertThatThrownBy(() -> adapter.generateAssertions("prompt", "response"))
        .isInstanceOf(JudgeUnavailableException.class)
        .extracting(e -> ((JudgeUnavailableException) e).step())
        .isEqualTo(ValidationStepType.TDR_D);
  }

  @Test
  @DisplayName("Handles JSON with missing assertions field")
  void generateAssertions_missingAssertionsField() {
    String modelResponse = "{\"schemaName\": \"partial\"}";
    when(chatModel.completeWithDefault(any(), any())).thenReturn(modelResponse);

    var adapter = new ModelTestShaperAdapter(chatModel);
    AssertionContract contract = adapter.generateAssertions("prompt", "response");

    assertThat(contract.schemaName()).isEqualTo("partial");
    assertThat(contract.assertions()).isEmpty();
  }

  @Test
  @DisplayName("Handles JSON with missing schemaName field")
  void generateAssertions_missingSchemaNameField() {
    String modelResponse = "{\"assertions\": [\"check\"]}";
    when(chatModel.completeWithDefault(any(), any())).thenReturn(modelResponse);

    var adapter = new ModelTestShaperAdapter(chatModel);
    AssertionContract contract = adapter.generateAssertions("prompt", "response");

    assertThat(contract.schemaName()).isEqualTo("tdr");
    assertThat(contract.assertions()).containsExactly("check");
  }

  @Test
  @DisplayName("Constructor rejects null ProviderChatClient")
  void constructor_nullProviderChatClient_throws() {
    assertThatThrownBy(() -> new ModelTestShaperAdapter(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("ProviderChatClient");
  }
}
