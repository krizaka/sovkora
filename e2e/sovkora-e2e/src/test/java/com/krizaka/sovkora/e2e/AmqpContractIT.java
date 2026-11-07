package com.krizaka.sovkora.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.APIResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * AMQP contract suite (strangler-fig Phase 0): the topic exchanges ARE the API, so this IT freezes
 * them against the live broker + running router.
 *
 * <ul>
 *   <li><b>Topology</b> — exchanges exist with the §6 types; every queue has its {@code
 *       <queue>.dlq}.
 *   <li><b>Provider capture</b> — a live flow is driven through the REST API while a tap queue
 *       records what lands on the exchange; the captured message must carry every key of the
 *       canonical fixture in {@code amqp-contracts/}.
 *   <li><b>Consumer execution</b> — a submitted job leaves PENDING, proving the whole outbox →
 *       relay(messageId) → topic routing → dedup → executor chain.
 * </ul>
 *
 * <p>When a consumer moves to a new service, its expectations move with these fixtures, not with
 * the code (lightweight consumer-driven contracts).
 */
class AmqpContractIT extends AbstractApiE2eTest {

  private static final String JOBS_EXCHANGE = "orazaka.jobs";
  private static final String EVENTS_EXCHANGE = "orazaka.events";
  private static final String DLX_EXCHANGE = "orazaka.dlx";

  private static final List<String> QUEUES_WITH_DLQ =
      List.of(
          "orazaka.jobs.batch",
          "orazaka.jobs.video",
          "orazaka.jobs.rag",
          "orazaka.jobs.interactive",
          "orazaka.jobs.automation",
          "orazaka.events.job-relay",
          "krizaka.notifications.user-events",
          "krizaka.notifications.password-events",
          "krizaka.notifications.requests");

  @Test
  @DisplayName("Exchanges match AGENTS.md §6: orazaka.jobs/events (topic) + orazaka.dlx (direct)")
  void exchangesMatchContract() {
    assertTrue(
        E2eAmqpClient.exchangeIsDeclared(JOBS_EXCHANGE, "topic"),
        "orazaka.jobs must be a durable topic exchange");
    assertTrue(
        E2eAmqpClient.exchangeIsDeclared(EVENTS_EXCHANGE, "topic"),
        "orazaka.events must be a durable topic exchange");
    assertTrue(
        E2eAmqpClient.exchangeIsDeclared(DLX_EXCHANGE, "direct"),
        "orazaka.dlx must be a durable direct exchange");
  }

  @Test
  @DisplayName("Every work queue exists with its <queue>.dlq (AGENTS.md §6)")
  void queuesAndDlqsExist() {
    List<String> missing = new ArrayList<>();
    for (String queue : QUEUES_WITH_DLQ) {
      if (!E2eAmqpClient.queueExists(queue)) {
        missing.add(queue);
      }
      if (!E2eAmqpClient.queueExists(queue + ".dlq")) {
        missing.add(queue + ".dlq");
      }
    }
    assertTrue(missing.isEmpty(), "Missing queues/DLQs: " + missing);
  }

  @Test
  @DisplayName("A run's step dispatch publishes the canonical JobCommand and the consumer runs it")
  void jobCommandContractRoundTrip() throws IOException {
    String tap = E2eAmqpClient.declareTapQueue(JOBS_EXCHANGE, "job.#");
    try {
      String token = authenticate();

      // The producer is the studio outbox since ADR-067, and the only producer since ADR-068:
      // `POST /api/v1/jobs` is gone, so a job command on this exchange has a run behind it.
      APIResponse composer = request.get("/api/v1/studios/composer", bearer(token));
      assertEquals(200, composer.status());
      JsonNode row = MAPPER.readTree(composer.text());
      assertTrue(row.isArray() && row.size() > 0, "The composer row must offer a Studio to run");

      JsonNode studio = null;
      for (JsonNode candidate : row) {
        if (candidate.path("available").asBoolean()
            && "TEXT".equals(candidate.path("inputKind").asString())) {
          studio = candidate;
          break;
        }
      }
      assertNotNull(studio, "No available Studio in the composer row takes a text input");

      APIResponse started =
          request.post(
              "/api/v1/studios/" + studio.path("studioKey").asString() + "/runs",
              bearerJson(
                  token,
                  "{\"inputs\":{\""
                      + studio.path("inputKey").asString()
                      + "\":\"an amqp contract run\"}}"));
      assertEquals(202, started.status(), "Starting a run must return 202 Accepted");
      String runId = MAPPER.readTree(started.text()).path("id").asString();
      assertFalse(runId.isBlank(), "The accepted run must carry an id");

      // Provider side: the outbox relay must publish the canonical JobCommand shape.
      String captured = E2eAmqpClient.pollMessage(tap, 15_000);
      assertNotNull(captured, "The outbox relay must publish the job command within 15s");
      assertCarriesFixtureKeys(captured, "job.command.json");

      // Consumer side: the executor must pick it up — the run's step leaves PENDING.
      String jobId = MAPPER.readTree(captured).path("jobId").asString();
      assertFalse(
          jobId.isBlank(), "The published command must carry the job id the run dispatched");
      String status = awaitStatusChange(token, jobId, 30_000);
      assertNotNull(status, "Job " + jobId + " must leave PENDING within 30s");
    } finally {
      E2eAmqpClient.deleteQueue(tap);
    }
  }

  @Test
  @DisplayName("Registration publishes the canonical evt.user.registered event")
  void userRegisteredContractShapeFrozen() throws IOException {
    String tap = E2eAmqpClient.declareTapQueue(EVENTS_EXCHANGE, "evt.user.*");
    try {
      String unique = UUID.randomUUID().toString().substring(0, 8);
      APIResponse register =
          request.post(
              "/api/v1/auth/register",
              com.microsoft.playwright.options.RequestOptions.create()
                  .setHeader("Content-Type", "application/json")
                  .setData(
                      "{\"username\":\"amqp-contract-"
                          + unique
                          + "\",\"email\":\"amqp-contract-"
                          + unique
                          + "@orazaka.com\",\"password\":\"SecurePass123!\",\"language\":\"en\"}"));
      assertTrue(
          register.status() == 200 || register.status() == 201,
          "Registration must succeed, got " + register.status());

      String captured = E2eAmqpClient.pollMessage(tap, 15_000);
      assertNotNull(captured, "evt.user.registered must be published within 15s");
      assertCarriesFixtureKeys(captured, "evt.user.registered.json");
    } finally {
      E2eAmqpClient.deleteQueue(tap);
    }
  }

  /** Asserts the captured message carries every (non-meta) top-level key of the fixture. */
  private void assertCarriesFixtureKeys(String capturedJson, String fixtureName) {
    JsonNode fixture = readFixture(fixtureName);
    JsonNode captured = MAPPER.readTree(capturedJson);
    List<String> missing = new ArrayList<>();
    fixture.propertyNames().stream()
        .filter(field -> !field.startsWith("_"))
        .forEach(
            field -> {
              if (!captured.has(field)) {
                missing.add(field);
              }
            });
    assertTrue(
        missing.isEmpty(),
        "Captured message misses contract keys "
            + missing
            + " of "
            + fixtureName
            + " — payload: "
            + capturedJson);
  }

  private JsonNode readFixture(String name) {
    try (var in = getClass().getResourceAsStream("/amqp-contracts/" + name)) {
      assertNotNull(in, "Missing contract fixture: " + name);
      return MAPPER.readTree(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Polls the job until its status leaves PENDING; returns the new status or null on timeout. */
  private String awaitStatusChange(String token, String jobId, long timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      APIResponse job = request.get("/api/v1/jobs/" + jobId, bearer(token));
      if (job.status() == 200) {
        String status = MAPPER.readTree(job.text()).path("status").asString();
        if (!"PENDING".equals(status)) {
          return status;
        }
      }
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return null;
      }
    }
    return null;
  }
}
