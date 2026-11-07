package com.krizaka.sovkora.automationservice.infrastructure.adapter.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.automationservice.support.AutomationServiceIT;
import com.krizaka.sovkora.automationservice.support.TestTokens;
import java.util.Map;
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
 * The automation API end to end on the real servlet stack: the security chain, the controller, the
 * JDBC adapters and the bootstrap SQL. Sovkora#195, ADR-084 slice 1.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AutomationApiIT extends AutomationServiceIT {

  private static final String ALICE = "alice-0001";
  private static final String BOB = "bob-0002";
  private static final String BODY =
      "{\"name\":\"Lease check on request\",\"studioKey\":\"compliance-check\","
          + "\"inputs\":{\"jurisdiction\":\"QC\"}}";

  @LocalServerPort private int port;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;

  private RestClient client() {
    return RestClient.builder()
        .baseUrl("http://localhost:" + port)
        .defaultStatusHandler(status -> true, (request, response) -> {})
        .build();
  }

  private ResponseEntity<String> call(HttpMethod method, String path, String token, String body) {
    RestClient.RequestBodySpec spec =
        client().method(method).uri(path).contentType(MediaType.APPLICATION_JSON);
    if (token != null) {
      spec.header("Authorization", "Bearer " + token);
    }
    return (body == null ? spec : spec.body(body)).retrieve().toEntity(String.class);
  }

  private JsonNode created(String owner, String body) {
    ResponseEntity<String> response =
        call(HttpMethod.POST, "/api/v1/automations", TestTokens.session(owner, "ROLE_USER"), body);
    assertThat(response.getStatusCode().value()).isEqualTo(201);
    return json.readTree(response.getBody());
  }

  @Test
  @DisplayName("the service starts with its security chain, and health is answered without a token")
  void healthIsOpen() {
    // Open means « not asked for a token »: whether it is UP depends on the broker, which CI does
    // not have.
    assertThat(call(HttpMethod.GET, "/actuator/health", null, null).getStatusCode().value())
        .isIn(200, 503);
  }

  @Test
  @DisplayName("no token, no automations: 401 on every verb of the API")
  void theApiRequiresAToken() {
    assertThat(call(HttpMethod.GET, "/api/v1/automations", null, null).getStatusCode().value())
        .isEqualTo(401);
    assertThat(call(HttpMethod.POST, "/api/v1/automations", null, BODY).getStatusCode().value())
        .isEqualTo(401);
    assertThat(
            call(
                    HttpMethod.DELETE,
                    "/api/v1/automations/" + java.util.UUID.randomUUID(),
                    null,
                    null)
                .getStatusCode()
                .value())
        .isEqualTo(401);
  }

  @Test
  @DisplayName("a token signed with another secret is refused")
  void aForgedTokenIsRefused() {
    String forged = TestTokens.session(ALICE, "ROLE_USER");
    String tampered = forged.substring(0, forged.length() - 4) + "AAAA";
    assertThat(call(HttpMethod.GET, "/api/v1/automations", tampered, null).getStatusCode().value())
        .isEqualTo(401);
  }

  @Test
  @DisplayName("/internal/v1 needs the SERVICE authority: a user's token is refused")
  void internalSurfaceIsForServices() {
    assertThat(
            call(
                    HttpMethod.GET,
                    "/internal/v1/anything",
                    TestTokens.session(ALICE, "ROLE_USER"),
                    null)
                .getStatusCode()
                .value())
        .isEqualTo(403);
  }

  @Test
  @DisplayName("an owner creates, lists, reads, changes and removes an automation")
  void ownerLifecycle() {
    String token = TestTokens.session("lifecycle-owner", "ROLE_USER");
    JsonNode automation = created("lifecycle-owner", BODY);
    String id = automation.get("id").asString();
    assertThat(automation.get("studioKey").asString()).isEqualTo("compliance-check");
    assertThat(automation.get("enabled").asBoolean()).isTrue();
    assertThat(automation.get("inputs").get("jurisdiction").asString()).isEqualTo("QC");
    assertThat(automation.has("ownerId")).as("the caller is the owner; no id echoed").isFalse();

    JsonNode listed =
        json.readTree(call(HttpMethod.GET, "/api/v1/automations", token, null).getBody());
    assertThat(listed.size()).isEqualTo(1);

    ResponseEntity<String> patched =
        call(
            HttpMethod.PATCH,
            "/api/v1/automations/" + id,
            token,
            "{\"enabled\":false,\"name\":\"Paused\"}");
    assertThat(patched.getStatusCode().value()).isEqualTo(200);
    JsonNode after = json.readTree(patched.getBody());
    assertThat(after.get("enabled").asBoolean()).isFalse();
    assertThat(after.get("name").asString()).isEqualTo("Paused");
    assertThat(after.get("inputs").get("jurisdiction").asString()).as("untouched").isEqualTo("QC");

    assertThat(
            call(HttpMethod.DELETE, "/api/v1/automations/" + id, token, null)
                .getStatusCode()
                .value())
        .isEqualTo(204);
    assertThat(
            call(HttpMethod.GET, "/api/v1/automations/" + id, token, null).getStatusCode().value())
        .isEqualTo(404);
  }

  @Test
  @DisplayName("a change of the name alone leaves the state and the inputs as they were")
  void renameOnly() {
    String token = TestTokens.session("rename-owner", "ROLE_USER");
    String id = created("rename-owner", BODY).get("id").asString();
    JsonNode renamed =
        json.readTree(
            call(HttpMethod.PATCH, "/api/v1/automations/" + id, token, "{\"name\":\"Only a name\"}")
                .getBody());
    assertThat(renamed.get("name").asString()).isEqualTo("Only a name");
    assertThat(renamed.get("enabled").asBoolean()).isTrue();
    assertThat(renamed.get("inputs").get("jurisdiction").asString()).isEqualTo("QC");
    JsonNode paused =
        json.readTree(
            call(HttpMethod.PATCH, "/api/v1/automations/" + id, token, "{\"enabled\":false}")
                .getBody());
    assertThat(paused.get("name").asString()).as("untouched").isEqualTo("Only a name");
    assertThat(paused.get("enabled").asBoolean()).isFalse();
  }

  @Test
  @DisplayName("someone else's automation is a 404 on every verb, never a 403")
  void tenancyIsAlwaysScoped() {
    String id = created(ALICE, BODY).get("id").asString();
    String bob = TestTokens.session(BOB, "ROLE_USER");
    assertThat(call(HttpMethod.GET, "/api/v1/automations/" + id, bob, null).getStatusCode().value())
        .isEqualTo(404);
    assertThat(
            call(HttpMethod.PATCH, "/api/v1/automations/" + id, bob, "{\"enabled\":false}")
                .getStatusCode()
                .value())
        .isEqualTo(404);
    assertThat(
            call(HttpMethod.DELETE, "/api/v1/automations/" + id, bob, null).getStatusCode().value())
        .isEqualTo(404);
    JsonNode bobs = json.readTree(call(HttpMethod.GET, "/api/v1/automations", bob, null).getBody());
    assertThat(bobs.size()).as("Bob lists none of Alice's").isZero();
    assertThat(
            call(
                    HttpMethod.GET,
                    "/api/v1/automations/" + id,
                    TestTokens.session(ALICE, "ROLE_USER"),
                    null)
                .getStatusCode()
                .value())
        .as("and Alice still has hers")
        .isEqualTo(200);
  }

  @Test
  @DisplayName("a bad request is a 400 problem with a stable code, and creates nothing")
  void validation() {
    String token = TestTokens.session("validation-owner", "ROLE_USER");
    for (String bad :
        new String[] {
          "{\"name\":\"\",\"studioKey\":\"compliance-check\"}",
          "{\"name\":\"x\",\"studioKey\":\"Not A Key\"}",
          "{\"name\":\"x\"}",
          "not json"
        }) {
      ResponseEntity<String> response = call(HttpMethod.POST, "/api/v1/automations", token, bad);
      assertThat(response.getStatusCode().value()).as(bad).isEqualTo(400);
      assertThat(json.readTree(response.getBody()).get("code").asString())
          .isEqualTo("invalid_request");
    }
    assertThat(
            json.readTree(call(HttpMethod.GET, "/api/v1/automations", token, null).getBody())
                .size())
        .isZero();
  }

  @Test
  @DisplayName("inputs larger than the rule set allows are refused")
  void inputsAreBounded() {
    String big = "x".repeat(70_000);
    String body =
        "{\"name\":\"big\",\"studioKey\":\"compliance-check\",\"inputs\":{\"a\":\"" + big + "\"}}";
    assertThat(
            call(
                    HttpMethod.POST,
                    "/api/v1/automations",
                    TestTokens.session("big-owner", "ROLE_USER"),
                    body)
                .getStatusCode()
                .value())
        .isEqualTo(400);
  }

  @Test
  @DisplayName("an owner cannot exceed the rule set's number of automations")
  void quota() {
    Integer limit =
        jdbc.queryForObject(
            "SELECT (rules ->> 'maxAutomationsPerUser')::int FROM automation_settings WHERE active",
            Integer.class);
    assertThat(limit).as("the active rule set carries the limit").isNotNull().isPositive();
    String owner = "quota-owner";
    for (int i = 0; i < limit; i++) {
      created(owner, BODY);
    }
    ResponseEntity<String> over =
        call(HttpMethod.POST, "/api/v1/automations", TestTokens.session(owner, "ROLE_USER"), BODY);
    assertThat(over.getStatusCode().value()).isEqualTo(409);
    assertThat(json.readTree(over.getBody()).get("code").asString())
        .isEqualTo("too_many_automations");
  }

  @Test
  @DisplayName("an unreadable stored rule falls back to the default instead of failing the API")
  void unreadableRuleFallsBack() {
    Map<String, Object> before =
        jdbc.queryForMap(
            "SELECT version, rules::text AS rules FROM automation_settings WHERE active");
    try {
      jdbc.update(
          "UPDATE automation_settings SET rules = rules || '{\"maxAutomationsPerUser\":\"lots\"}'::jsonb WHERE active");
      assertThat(
              call(
                      HttpMethod.POST,
                      "/api/v1/automations",
                      TestTokens.session("rule-owner", "ROLE_USER"),
                      BODY)
                  .getStatusCode()
                  .value())
          .isEqualTo(201);
    } finally {
      jdbc.update(
          "UPDATE automation_settings SET rules = ?::jsonb WHERE version = ?",
          before.get("rules"),
          before.get("version"));
    }
  }
}
