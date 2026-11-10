package com.krizaka.sovkora.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.APIResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.Isolated;
import tools.jackson.databind.JsonNode;

/**
 * Async job lifecycle contract: <b>start a run</b> → the job it dispatched → retrieve.
 *
 * <p>This suite used to POST {@code /api/v1/jobs} with a feature key and an empty payload. That
 * endpoint is gone (ADR-068 §5): a job is produced by a <b>run</b> now, and there is no other way
 * to make one. So the flow starts where a user starts — the composer's button row — and then
 * asserts the same three things about the job that run dispatched: it exists, it is retrievable by
 * id, and something consumes it.
 *
 * <p>Which Studio is used is read off {@code /api/v1/studios/composer} rather than named here: the
 * row is what this deployment can launch from one input, and a suite that hardcoded a key would
 * fail for the wrong reason the day a pack changed.
 *
 * <p>{@link Isolated} because the {@code @AfterAll} purge deletes EVERY job of the shared admin
 * account: run concurrently, it erases jobs sibling ITs (e.g. AmqpContractIT) just submitted,
 * making their consumers fail with "Job does not exist" mid-flight.
 */
@Isolated("purges the shared admin account's job rows")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JobLifecycleIT extends AbstractApiE2eTest {

  private String runId;
  private String jobId;

  @Test
  @Order(1)
  @DisplayName("A run of a one-input Studio is accepted (202) and dispatches exactly one job")
  void startRunAndFindItsJob() {
    String token = authenticate();

    APIResponse composer = request.get("/api/v1/studios/composer", bearer(token));
    assertEquals(200, composer.status());
    JsonNode row = MAPPER.readTree(composer.text());
    assertTrue(row.isArray() && row.size() > 0, "The composer row must offer at least one Studio");

    JsonNode studio = null;
    for (JsonNode candidate : row) {
      // A TEXT input is one this suite can fill; an ASSET input would need an upload first, which
      // StudioLifecycleIT covers.
      if (candidate.path("available").asBoolean()
          && "TEXT".equals(candidate.path("inputKind").asString())) {
        studio = candidate;
        break;
      }
    }
    assertNotNull(studio, "No available Studio in the composer row takes a text input");

    String studioKey = studio.path("studioKey").asString();
    String inputKey = studio.path("inputKey").asString();
    APIResponse started =
        request.post(
            "/api/v1/studios/" + studioKey + "/runs",
            bearerJson(
                token, "{\"inputs\":{\"" + inputKey + "\":\"an end-to-end contract run\"}}"));
    assertEquals(202, started.status(), "Starting a run must return 202 Accepted");

    JsonNode run = MAPPER.readTree(started.text());
    runId = run.path("id").asString();
    assertFalse(runId.isBlank(), "The accepted run must carry an id");

    // The job the run dispatched. It is the run that knows about it — which is the property M3
    // bought: no job exists that no run asked for.
    jobId = awaitDispatchedJob(token, runId);
    assertNotNull(jobId, "The run must dispatch a job within the wait window");
  }

  /** The jobId of the run's first step, once the saga has stamped it. */
  private String awaitDispatchedJob(String token, String run) {
    for (int attempt = 0; attempt < 20; attempt++) {
      APIResponse detail = request.get("/api/v1/studios/runs/" + run, bearer(token));
      assertEquals(200, detail.status(), "The started run must be retrievable");
      JsonNode steps = MAPPER.readTree(detail.text()).path("steps");
      if (steps.isArray() && steps.size() > 0) {
        String dispatched = steps.get(0).path("jobId").asString("");
        if (!dispatched.isBlank()) {
          return dispatched;
        }
      }
      sleepBriefly();
    }
    return null;
  }

  @Test
  @Order(2)
  @DisplayName("GET /api/v1/jobs/{id} returns the job the run dispatched")
  void retrieveJob() {
    String token = authenticate();
    APIResponse job = request.get("/api/v1/jobs/" + jobId, bearer(token));
    assertEquals(200, job.status(), "The dispatched job must be retrievable by id");
    assertEquals(
        jobId, MAPPER.readTree(job.text()).path("id").asString(), "Retrieved job id must match");
  }

  @Test
  @Order(3)
  @DisplayName("A dispatched job is EXECUTED, and never fails on the job service's own credentials")
  void jobLeavesPendingAndNotOnAnAuthFailure() {
    String token = authenticate();

    // The gap this closes. Until now this suite asserted a job was accepted (202) and retrievable,
    // and stopped there — both true of a job that is never executed. The job service called
    // identity's /internal/v1/users with NO Authorization header, so every job needing its actor's
    // context died with a bare 401 the moment a worker picked it up, and the suite stayed green
    // through all of it. The defect was found by running a Studio by hand.
    String status = "PENDING";
    String error = "";
    for (int attempt = 0; attempt < 40 && "PENDING".equals(status); attempt++) {
      APIResponse job = request.get("/api/v1/jobs/" + jobId, bearer(token));
      assertEquals(200, job.status());
      JsonNode body = MAPPER.readTree(job.text());
      status = body.path("status").asString();
      error = body.path("error").asString("");
      if ("PENDING".equals(status)) {
        sleepBriefly();
      }
    }

    assertNotEquals(
        "PENDING",
        status,
        "A dispatched job must be picked up and executed. Still PENDING means nothing consumed it.");

    // Deliberately NOT asserting COMPLETED: whether a generation succeeds depends on which models
    // are pulled on this machine, and a suite that fails because a model is missing teaches people
    // to ignore it. What must never happen is a job dying on the PLATFORM's own credentials — that
    // is a wiring defect, it is identical on every machine, and it is what went unnoticed.
    String failure = error.toLowerCase(java.util.Locale.ROOT);
    assertFalse(
        failure.contains("401")
            || failure.contains("unauthorized")
            || failure.contains("forbidden"),
        "A job must never fail on the job service's own credentials; got: " + error);
  }

  /**
   * Polls rather than waits on a signal: the job plane is asynchronous and has no callback here.
   */
  private static void sleepBriefly() {
    try {
      Thread.sleep(
          500); // NOSONAR java:S2925 - shared helper reused in unrelated loops; inline Awaitility
      // would duplicate the full poll logic twice
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for the job to be executed");
    }
  }

  @AfterAll
  void purgeJobs() {
    // Best-effort cleanup of E2E job rows; the base closeApi() still disposes the context after.
    try {
      request.post("/api/v1/jobs/purge", bearer(authenticate()));
    } catch (RuntimeException ignored) {
      // teardown is best-effort
    }
  }
}
