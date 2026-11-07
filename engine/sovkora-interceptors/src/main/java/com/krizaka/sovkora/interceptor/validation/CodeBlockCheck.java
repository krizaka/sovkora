package com.krizaka.sovkora.interceptor.validation;

import com.krizaka.sovkora.core.domain.ports.outbound.CodeSandboxPort;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.ObjectMapper;

/**
 * The deterministic crash-test of the fenced code blocks of an answer (Tier B). Every fenced block
 * must be closed; a {@code json} block must parse; a wired {@link CodeSandboxPort} compiles or
 * lints each block as well, and a sandbox that itself fails rejects the answer (fails closed).
 */
final class CodeBlockCheck {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Pattern CODE_FENCE =
      Pattern.compile("```([\\w+#.-]*)[ \\t]*\\R(.*?)```", Pattern.DOTALL);

  private final CodeSandboxPort sandbox;

  CodeBlockCheck(CodeSandboxPort sandbox) {
    this.sandbox = sandbox;
  }

  /**
   * @param text the answer, whole or partial
   * @param complete whether {@code text} is the whole answer. On a partial (streamed) answer only
   *     the in-process checks apply, to the blocks already closed: an open fence is not yet a
   *     failure and the sandbox waits for the whole
   * @return the first failure, or empty when every block passes
   */
  Optional<String> failure(String text, boolean complete) {
    if (complete && text.split("```", -1).length % 2 == 0) {
      return Optional.of("Unterminated code block: a ``` fence is never closed.");
    }
    Matcher blocks = CODE_FENCE.matcher(text);
    while (blocks.find()) {
      String language = blocks.group(1).toLowerCase(Locale.ROOT);
      String code = blocks.group(2);
      Optional<String> issue = blockFailure(language, code, complete);
      if (issue.isPresent()) {
        return issue;
      }
    }
    return Optional.empty();
  }

  private Optional<String> blockFailure(String language, String code, boolean complete) {
    if ("json".equals(language)) {
      try {
        MAPPER.readTree(code);
      } catch (Exception e) {
        return Optional.of("Invalid JSON in a json code block: " + e.getMessage());
      }
    }
    return complete ? sandboxFailure(language, code) : Optional.empty();
  }

  private Optional<String> sandboxFailure(String language, String code) {
    if (sandbox == null) {
      return Optional.empty();
    }
    try {
      return sandbox
          .crashTest(language, code)
          .map(failure -> "Sandbox crash-test failed (" + language + "): " + failure);
    } catch (RuntimeException e) {
      return Optional.of("Sandbox crash-test could not run: " + e.getMessage());
    }
  }
}
