package com.krizaka.sovkora.studio.infrastructure.adapter.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.studio.api.domain.model.RunStatus;
import com.krizaka.sovkora.studio.api.domain.model.RunStepStatus;
import com.krizaka.sovkora.studio.application.service.IdempotentRunStartService;
import com.krizaka.sovkora.studio.application.service.RunEventService;
import com.krizaka.sovkora.studio.application.service.StudioRunService;
import com.krizaka.sovkora.studio.domain.model.RunArtefact;
import com.krizaka.sovkora.studio.domain.model.RunDetail;
import com.krizaka.sovkora.studio.domain.model.RunStepView;
import com.krizaka.sovkora.studio.infrastructure.support.OutputContract;
import com.krizaka.sovkora.test.support.architecture.Workspace;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The producer side of the Verikora/Sovkora contract (krizaka-internal#2381): a compliance-check
 * run, rebuilt from a run recorded once on dev (never written by hand), is answered by the real
 * controller and serialized as the service does, and the JSON that comes out must satisfy the
 * schema Verikora reads ({@code contracts/openapi/compliance-check-run.schema.json}). Verikora
 * checks the same file on its side, so a change that breaks it fails here first.
 */
class ComplianceCheckRunContractTest {

  private static final String ACTOR = "550e8400-e29b-41d4-a716-446655440002";
  private static final Jwt ACTOR_JWT =
      Jwt.withTokenValue("t").header("alg", "HS256").subject(ACTOR).build();
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private static JsonNode schema;
  private static JsonNode reportSchema;
  private static JsonNode recorded;
  private static Path schemaFile;

  @BeforeAll
  static void load() throws Exception {
    Path contracts = Workspace.root(Path.of("."), "contracts/").resolve("contracts");
    schemaFile = contracts.resolve("openapi/compliance-check-run.schema.json");
    schema = JSON.readTree(Files.readString(schemaFile));
    reportSchema = schema.path("$defs").path("report");
    recorded =
        JSON.readTree(
                Files.readString(contracts.resolve("recorded-runs/compliance-check-tenant.json")))
            .path("run");
  }

  /** What the service answers for the recorded run: controller, projection and serializer. */
  private static ObjectNode answer() {
    StudioRunService runService = mock(StudioRunService.class);
    UUID id = UUID.fromString(recorded.path("id").asString());
    when(runService.find(id, ACTOR)).thenReturn(Optional.of(recordedRun()));
    StudioRunController controller =
        new StudioRunController(
            runService, mock(IdempotentRunStartService.class), mock(RunEventService.class));
    return (ObjectNode) JSON.readTree(JSON.writeValueAsString(controller.find(id, ACTOR_JWT)));
  }

  private static RunDetail recordedRun() {
    return new RunDetail(
        UUID.fromString(recorded.path("id").asString()),
        UUID.fromString(recorded.path("installationId").asString()),
        recorded.path("studioKey").asString(),
        recorded.path("blueprintVersion").asString(),
        RunStatus.valueOf(recorded.path("status").asString()),
        "hold-1",
        recorded
            .path("outputs")
            .valueStream()
            .map(ComplianceCheckRunContractTest::artefact)
            .toList(),
        null,
        Instant.parse(recorded.path("startedAt").asString()),
        Instant.parse(recorded.path("finishedAt").asString()),
        recorded.path("steps").valueStream().map(ComplianceCheckRunContractTest::step).toList(),
        null,
        null,
        java.math.BigDecimal.ZERO);
  }

  private static RunArtefact artefact(JsonNode output) {
    return new RunArtefact(
        output.path("key").asString(),
        output.path("label").asString(),
        output.path("type").asString(),
        output.path("value").asString(),
        null);
  }

  private static RunStepView step(JsonNode step) {
    return new RunStepView(
        step.path("stepId").asString(),
        step.path("ordinal").asInt(),
        step.path("jobId").asString(),
        RunStepStatus.valueOf(step.path("status").asString()),
        step.path("attempts").asInt(),
        null,
        Instant.parse(step.path("startedAt").asString()),
        null,
        step.path("estimateSamples").asInt());
  }

  private static JsonNode report(ObjectNode run) {
    for (JsonNode output : run.path("outputs")) {
      if ("report".equals(output.path("key").asString())) {
        return JSON.readTree(output.path("value").asString());
      }
    }
    throw new AssertionError("the run carries no report output");
  }

  @Test
  @DisplayName("the recorded run is itself valid: the schema describes a real answer")
  void theRecordingConformsToTheSchema() {
    assertThat(OutputContract.violations(schema, recorded)).isEmpty();
    assertThat(OutputContract.violations(reportSchema, report((ObjectNode) recorded))).isEmpty();
    assertThat(OutputContract.unsupportedKeywords(schema.path("$defs").path("report"))).isEmpty();
  }

  @Test
  @DisplayName("what the service answers for that run, as serialized, satisfies the schema")
  void theServiceAnswerConformsToTheSchema() {
    ObjectNode run = answer();

    assertThat(OutputContract.violations(schema, run)).isEmpty();
    assertThat(OutputContract.violations(reportSchema, report(run))).isEmpty();
    assertThat(report(run).path("findings")).isNotEmpty();
  }

  @Test
  @DisplayName("a renamed or removed field of the run fails the contract")
  void aChangedRunShapeFails() {
    ObjectNode renamed = answer();
    renamed.set("runStatus", renamed.remove("status"));
    assertThat(OutputContract.violations(schema, renamed)).contains("$.status: required");

    ObjectNode removed = answer();
    removed.remove("outputs");
    assertThat(OutputContract.violations(schema, removed)).contains("$.outputs: required");

    ObjectNode retyped = answer();
    retyped.put("id", 42);
    assertThat(OutputContract.violations(schema, retyped)).contains("$.id: must be string");
  }

  @Test
  @DisplayName("a renamed or removed field of a finding or of the report fails the contract")
  void aChangedReportShapeFails() {
    ObjectNode renamed = (ObjectNode) report(answer());
    ObjectNode finding = (ObjectNode) renamed.path("findings").get(0);
    finding.set("reference", finding.remove("citation"));
    assertThat(OutputContract.violations(reportSchema, renamed))
        .contains("$.findings[0].citation: required");

    ObjectNode removedCitation = (ObjectNode) report(answer());
    ((ObjectNode) removedCitation.path("findings").get(0)).remove("citation");
    assertThat(OutputContract.violations(reportSchema, removedCitation))
        .contains("$.findings[0].citation: required");

    ObjectNode sentence = (ObjectNode) report(answer());
    ((ObjectNode) sentence.path("findings").get(0)).put("citation", "C.c.Q., art. 1897");
    assertThat(OutputContract.violations(reportSchema, sentence))
        .contains("$.findings[0].citation: must be object");

    ObjectNode renamedReference = (ObjectNode) report(answer());
    ObjectNode citation = (ObjectNode) renamedReference.path("findings").get(0).path("citation");
    citation.set("article", citation.remove("reference"));
    assertThat(OutputContract.violations(reportSchema, renamedReference))
        .contains("$.findings[0].citation.reference: required");

    ObjectNode flatRuleset = (ObjectNode) report(answer());
    flatRuleset.put("ruleset", "qc-residential-lease");
    assertThat(OutputContract.violations(reportSchema, flatRuleset))
        .contains("$.ruleset: must be object");

    ObjectNode removed = (ObjectNode) report(answer());
    removed.remove("findings");
    assertThat(OutputContract.violations(reportSchema, removed)).contains("$.findings: required");

    ObjectNode quote = (ObjectNode) report(answer());
    ((ObjectNode) quote.path("findings").get(0).path("evidenceSpan")).put("quote", 1);
    assertThat(OutputContract.violations(reportSchema, quote))
        .contains("$.findings[0].evidenceSpan.quote: must be string");
  }

  @Test
  @DisplayName("the schema is the file Verikora keeps: its digest is pinned beside it")
  void theSchemaIsPinnedByItsDigest() throws Exception {
    String digest =
        HexFormat.of()
            .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(schemaFile)));
    String pinned =
        Files.readString(
                schemaFile.resolveSibling("compliance-check-run.schema.sha256"),
                StandardCharsets.UTF_8)
            .strip();

    assertThat(digest)
        .as(
            "the schema changed: update compliance-check-run.schema.sha256 and carry the same file"
                + " to Verikora (its test pins the same digest)")
        .isEqualTo(pinned);
  }

  @Test
  @DisplayName("the recording is a run of the Studio the schema is about")
  void theRecordingIsAComplianceRun() {
    assertThat(recorded.path("studioKey").asString()).isEqualTo("compliance-check");
    assertThat(recorded.path("status").asString()).isEqualTo("SUCCEEDED");
    List<String> keys =
        recorded.path("outputs").valueStream().map(o -> o.path("key").asString()).toList();
    assertThat(keys).contains("report");
    assertThat(recorded.path("blueprintVersion").asString()).startsWith("2.");
    assertThat(report((ObjectNode) recorded).path("contract").asString()).startsWith("2.");
  }
}
