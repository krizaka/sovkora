package com.krizaka.sovkora.automationservice.infrastructure.adapter.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.automationservice.application.service.RunOutcomeService;
import com.krizaka.sovkora.automationservice.support.AutomationServiceIT;
import com.krizaka.sovkora.automationservice.support.TestTokens;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** The run history: newest first, scoped by owner, with the webhook body in the detail only. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RunHistoryIT extends AutomationServiceIT {

  @LocalServerPort private int port;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private RunOutcomeService outcomes;

  private String owner;
  private String automationId;

  @BeforeEach
  void anAutomation() {
    owner = "history-" + UUID.randomUUID();
    automationId =
        json.readTree(
                call(
                        owner,
                        HttpMethod.POST,
                        "/api/v1/automations",
                        "{\"name\":\"Lease check\",\"studioKey\":\"compliance-check\"}")
                    .getBody())
            .get("id")
            .asString();
  }

  private ResponseEntity<String> call(String as, HttpMethod method, String path, String body) {
    RestClient.RequestBodySpec spec =
        RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultStatusHandler(status -> true, (request, response) -> {})
            .build()
            .method(method)
            .uri(path)
            .header("Authorization", "Bearer " + TestTokens.session(as, "ROLE_USER"))
            .contentType(MediaType.APPLICATION_JSON);
    return (body == null ? spec : spec.body(body)).retrieve().toEntity(String.class);
  }

  private UUID run(String status, String offset, String payload) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO automation_run (id, automation_id, trigger_kind, status, started_at, payload, payload_bytes, studio_run_id)"
            + " VALUES (?::uuid, ?::uuid, 'WEBHOOK', ?::automation_run_status, now() - ?::interval, ?::jsonb, ?, gen_random_uuid())",
        id.toString(),
        automationId,
        status,
        offset,
        payload,
        payload == null ? null : payload.length());
    return id;
  }

  @Test
  @DisplayName("the history of an automation lists its runs newest first, without the payload")
  void listsNewestFirst() {
    UUID oldest = run("SUCCEEDED", "3 hours", "{\"a\":1}");
    UUID newest = run("RUNNING", "1 minute", "{\"b\":2}");
    UUID middle = run("FAILED", "1 hour", null);

    JsonNode runs =
        json.readTree(
            call(owner, HttpMethod.GET, "/api/v1/automations/" + automationId + "/runs", null)
                .getBody());

    assertThat(runs.size()).isEqualTo(3);
    assertThat(runs.get(0).get("id").asString()).isEqualTo(newest.toString());
    assertThat(runs.get(1).get("id").asString()).isEqualTo(middle.toString());
    assertThat(runs.get(2).get("id").asString()).isEqualTo(oldest.toString());
    assertThat(runs.get(0).get("status").asString()).isEqualTo("running");
    assertThat(runs.get(0).get("trigger").asString()).isEqualTo("webhook");
    assertThat(runs.get(0).get("automationName").asString()).isEqualTo("Lease check");
    assertThat(runs.get(0).has("payload")).as("the list never carries the payload").isFalse();
  }

  @Test
  @DisplayName("one run carries the webhook body it was started with")
  void detail() {
    UUID id = run("SUCCEEDED", "1 minute", "{\"order\":42}");
    JsonNode detail =
        json.readTree(
            call(owner, HttpMethod.GET, "/api/v1/automations/runs/" + id, null).getBody());
    assertThat(detail.get("payload").get("order").asInt()).isEqualTo(42);
    assertThat(detail.get("studioRunId").asString()).isNotBlank();
  }

  @Test
  @DisplayName("all the owner's runs, across automations, and only theirs")
  void allRunsAndTenancy() {
    run("SUCCEEDED", "1 minute", null);
    String other = "history-other-" + UUID.randomUUID();
    String otherAutomation =
        json.readTree(
                call(
                        other,
                        HttpMethod.POST,
                        "/api/v1/automations",
                        "{\"name\":\"Other\",\"studioKey\":\"compliance-check\"}")
                    .getBody())
            .get("id")
            .asString();
    jdbc.update(
        "INSERT INTO automation_run (automation_id, trigger_kind, status) VALUES (?::uuid, 'WEBHOOK', 'QUEUED')",
        otherAutomation);

    JsonNode mine =
        json.readTree(call(owner, HttpMethod.GET, "/api/v1/automations/runs", null).getBody());
    assertThat(mine.size()).isEqualTo(1);

    assertThat(
            call(owner, HttpMethod.GET, "/api/v1/automations/" + otherAutomation + "/runs", null)
                .getStatusCode()
                .value())
        .as("another owner's automation is not found")
        .isEqualTo(404);
    UUID theirRun =
        jdbc.queryForObject(
            "SELECT id FROM automation_run WHERE automation_id = ?::uuid",
            UUID.class,
            otherAutomation);
    assertThat(
            call(owner, HttpMethod.GET, "/api/v1/automations/runs/" + theirRun, null)
                .getStatusCode()
                .value())
        .isEqualTo(404);
  }

  @Test
  @DisplayName("the page is bounded")
  void bounded() {
    for (int i = 0; i < 5; i++) {
      run("SUCCEEDED", i + " minutes", null);
    }
    assertThat(
            json.readTree(
                    call(
                            owner,
                            HttpMethod.GET,
                            "/api/v1/automations/" + automationId + "/runs?limit=2",
                            null)
                        .getBody())
                .size())
        .isEqualTo(2);
  }

  @Test
  @DisplayName("the end of the Studio run closes the run: succeeded, or failed with a stable code")
  void outcomes() {
    UUID ok = run("RUNNING", "1 minute", null);
    UUID ko = run("RUNNING", "1 minute", null);
    outcomes.studioRunEnded(studioRunOf(ok), true);
    outcomes.studioRunEnded(studioRunOf(ko), false);

    assertThat(status(ok)).isEqualTo("succeeded");
    assertThat(status(ko)).isEqualTo("failed");
    assertThat(
            json.readTree(
                    call(owner, HttpMethod.GET, "/api/v1/automations/runs/" + ko, null).getBody())
                .get("errorCode")
                .asString())
        .isEqualTo("studio_run_failed");
    outcomes.studioRunEnded(studioRunOf(ok), false);
    assertThat(status(ok)).as("an ended run does not change again").isEqualTo("succeeded");
  }

  private UUID studioRunOf(UUID runId) {
    return jdbc.queryForObject(
        "SELECT studio_run_id FROM automation_run WHERE id = ?::uuid",
        UUID.class,
        runId.toString());
  }

  private String status(UUID runId) {
    return json.readTree(
            call(owner, HttpMethod.GET, "/api/v1/automations/runs/" + runId, null).getBody())
        .get("status")
        .asString();
  }
}
