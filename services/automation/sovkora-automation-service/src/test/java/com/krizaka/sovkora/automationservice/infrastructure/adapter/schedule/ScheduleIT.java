package com.krizaka.sovkora.automationservice.infrastructure.adapter.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.automationservice.application.service.RunSweepService;
import com.krizaka.sovkora.automationservice.application.service.ScheduleClaimService;
import com.krizaka.sovkora.automationservice.support.AutomationServiceIT;
import com.krizaka.sovkora.automationservice.support.TestTokens;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import tools.jackson.databind.ObjectMapper;

/**
 * Schedules end to end: a slot that has come fires once, catch-up and budgets are the rule set's.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ScheduleIT extends AutomationServiceIT {

  @LocalServerPort private int port;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private RunSweepService sweep;
  @Autowired private ScheduleClaimService claim;

  private String owner;
  private String automationId;

  @BeforeEach
  void anAutomation() {
    STUDIO.reset();
    // Other tests' schedules must not fire in this one's sweeps.
    jdbc.update("UPDATE automation_trigger SET enabled = FALSE WHERE kind = 'SCHEDULE'");
    owner = "schedule-" + UUID.randomUUID();
    automationId =
        json.readTree(
                api(
                        HttpMethod.POST,
                        "/api/v1/automations",
                        "{\"name\":\"Morning check\",\"studioKey\":\"compliance-check\"}")
                    .getBody())
            .get("id")
            .asString();
  }

  private ResponseEntity<String> api(HttpMethod method, String path, String body) {
    RestClient.RequestBodySpec spec =
        RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultStatusHandler(status -> true, (request, response) -> {})
            .build()
            .method(method)
            .uri(path)
            .header("Authorization", "Bearer " + TestTokens.session(owner, "ROLE_USER"))
            .contentType(MediaType.APPLICATION_JSON);
    return (body == null ? spec : spec.body(body)).retrieve().toEntity(String.class);
  }

  private String schedule(String catchUp) {
    return json.readTree(
            api(
                    HttpMethod.POST,
                    "/api/v1/automations/" + automationId + "/triggers",
                    "{\"kind\":\"schedule\",\"time\":\"08:00\",\"zone\":\"America/Toronto\",\"catchUp\":\""
                        + catchUp
                        + "\"}")
                .getBody())
        .get("id")
        .asString();
  }

  private void due(String triggerId, String ago) {
    jdbc.update(
        "UPDATE automation_trigger SET enabled = TRUE, next_fire_at = now() - ?::interval WHERE id = ?::uuid",
        ago,
        triggerId);
  }

  private int runs() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM automation_run WHERE automation_id = ?::uuid",
        Integer.class,
        automationId);
  }

  private void eventually(Callable<Boolean> condition) throws Exception {
    long deadline = System.currentTimeMillis() + 10_000;
    while (!condition.call()) {
      if (System.currentTimeMillis() > deadline) {
        throw new AssertionError("condition not met within 10 s");
      }
      Thread.sleep(50);
    }
  }

  @Test
  @DisplayName(
      "a slot that has come starts the Studio run once, and the trigger moves to its next slot")
  void firesOnce() throws Exception {
    String trigger = schedule("once");
    due(trigger, "5 seconds");

    sweep.sweep();
    sweep.sweep();

    assertThat(runs()).as("the second sweep finds nothing due").isEqualTo(1);
    eventually(() -> STUDIO.starts() == 1);
    assertThat(
            jdbc.queryForObject(
                "SELECT status::text FROM automation_run WHERE automation_id = ?::uuid",
                String.class,
                automationId))
        .isIn("QUEUED", "RUNNING");
    assertThat(
            jdbc.queryForObject(
                "SELECT trigger_kind::text FROM automation_run WHERE automation_id = ?::uuid",
                String.class,
                automationId))
        .isEqualTo("SCHEDULE");
    Instant next =
        jdbc.queryForObject(
            "SELECT next_fire_at FROM automation_trigger WHERE id = ?::uuid",
            Instant.class,
            trigger);
    assertThat(next).isAfter(Instant.now());
    assertThat(
            jdbc.queryForObject(
                "SELECT last_fired_at IS NOT NULL FROM automation_trigger WHERE id = ?::uuid",
                Boolean.class,
                trigger))
        .isTrue();
  }

  @Test
  @DisplayName(
      "a slot missed for a day runs once under catch-up 'once' and not at all under 'skip'")
  void catchUp() {
    String once = schedule("once");
    due(once, "1 day");
    sweep.sweep();
    assertThat(runs()).isEqualTo(1);

    String skip = schedule("skip");
    jdbc.update("UPDATE automation_trigger SET enabled = FALSE WHERE id = ?::uuid", once);
    due(skip, "1 day");
    sweep.sweep();
    assertThat(runs()).as("the skipped slot starts nothing").isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT next_fire_at > now() FROM automation_trigger WHERE id = ?::uuid",
                Boolean.class,
                skip))
        .as("but the trigger moves on")
        .isTrue();
  }

  @Test
  @DisplayName("a disabled automation or trigger does not fire")
  void disabled() {
    String trigger = schedule("once");
    due(trigger, "5 seconds");
    api(HttpMethod.PATCH, "/api/v1/automations/" + automationId, "{\"enabled\":false}");
    sweep.sweep();
    assertThat(runs()).isZero();
    api(HttpMethod.PATCH, "/api/v1/automations/" + automationId, "{\"enabled\":true}");
    jdbc.update("UPDATE automation_trigger SET enabled = FALSE WHERE id = ?::uuid", trigger);
    sweep.sweep();
    assertThat(runs()).isZero();
  }

  @Test
  @DisplayName("an automation over its day's budget records a skipped run and starts nothing")
  void budget() {
    jdbc.update(
        "UPDATE automation_settings SET rules = rules || '{\"maxRunsPerDay\":1}'::jsonb WHERE active");
    try {
      String trigger = schedule("once");
      due(trigger, "5 seconds");
      sweep.sweep();
      due(trigger, "5 seconds");
      sweep.sweep();
      assertThat(runs()).isEqualTo(2);
      assertThat(
              jdbc.queryForList(
                  "SELECT status::text FROM automation_run WHERE automation_id = ?::uuid ORDER BY started_at",
                  String.class,
                  automationId))
          .containsExactly("QUEUED", "SKIPPED_BUDGET")
          .as("the first may already be RUNNING");
    } catch (AssertionError raced) {
      assertThat(
              jdbc.queryForList(
                  "SELECT status::text FROM automation_run WHERE automation_id = ?::uuid ORDER BY started_at",
                  String.class,
                  automationId))
          .containsExactly("RUNNING", "SKIPPED_BUDGET");
    } finally {
      jdbc.update(
          "UPDATE automation_settings SET rules = rules || '{\"maxRunsPerDay\":20}'::jsonb WHERE active");
    }
  }

  @Test
  @DisplayName("two instances claiming at once fire a slot exactly once (SKIP LOCKED)")
  void concurrentClaims() throws Exception {
    String trigger = schedule("once");
    due(trigger, "5 seconds");
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      CountDownLatch go = new CountDownLatch(1);
      List<Future<List<UUID>>> claims = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        claims.add(
            pool.submit(
                () -> {
                  go.await();
                  return claim.claimDue();
                }));
      }
      go.countDown();
      int total = 0;
      for (Future<List<UUID>> result : claims) {
        total += result.get().size();
      }
      assertThat(total).isEqualTo(1);
      assertThat(runs()).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  @DisplayName("a run recorded but never started is started by the next sweep")
  void startsStaleQueuedRuns() throws Exception {
    UUID runId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO automation_run (id, automation_id, trigger_kind, status, started_at)"
            + " VALUES (?::uuid, ?::uuid, 'SCHEDULE', 'QUEUED', now() - interval '5 minutes')",
        runId.toString(),
        automationId);
    sweep.sweep();
    eventually(
        () ->
            "RUNNING"
                .equals(
                    jdbc.queryForObject(
                        "SELECT status::text FROM automation_run WHERE id = ?::uuid",
                        String.class,
                        runId.toString())));
    assertThat(STUDIO.received().getFirst().idempotencyKey()).isEqualTo(runId.toString());
  }

  @Test
  @DisplayName("a running run whose end event was lost is closed from the Studio run itself")
  void reconcilesLostEvents() throws Exception {
    UUID runId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO automation_run (id, automation_id, trigger_kind, status, studio_run_id, started_at)"
            + " VALUES (?::uuid, ?::uuid, 'SCHEDULE', 'RUNNING', gen_random_uuid(), now() - interval '1 hour')",
        runId.toString(),
        automationId);
    STUDIO.runStatus("SUCCEEDED");
    sweep.sweep();
    assertThat(
            jdbc.queryForObject(
                "SELECT status::text FROM automation_run WHERE id = ?::uuid",
                String.class,
                runId.toString()))
        .isEqualTo("SUCCEEDED");
  }
}
