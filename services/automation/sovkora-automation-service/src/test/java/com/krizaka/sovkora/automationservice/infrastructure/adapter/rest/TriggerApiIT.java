package com.krizaka.sovkora.automationservice.infrastructure.adapter.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.automationservice.support.AutomationServiceIT;
import com.krizaka.sovkora.automationservice.support.TestTokens;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Triggers end to end: the API, the sealing, the database — and that a secret is never logged. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class TriggerApiIT extends AutomationServiceIT {

  private static final String ALICE = "trigger-alice";
  private static final String BOB = "trigger-bob";

  @LocalServerPort private int port;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;

  private ResponseEntity<String> call(HttpMethod method, String path, String owner, String body) {
    RestClient.RequestBodySpec spec =
        RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultStatusHandler(status -> true, (request, response) -> {})
            .build()
            .method(method)
            .uri(path)
            .contentType(MediaType.APPLICATION_JSON);
    if (owner != null) {
      spec.header("Authorization", "Bearer " + TestTokens.session(owner, "ROLE_USER"));
    }
    return (body == null ? spec : spec.body(body)).retrieve().toEntity(String.class);
  }

  private String automation(String owner) {
    ResponseEntity<String> created =
        call(
            HttpMethod.POST,
            "/api/v1/automations",
            owner,
            "{\"name\":\"Lease check\",\"studioKey\":\"compliance-check\"}");
    assertThat(created.getStatusCode().value()).isEqualTo(201);
    return json.readTree(created.getBody()).get("id").asString();
  }

  private JsonNode webhook(String owner, String automationId) {
    ResponseEntity<String> created =
        call(
            HttpMethod.POST,
            "/api/v1/automations/" + automationId + "/triggers",
            owner,
            "{\"kind\":\"webhook\"}");
    assertThat(created.getStatusCode().value()).isEqualTo(201);
    return json.readTree(created.getBody());
  }

  @Test
  @DisplayName(
      "a webhook trigger shows its secret once, a URL to call, and stores the secret sealed")
  void webhookSecretIsShownOnceAndStoredSealed() {
    String automation = automation(ALICE);
    JsonNode trigger = webhook(ALICE, automation);

    String secret = trigger.get("secret").asString();
    assertThat(secret).startsWith("whsec_");
    assertThat(trigger.get("webhookUrl").asString())
        .endsWith("/hooks/" + trigger.get("id").asString());
    assertThat(trigger.get("secretHint").asString())
        .isEqualTo(secret.substring(secret.length() - 4));

    String stored =
        jdbc.queryForObject(
            "SELECT secret_sealed FROM automation_trigger WHERE id = ?::uuid",
            String.class,
            trigger.get("id").asString());
    assertThat(stored).startsWith("v1.").doesNotContain(secret).doesNotContain(secret.substring(6));

    JsonNode listed =
        json.readTree(
            call(HttpMethod.GET, "/api/v1/automations/" + automation + "/triggers", ALICE, null)
                .getBody());
    assertThat(listed.size()).isEqualTo(1);
    assertThat(listed.get(0).has("secret")).as("a read never returns the secret").isFalse();
    assertThat(listed.get(0).get("secretHint").asString()).isNotBlank();
  }

  @Test
  @DisplayName("rotation gives a new secret and keeps the old one sealed for the grace")
  void rotation() {
    String automation = automation(ALICE);
    JsonNode trigger = webhook(ALICE, automation);
    String id = trigger.get("id").asString();

    ResponseEntity<String> rotated =
        call(
            HttpMethod.POST,
            "/api/v1/automations/" + automation + "/triggers/" + id + "/rotate",
            ALICE,
            null);
    assertThat(rotated.getStatusCode().value()).isEqualTo(200);
    JsonNode body = json.readTree(rotated.getBody());
    assertThat(body.get("secret").asString()).isNotEqualTo(trigger.get("secret").asString());

    assertThat(
            jdbc.queryForObject(
                "SELECT previous_secret_sealed IS NOT NULL AND previous_secret_until > now()"
                    + " FROM automation_trigger WHERE id = ?::uuid",
                Boolean.class,
                id))
        .isTrue();
  }

  @Test
  @DisplayName("a schedule trigger keeps its slot and gets a next fire time in the future")
  void scheduleTrigger() {
    String automation = automation(ALICE);
    ResponseEntity<String> created =
        call(
            HttpMethod.POST,
            "/api/v1/automations/" + automation + "/triggers",
            ALICE,
            "{\"kind\":\"schedule\",\"time\":\"08:00\",\"zone\":\"America/Toronto\","
                + "\"days\":[\"MON\",\"TUE\"],\"catchUp\":\"skip\"}");
    assertThat(created.getStatusCode().value()).isEqualTo(201);
    JsonNode trigger = json.readTree(created.getBody());
    assertThat(trigger.get("kind").asString()).isEqualTo("schedule");
    assertThat(trigger.get("schedule").get("time").asString()).isEqualTo("08:00");
    assertThat(trigger.get("schedule").get("days").size()).isEqualTo(2);
    assertThat(trigger.get("catchUp").asString()).isEqualTo("skip");
    assertThat(trigger.has("secret")).isFalse();
    assertThat(java.time.Instant.parse(trigger.get("nextFireAt").asString()))
        .isAfter(java.time.Instant.now());

    ResponseEntity<String> off =
        call(
            HttpMethod.PATCH,
            "/api/v1/automations/" + automation + "/triggers/" + trigger.get("id").asString(),
            ALICE,
            "{\"enabled\":false}");
    JsonNode disabled = json.readTree(off.getBody());
    assertThat(disabled.get("enabled").asBoolean()).isFalse();
    assertThat(disabled.has("nextFireAt")).as("a disabled schedule has no next slot").isFalse();
  }

  @Test
  @DisplayName("bad triggers are 400 problems: unknown kind, bad zone, schedule without a time")
  void validation() {
    String automation = automation(ALICE);
    for (String bad :
        new String[] {
          "{\"kind\":\"email\"}",
          "{\"kind\":\"schedule\",\"time\":\"08:00\",\"zone\":\"Mars/Olympus\"}",
          "{\"kind\":\"schedule\"}",
          "{}"
        }) {
      ResponseEntity<String> response =
          call(HttpMethod.POST, "/api/v1/automations/" + automation + "/triggers", ALICE, bad);
      assertThat(response.getStatusCode().value()).as(bad).isEqualTo(400);
    }
  }

  @Test
  @DisplayName("another owner can neither see, change, rotate nor remove a trigger: always 404")
  void tenancy() {
    String automation = automation(ALICE);
    String trigger = webhook(ALICE, automation).get("id").asString();
    String base = "/api/v1/automations/" + automation + "/triggers";
    assertThat(call(HttpMethod.GET, base, BOB, null).getStatusCode().value()).isEqualTo(404);
    assertThat(call(HttpMethod.POST, base, BOB, "{\"kind\":\"webhook\"}").getStatusCode().value())
        .isEqualTo(404);
    assertThat(
            call(HttpMethod.POST, base + "/" + trigger + "/rotate", BOB, null)
                .getStatusCode()
                .value())
        .isEqualTo(404);
    assertThat(
            call(HttpMethod.PATCH, base + "/" + trigger, BOB, "{\"enabled\":false}")
                .getStatusCode()
                .value())
        .isEqualTo(404);
    assertThat(call(HttpMethod.DELETE, base + "/" + trigger, BOB, null).getStatusCode().value())
        .isEqualTo(404);
    assertThat(call(HttpMethod.GET, base, ALICE, null).getStatusCode().value()).isEqualTo(200);
  }

  @Test
  @DisplayName("an automation holds a bounded number of triggers")
  void quota() {
    String automation = automation("trigger-quota");
    Integer limit =
        jdbc.queryForObject(
            "SELECT COALESCE((rules ->> 'maxTriggersPerAutomation')::int, 5)"
                + " FROM automation_settings WHERE active",
            Integer.class);
    for (int i = 0; i < limit; i++) {
      webhook("trigger-quota", automation);
    }
    ResponseEntity<String> over =
        call(
            HttpMethod.POST,
            "/api/v1/automations/" + automation + "/triggers",
            "trigger-quota",
            "{\"kind\":\"webhook\"}");
    assertThat(over.getStatusCode().value()).isEqualTo(409);
    assertThat(json.readTree(over.getBody()).get("code").asString()).isEqualTo("too_many_triggers");
  }

  @Test
  @DisplayName("deleting an automation takes its triggers with it")
  void cascade() {
    String automation = automation("trigger-cascade");
    String trigger = webhook("trigger-cascade", automation).get("id").asString();
    assertThat(
            call(HttpMethod.DELETE, "/api/v1/automations/" + automation, "trigger-cascade", null)
                .getStatusCode()
                .value())
        .isEqualTo(204);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM automation_trigger WHERE id = ?::uuid",
                Integer.class,
                trigger))
        .isZero();
  }

  @Test
  @DisplayName("a secret never reaches the logs, whatever the flow")
  void secretsAreNeverLogged(CapturedOutput output) {
    String automation = automation("trigger-logs");
    JsonNode created = webhook("trigger-logs", automation);
    String id = created.get("id").asString();
    JsonNode rotated =
        json.readTree(
            call(
                    HttpMethod.POST,
                    "/api/v1/automations/" + automation + "/triggers/" + id + "/rotate",
                    "trigger-logs",
                    null)
                .getBody());
    call(
        HttpMethod.DELETE,
        "/api/v1/automations/" + automation + "/triggers/" + id,
        "trigger-logs",
        null);
    call(
        HttpMethod.GET,
        "/api/v1/automations/" + UUID.randomUUID() + "/triggers",
        "trigger-logs",
        null);

    String logs = output.getAll();
    for (String secret :
        new String[] {created.get("secret").asString(), rotated.get("secret").asString()}) {
      assertThat(logs).doesNotContain(secret).doesNotContain(secret.substring("whsec_".length()));
    }
  }
}
