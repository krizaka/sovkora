package com.krizaka.sovkora.interceptor.validation;

import com.krizaka.sovkora.core.domain.model.AssertionContract;
import com.krizaka.sovkora.core.domain.model.ValidationStepType;
import com.krizaka.sovkora.core.domain.model.chat.EngineMessage;
import com.krizaka.sovkora.core.domain.model.control.JudgeUnavailableException;
import com.krizaka.sovkora.core.domain.ports.outbound.ProviderChatClient;
import com.krizaka.sovkora.core.domain.ports.outbound.TestShaperPort;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Concrete adapter implementing the {@link TestShaperPort} outbound port for Tier D (Test-Driven
 * Response) assertion generation.
 *
 * <p>Uses a constructor-injected {@link ProviderChatClient} (preferably a fast reasoning model like
 * Qwen-2.5-Coder) to generate structured JSON assertion contracts. The prompt instructs the model
 * to produce machine-evaluable assertion rules that the LLM response must satisfy.
 *
 * <p>Fails closed: a model that fails, answers nothing or answers something unreadable throws
 * {@link JudgeUnavailableException}, never an empty contract (an empty contract means "nothing to
 * assert").
 *
 * @since 1.1.0
 */
public class ModelTestShaperAdapter implements TestShaperPort {

  private static final Logger logger = LoggerFactory.getLogger(ModelTestShaperAdapter.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String TDR_SYSTEM_PROMPT =
      """
      You are a test assertion generator. Given a user prompt and an AI response, generate a set of
      machine-evaluable assertion rules that the response MUST satisfy.

      Respond with ONLY a valid JSON object in this exact format:
      {
        "schemaName": "<brief context identifier>",
        "assertions": [
          "Response must contain a code block",
          "Response must address the user's specific question about X",
          "Response must not contain placeholder values like TODO or FIXME"
        ]
      }

      Focus on:
      1. Structural completeness (does the response have all requested parts?)
      2. Factual accuracy markers (does it reference correct concepts?)
      3. Anti-hallucination checks (does it avoid unsupported claims?)
      4. Format compliance (does it match the expected output format?)
      """;

  private final ProviderChatClient chatModel;

  /**
   * Constructs a ModelTestShaperAdapter.
   *
   * @param chatModel The model port to use for assertion generation (e.g., Qwen-2.5-Coder).
   */
  public ModelTestShaperAdapter(ProviderChatClient chatModel) {
    this.chatModel =
        Objects.requireNonNull(chatModel, "ProviderChatClient must not be null for TDR Tier D");
  }

  @Override
  public AssertionContract generateAssertions(String userPrompt, String responseText) {
    String userContent = "User Prompt:\n" + userPrompt + "\n\nAI Response:\n" + responseText;
    String text;
    try {
      text =
          chatModel.completeWithDefault(
              List.of(EngineMessage.system(TDR_SYSTEM_PROMPT), EngineMessage.user(userContent)),
              0.0);
    } catch (RuntimeException e) {
      throw unavailable("the TDR model failed", e);
    }
    if (text == null || text.isBlank()) {
      throw unavailable("the TDR model returned an empty response", null);
    }
    return parseContract(text);
  }

  /** The contract in the model's JSON answer; an answer that holds none is an outage. */
  private AssertionContract parseContract(String text) {
    String json = extractJsonBlock(text);
    if (json == null) {
      throw unavailable("no JSON in the TDR response", null);
    }
    try {
      var node = MAPPER.readTree(json);
      String schemaName = node.has("schemaName") ? node.get("schemaName").asString("tdr") : "tdr";
      List<String> assertions =
          node.has("assertions")
              ? MAPPER.convertValue(node.get("assertions"), new TypeReference<List<String>>() {})
              : List.of();

      return new AssertionContract(schemaName, assertions, Instant.now().toString());
    } catch (RuntimeException e) {
      throw unavailable("unreadable TDR assertion JSON", e);
    }
  }

  private static JudgeUnavailableException unavailable(String cause, Throwable origin) {
    logger.warn("Tier D — {}.", cause);
    return new JudgeUnavailableException(ValidationStepType.TDR_D, cause, origin);
  }

  private String extractJsonBlock(String text) {
    return JsonBlocks.extract(text, false);
  }
}
