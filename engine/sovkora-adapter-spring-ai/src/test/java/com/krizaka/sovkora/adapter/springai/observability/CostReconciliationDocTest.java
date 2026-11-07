package com.krizaka.sovkora.adapter.springai.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The reconciliation query of the cost dashboard (ADR-094 slice 3, #171) names the meters and tags
 * the engine really emits and the table billing really writes: a rename here fails this test before
 * the dashboard silently reads nothing.
 */
class CostReconciliationDocTest {

  private static String doc() throws Exception {
    Path dir = Path.of("").toAbsolutePath();
    while (dir != null && !Files.exists(dir.resolve("docs/cost-reconciliation.md"))) {
      dir = dir.getParent();
    }
    return Files.readString(dir.resolve("docs/cost-reconciliation.md"));
  }

  @Test
  void theQueryNamesWhatTheEngineEmits() throws Exception {
    String text = doc();

    assertThat(text)
        .contains("gen_ai.client.operation")
        .contains("gen_ai.client.token.usage")
        .contains("gen_ai.request.model")
        .contains("gen_ai.token.type")
        .contains(FeatureTagObservationFilter.TAG)
        .contains("usage_event");
  }

  @Test
  void theDocPromisesNoContentInTelemetry() throws Exception {
    assertThat(doc()).contains("No prompt, answer, input text or tool argument is a tag");
  }
}
