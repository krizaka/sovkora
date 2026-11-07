package com.krizaka.sovkora.automationservice.infrastructure.adapter.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.automationservice.domain.model.WebhookSecret;
import com.krizaka.sovkora.automationservice.domain.model.WebhookSignature;
import com.krizaka.sovkora.automationservice.support.AutomationServiceIT;
import com.krizaka.sovkora.automationservice.support.StudioStub;
import com.krizaka.sovkora.automationservice.support.TestTokens;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The signed webhook on the real servlet stack, the real database (pool of one), a real keyring and
 * a Studio stub: a valid signature starts a run; a bad one, a stale one, a replay, a flood and an
 * oversized body each get their refusal — and nothing is written for a call that fails.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.junit.jupiter.api.extension.ExtendWith(
    org.springframework.boot.test.system.OutputCaptureExtension.class)
class WebhookIngressIT extends AutomationServiceIT {

  private static final byte[] BODY =
      "{\"order\":42,\"note\":\"hello\"}".getBytes(StandardCharsets.UTF_8);

  @LocalServerPort private int port;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;

  private String owner;
  private String automationId;
  private String triggerId;
  private WebhookSecret secret;

  @BeforeEach
  void aWebhookAutomation() {
    STUDIO.reset();
    owner = "ingress-" + UUID.randomUUID();
    automationId =
        json.readTree(
                api(
                        HttpMethod.POST,
                        "/api/v1/automations",
                        "{\"name\":\"Lease check\",\"studioKey\":\"compliance-check\",\"inputs\":{\"jurisdiction\":\"QC\"}}")
                    .getBody())
            .get("id")
            .asString();
    JsonNode trigger =
        json.readTree(
            api(
                    HttpMethod.POST,
                    "/api/v1/automations/" + automationId + "/triggers",
                    "{\"kind\":\"webhook\"}")
                .getBody());
    triggerId = trigger.get("id").asString();
    secret = WebhookSecret.of(trigger.get("secret").asString());
  }

  private RestClient client() {
    return RestClient.builder()
        .baseUrl("http://localhost:" + port)
        .defaultStatusHandler(status -> true, (request, response) -> {})
        .build();
  }

  private ResponseEntity<String> api(HttpMethod method, String path, String body) {
    RestClient.RequestBodySpec spec =
        client()
            .method(method)
            .uri(path)
            .header("Authorization", "Bearer " + TestTokens.session(owner, "ROLE_USER"))
            .contentType(MediaType.APPLICATION_JSON);
    return (body == null ? spec : spec.body(body)).retrieve().toEntity(String.class);
  }

  private ResponseEntity<String> hook(
      String id, String webhookId, long timestamp, String signature, byte[] body) {
    RestClient.RequestBodySpec spec =
        client()
            .post()
            .uri("/hooks/" + id)
            .contentType(MediaType.APPLICATION_JSON)
            .header("webhook-id", webhookId)
            .header("webhook-timestamp", Long.toString(timestamp));
    if (signature != null) {
      spec.header("webhook-signature", signature);
    }
    return spec.body(body).retrieve().toEntity(String.class);
  }

  private ResponseEntity<String> signedHook(String webhookId, byte[] body) {
    long now = Instant.now().getEpochSecond();
    return hook(
        triggerId,
        webhookId,
        now,
        WebhookSignature.sign(secret.key(), webhookId, Long.toString(now), body),
        body);
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
      "a valid signature answers 202 with a run id, and the Studio run is started as the owner")
  void validSignatureStartsARun() throws Exception {
    ResponseEntity<String> response = signedHook("msg_ok_1", BODY);

    assertThat(response.getStatusCode().value()).isEqualTo(202);
    String runId = json.readTree(response.getBody()).get("runId").asString();
    assertThat(json.readTree(response.getBody()).get("status").asString()).isEqualTo("queued");

    eventually(
        () ->
            "RUNNING"
                .equals(
                    jdbc.queryForObject(
                        "SELECT status::text FROM automation_run WHERE id = ?::uuid",
                        String.class,
                        runId)));
    StudioStub.Received sent = STUDIO.received().getFirst();
    assertThat(sent.path()).isEqualTo("/api/v1/studios/compliance-check/runs");
    assertThat(sent.idempotencyKey())
        .as("a crash and a retry cannot start it twice")
        .isEqualTo(runId);
    assertThat(sent.body()).contains("\"jurisdiction\":\"QC\"").doesNotContain("hello");
    assertThat(sent.authorization()).startsWith("Bearer ");
    String claims =
        new String(
            java.util.Base64.getUrlDecoder().decode(sent.authorization().split("\\.")[1]),
            StandardCharsets.UTF_8);
    assertThat(json.readTree(claims).get("sub").asString()).isEqualTo(owner);
    assertThat(
            jdbc.queryForObject(
                "SELECT studio_run_id IS NOT NULL AND webhook_id = 'msg_ok_1' AND payload_bytes = ?"
                    + " FROM automation_run WHERE id = ?::uuid",
                Boolean.class,
                BODY.length,
                runId))
        .isTrue();
  }

  @Test
  @DisplayName(
      "a wrong signature, a tampered body, a stale timestamp and missing headers are all the same 401")
  void unauthenticated() {
    long now = Instant.now().getEpochSecond();
    String good = WebhookSignature.sign(secret.key(), "msg_bad", Long.toString(now), BODY);
    WebhookSecret other = WebhookSecret.generate();

    List<ResponseEntity<String>> refusals =
        List.of(
            hook(
                triggerId,
                "msg_bad",
                now,
                WebhookSignature.sign(other.key(), "msg_bad", Long.toString(now), BODY),
                BODY),
            hook(
                triggerId, "msg_bad", now, good, "{\"order\":43}".getBytes(StandardCharsets.UTF_8)),
            hook(
                triggerId,
                "msg_bad",
                now - 600,
                WebhookSignature.sign(secret.key(), "msg_bad", Long.toString(now - 600), BODY),
                BODY),
            hook(
                triggerId,
                "msg_bad",
                now + 600,
                WebhookSignature.sign(secret.key(), "msg_bad", Long.toString(now + 600), BODY),
                BODY),
            hook(triggerId, "msg_bad", now, null, BODY),
            hook(UUID.randomUUID().toString(), "msg_bad", now, good, BODY),
            hook("not-a-trigger", "msg_bad", now, good, BODY));

    for (ResponseEntity<String> refusal : refusals) {
      assertThat(refusal.getStatusCode().value()).isEqualTo(401);
    }
    JsonNode first = json.readTree(refusals.getFirst().getBody());
    assertThat(first.get("code").asString()).isEqualTo("invalid_webhook_request");
    for (ResponseEntity<String> refusal : refusals) {
      JsonNode problem = json.readTree(refusal.getBody());
      assertThat(problem.get("code")).isEqualTo(first.get("code"));
      assertThat(problem.get("detail"))
          .as("same words whatever the cause")
          .isEqualTo(first.get("detail"));
    }
    assertThat(runs()).as("nothing is written for a call that fails").isZero();
    assertThat(STUDIO.starts()).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM webhook_delivery WHERE trigger_id = ?::uuid",
                Integer.class,
                triggerId))
        .isZero();
  }

  @Test
  @DisplayName("a disabled trigger and a disabled automation answer 401 like any other bad call")
  void disabled() {
    api(
        HttpMethod.PATCH,
        "/api/v1/automations/" + automationId + "/triggers/" + triggerId,
        "{\"enabled\":false}");
    assertThat(signedHook("msg_off_1", BODY).getStatusCode().value()).isEqualTo(401);
    api(
        HttpMethod.PATCH,
        "/api/v1/automations/" + automationId + "/triggers/" + triggerId,
        "{\"enabled\":true}");
    assertThat(signedHook("msg_off_2", BODY).getStatusCode().value()).isEqualTo(202);
    api(HttpMethod.PATCH, "/api/v1/automations/" + automationId, "{\"enabled\":false}");
    assertThat(signedHook("msg_off_3", BODY).getStatusCode().value()).isEqualTo(401);
  }

  @Test
  @DisplayName(
      "a verified replay of a webhook-id is 409 with the first run's id, and starts nothing")
  void replay() {
    ResponseEntity<String> first = signedHook("msg_replay", BODY);
    String runId = json.readTree(first.getBody()).get("runId").asString();

    ResponseEntity<String> again = signedHook("msg_replay", BODY);

    assertThat(again.getStatusCode().value()).isEqualTo(409);
    JsonNode problem = json.readTree(again.getBody());
    assertThat(problem.get("code").asString()).isEqualTo("webhook_replayed");
    assertThat(problem.get("runId").asString()).isEqualTo(runId);
    assertThat(runs()).isEqualTo(1);
  }

  @Test
  @DisplayName("ten parallel deliveries of the same webhook-id: one run, nine conflicts")
  void parallelDuplicates() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(10);
    try {
      List<Future<Integer>> results = new ArrayList<>();
      for (int i = 0; i < 10; i++) {
        results.add(pool.submit(() -> signedHook("msg_race", BODY).getStatusCode().value()));
      }
      int accepted = 0;
      int conflicts = 0;
      for (Future<Integer> result : results) {
        int status = result.get();
        accepted += status == 202 ? 1 : 0;
        conflicts += status == 409 ? 1 : 0;
      }
      assertThat(accepted).isEqualTo(1);
      assertThat(conflicts).isEqualTo(9);
      assertThat(runs()).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  @DisplayName("an oversized body is 413 before anything is read or written")
  void tooLarge() {
    byte[] big = new byte[70_000];
    java.util.Arrays.fill(big, (byte) 'x');
    assertThat(signedHook("msg_big", big).getStatusCode().value()).isEqualTo(413);
    assertThat(runs()).isZero();
  }

  @Test
  @DisplayName("past the per-minute limit of a trigger the answer is 429 with Retry-After")
  void rateLimit() {
    jdbc.update(
        "UPDATE automation_settings SET rules = rules || '{\"webhookRunsPerMinute\":3}'::jsonb WHERE active");
    try {
      int accepted = 0;
      ResponseEntity<String> last = null;
      for (int i = 0; i < 6; i++) {
        last = signedHook("msg_rate_" + i, BODY);
        accepted += last.getStatusCode().value() == 202 ? 1 : 0;
      }
      assertThat(accepted).isEqualTo(3);
      assertThat(last.getStatusCode().value()).isEqualTo(429);
      assertThat(last.getHeaders().getFirst("Retry-After")).isNotNull();
      assertThat(json.readTree(last.getBody()).get("code").asString()).isEqualTo("rate_limited");
    } finally {
      jdbc.update(
          "UPDATE automation_settings SET rules = rules - 'webhookRunsPerMinute' WHERE active");
    }
  }

  @Test
  @DisplayName("an automation that spent its day's runs answers 429 daily_budget_reached")
  void dailyBudget() {
    jdbc.update(
        "UPDATE automation_settings SET rules = rules || '{\"maxRunsPerDay\":2}'::jsonb WHERE active");
    try {
      assertThat(signedHook("msg_day_1", BODY).getStatusCode().value()).isEqualTo(202);
      assertThat(signedHook("msg_day_2", BODY).getStatusCode().value()).isEqualTo(202);
      ResponseEntity<String> third = signedHook("msg_day_3", BODY);
      assertThat(third.getStatusCode().value()).isEqualTo(429);
      assertThat(json.readTree(third.getBody()).get("code").asString())
          .isEqualTo("daily_budget_reached");
      assertThat(Long.parseLong(third.getHeaders().getFirst("Retry-After"))).isPositive();
    } finally {
      jdbc.update(
          "UPDATE automation_settings SET rules = rules || '{\"maxRunsPerDay\":20}'::jsonb WHERE active");
    }
  }

  @Test
  @DisplayName("after a rotation the old secret verifies during the grace and not after it")
  void rotationGrace() {
    WebhookSecret old = secret;
    JsonNode rotated =
        json.readTree(
            api(
                    HttpMethod.POST,
                    "/api/v1/automations/" + automationId + "/triggers/" + triggerId + "/rotate",
                    null)
                .getBody());
    WebhookSecret fresh = WebhookSecret.of(rotated.get("secret").asString());

    secret = old;
    assertThat(signedHook("msg_grace_old", BODY).getStatusCode().value()).isEqualTo(202);
    secret = fresh;
    assertThat(signedHook("msg_grace_new", BODY).getStatusCode().value()).isEqualTo(202);

    jdbc.update(
        "UPDATE automation_trigger SET previous_secret_until = now() - interval '1 minute' WHERE id = ?::uuid",
        triggerId);
    secret = old;
    assertThat(signedHook("msg_grace_after", BODY).getStatusCode().value()).isEqualTo(401);
    secret = fresh;
    assertThat(signedHook("msg_grace_after_new", BODY).getStatusCode().value()).isEqualTo(202);
  }

  @Test
  @DisplayName(
      "the engine's refusal is recorded on the run; the webhook was still authentic and answered 202")
  void engineRefusal() throws Exception {
    STUDIO.answer(402);
    String runId = json.readTree(signedHook("msg_refused", BODY).getBody()).get("runId").asString();
    eventually(
        () ->
            "FAILED"
                .equals(
                    jdbc.queryForObject(
                        "SELECT status::text FROM automation_run WHERE id = ?::uuid",
                        String.class,
                        runId)));
    assertThat(
            jdbc.queryForObject(
                "SELECT error_code FROM automation_run WHERE id = ?::uuid", String.class, runId))
        .isEqualTo("studio_refused_402");
  }

  @Test
  @DisplayName(
      "while the engine is slow no database connection is held: other calls are served meanwhile")
  void noConnectionHeldAcrossTheEngineCall() throws Exception {
    STUDIO.delay(3_000);
    long started = System.currentTimeMillis();
    assertThat(signedHook("msg_slow_1", BODY).getStatusCode().value()).isEqualTo(202);
    assertThat(System.currentTimeMillis() - started)
        .as("the call is answered when the run is recorded, not when the engine answers")
        .isLessThan(2_000);

    started = System.currentTimeMillis();
    assertThat(signedHook("msg_slow_2", BODY).getStatusCode().value()).isEqualTo(202);
    assertThat(api(HttpMethod.GET, "/api/v1/automations", null).getStatusCode().value())
        .isEqualTo(200);
    assertThat(System.currentTimeMillis() - started)
        .as("with a pool of one, a connection held during the Studio call would block these")
        .isLessThan(2_000);
  }

  @Test
  @DisplayName("only POST /hooks/{id} is anonymous: other verbs and paths still need a token")
  void onlyThePostIsOpen() {
    assertThat(
            client()
                .get()
                .uri("/hooks/" + triggerId)
                .retrieve()
                .toEntity(String.class)
                .getStatusCode()
                .value())
        .isEqualTo(401);
    assertThat(
            client()
                .post()
                .uri("/hooks/" + triggerId + "/extra")
                .body(BODY)
                .retrieve()
                .toEntity(String.class)
                .getStatusCode()
                .value())
        .isEqualTo(401);
    assertThat(
            client()
                .get()
                .uri("/api/v1/automations")
                .retrieve()
                .toEntity(String.class)
                .getStatusCode()
                .value())
        .isEqualTo(401);
  }

  @Test
  @DisplayName("the webhook secret and the signature never reach the logs")
  void noSecretInLogs(org.springframework.boot.test.system.CapturedOutput output) {
    signedHook("msg_log", BODY);
    hook(triggerId, "msg_log2", Instant.now().getEpochSecond(), "v1,Zm9vYmFy", BODY);
    assertThat(output.getAll())
        .doesNotContain(secret.reveal())
        .doesNotContain(secret.reveal().substring(6))
        .doesNotContain("Zm9vYmFy")
        .doesNotContain("hello");
  }
}
