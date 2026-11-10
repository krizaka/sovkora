package com.krizaka.sovkora.e2e;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import tools.jackson.databind.JsonNode;

/**
 * The Studio marketplace end to end, through the <b>running</b> stack (ADR-034 §15).
 *
 * <p>Black-box on purpose: every call goes browser → BFF → edge → studio service, which is what
 * makes this a quality gate rather than a second unit test. It proves the seam the unit and
 * integration suites cannot — that the edge route exists, that the session token survives the hops,
 * and that the catalogue an actual user sees is the one the seeds describe.
 *
 * <p>What it deliberately does not assert is a finished run: completing one needs the MLX models
 * loaded, and a quality gate that depends on a 20-minute video render is a gate nobody runs. Run
 * <i>acceptance</i> — validated, held, persisted, fanned out — is asserted here; the DAG's advance
 * is proved against the real schema in {@code StudioRunLifecycleIT}.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StudioLifecycleIT extends AbstractApiE2eTest {

  private static final String FREE_STUDIO = "trade-showcase";
  private static final String PAID_STUDIO = "realestate-reels";

  private String installationId;

  @Test
  @Order(1)
  @DisplayName("GET /api/v1/studios routes through the edge and lists the seeded catalogue")
  void catalogueIsReachableThroughTheEdge() {
    String token = authenticate();

    APIResponse response = request.get("/api/v1/studios", bearer(token));

    assertEquals(200, response.status(), "the edge must route /api/v1/studios to :8096");
    JsonNode catalogue = MAPPER.readTree(response.text());
    assertTrue(
        catalogue.isArray() && !catalogue.isEmpty(), "the seeds publish at least one Studio");
    assertTrue(containsKey(catalogue, FREE_STUDIO), "the free launch Studio must be browsable");
  }

  @Test
  @Order(2)
  @DisplayName("A locked Studio is returned, not hidden — the upsell is the funnel")
  void lockedStudiosAreShownWithTheirPackage() {
    String token = authenticate();

    JsonNode catalogue = MAPPER.readTree(request.get("/api/v1/studios", bearer(token)).text());
    JsonNode paid = find(catalogue, PAID_STUDIO);

    if (paid == null) {
      // The paid Studio is only in the catalogue once published; nothing to assert otherwise.
      return;
    }
    if (paid.path("locked").asBoolean()) {
      assertFalse(
          paid.path("lockedReason").asString().isBlank(),
          "a locked card must say why, or the UI cannot offer a remedy");
    }
  }

  @Test
  @Order(3)
  @DisplayName("Detail carries the JSON Schemas the client generates its forms from")
  void detailCarriesTheSchemas() {
    String token = authenticate();

    APIResponse response = request.get("/api/v1/studios/" + FREE_STUDIO, bearer(token));

    assertEquals(200, response.status());
    JsonNode detail = MAPPER.readTree(response.text());
    assertEquals(FREE_STUDIO, detail.path("studioKey").asString());
    assertFalse(
        detail.path("inputSchema").asString("").isBlank(),
        "a published Studio must expose the schema its run form is generated from");
  }

  @Test
  @Order(4)
  @DisplayName("An unknown Studio is 404 — distinct from a locked one, which is 409")
  void unknownStudioIsNotFound() {
    String token = authenticate();

    assertEquals(404, request.get("/api/v1/studios/no-such-studio", bearer(token)).status());
  }

  @Test
  @Order(5)
  @DisplayName("Install → the Studio appears in My Studios with its pinned version")
  void installRoundTrips() {
    String token = authenticate();

    APIResponse installed =
        request.post(
            "/api/v1/studios/" + FREE_STUDIO + "/installations",
            bearerJson(token, "{\"config\":{\"tone\":\"premium\"}}"));

    assertEquals(201, installed.status(), "installing a FREE Studio must succeed");
    JsonNode installation = MAPPER.readTree(installed.text());
    installationId = installation.path("id").asString();
    assertNotNull(installationId);
    assertFalse(
        installation.path("pinnedVersion").asString().isBlank(), "an install pins a version");

    JsonNode mine =
        MAPPER.readTree(request.get("/api/v1/studios/installations", bearer(token)).text());
    assertTrue(
        containsField(mine, "studioKey", FREE_STUDIO), "My Studios must list what was installed");
  }

  @Test
  @Order(6)
  @DisplayName("An unknown config key is refused — the install dialog is not a free-form map")
  void unknownConfigKeyIsRefused() {
    String token = authenticate();

    APIResponse refused =
        request.post(
            "/api/v1/studios/" + FREE_STUDIO + "/installations",
            bearerJson(token, "{\"config\":{\"definitelyNotAKey\":\"x\"}}"));

    assertEquals(400, refused.status());
  }

  @Test
  @Order(7)
  @DisplayName("A run is accepted (202), validated, held and fanned out into steps")
  void runIsAccepted() {
    String token = authenticate();
    assertNotNull(installationId, "the install step must have run first");

    APIResponse accepted =
        request.post(
            "/api/v1/studios/installations/" + installationId + "/runs",
            bearerJson(
                token, "{\"inputs\":{\"photos\":[\"e2e-a\",\"e2e-b\"],\"trade\":\"plombier\"}}"));

    assertEquals(202, accepted.status(), "a run answers with an id, not a result");
    JsonNode run = MAPPER.readTree(accepted.text());
    assertFalse(run.path("id").asString().isBlank());
    assertEquals("RUNNING", run.path("status").asString());
    assertEquals(
        2,
        run.path("steps").size(),
        "two photos must fan out into two steps before anything downstream runs");
  }

  @Test
  @Order(8)
  @DisplayName("A run over REAL uploaded assets reaches SUCCEEDED with no step skipped")
  void runOverRealAssetsCompletesEveryStep() {
    String token = authenticate();
    assertNotNull(installationId, "the install step must have run first");

    // The gap this closes. `runIsAccepted` above asserts a run is ACCEPTED and fanned out — both
    // true of a run whose every vision step then fails. Combined with the fixture's old
    // `onError: SKIP` on that fan-out, a run in which NOTHING analysed anything finished
    // SUCCEEDED and this suite stayed green through three phases (ADR-042). Acceptance is not
    // execution; the difference is this test.
    // This actor's earlier runs are still in flight — `runIsAccepted` above starts one and never
    // finishes it — and the concurrency cap answers 409 rather than queueing. Clear them first:
    // the assertion below is about whether steps EXECUTE, and it must not be hostage to test order.
    cancelRunsInFlight(token);

    String assetA = uploadSampleImage(token);
    String assetB = uploadSampleImage(token);

    APIResponse accepted =
        request.post(
            "/api/v1/studios/installations/" + installationId + "/runs",
            bearerJson(
                token,
                "{\"inputs\":{\"photos\":[\""
                    + assetA
                    + "\",\""
                    + assetB
                    + "\"],\"trade\":\"plombier\"}}"));
    assertEquals(202, accepted.status());
    String runId = MAPPER.readTree(accepted.text()).path("id").asString();

    JsonNode finished = awaitTerminal(token, runId);
    String status = finished.path("status").asString();

    // Every step, by name, so a failure says WHICH one — a run that is merely "FAILED" sends the
    // reader back to the logs this assertion exists to replace.
    List<String> notSucceeded = new ArrayList<>();
    for (JsonNode step : finished.path("steps")) {
      if (!"SUCCEEDED".equals(step.path("status").asString())) {
        notSucceeded.add(
            step.path("stepId").asString()
                + "#"
                + step.path("ordinal").asInt()
                + "="
                + step.path("status").asString()
                + " ("
                + (step.path("error").isNull()
                    ? "no error reported"
                    : step.path("error").asString())
                + ")");
      }
    }

    assertTrue(
        notSucceeded.isEmpty(),
        "every step of a run over real assets must succeed; got: " + notSucceeded);
    assertEquals("SUCCEEDED", status, "a run whose steps all succeeded is SUCCEEDED");
  }

  /** Cancels every run this actor still has open, so the concurrency cap cannot refuse ours. */
  private void cancelRunsInFlight(String token) {
    APIResponse listed = request.get("/api/v1/studios/runs", bearer(token));
    if (listed.status() != 200) {
      return;
    }
    // Everything NOT terminal, not a hand-picked list: the cap counts `finished_at IS NULL`, and a
    // run can sit in PENDING_APPROVAL or COMPENSATING as easily as RUNNING. Naming the three
    // terminal states is the enumeration that cannot drift as the others are added.
    Set<String> terminal = Set.of("SUCCEEDED", "FAILED", "CANCELLED");
    for (JsonNode run : MAPPER.readTree(listed.text())) {
      if (!terminal.contains(run.path("status").asString())) {
        request.post(
            "/api/v1/studios/runs/" + run.path("id").asString() + "/cancel", bearer(token));
      }
    }
  }

  /** Uploads a tiny real PNG and returns its asset id. */
  private String uploadSampleImage(String token) {
    APIResponse uploaded =
        request.post(
            "/api/v1/media/upload",
            RequestOptions.create()
                .setHeader("Authorization", "Bearer " + token)
                .setMultipart(
                    FormData.create()
                        .set(
                            "file", new FilePayload("sample.png", "image/png", samplePngBytes()))));
    assertTrue(
        uploaded.status() == 200 || uploaded.status() == 201,
        "uploading a sample image must succeed; got " + uploaded.status());
    String assetId = MAPPER.readTree(uploaded.text()).path("assetId").asString();
    assertFalse(assetId.isBlank(), "the upload must return an asset id");
    return assetId;
  }

  /** Polls until the run leaves RUNNING; the job plane is asynchronous and offers no callback. */
  private JsonNode awaitTerminal(String token, String runId) {
    AtomicReference<JsonNode> run = new AtomicReference<>();
    await()
        .atMost(Duration.ofSeconds(240))
        .pollInterval(Duration.ofSeconds(2))
        .until(
            () -> {
              APIResponse response = request.get("/api/v1/studios/runs/" + runId, bearer(token));
              assertEquals(200, response.status());
              JsonNode current = MAPPER.readTree(response.text());
              run.set(current);
              return !"RUNNING".equals(current.path("status").asString());
            });
    return run.get();
  }

  /** A 16x16 PNG, built here so the suite carries no binary fixture. */
  private static byte[] samplePngBytes() {
    int size = 16;
    byte[] raw = new byte[size * (1 + size * 3)];
    int index = 0;
    for (int row = 0; row < size; row++) {
      raw[index++] = 0;
      for (int column = 0; column < size; column++) {
        raw[index++] = (byte) 120;
        raw[index++] = (byte) 140;
        raw[index++] = (byte) 180;
      }
    }
    java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
    try {
      png.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'});
      java.io.ByteArrayOutputStream ihdr = new java.io.ByteArrayOutputStream();
      ihdr.write(intBytes(size));
      ihdr.write(intBytes(size));
      ihdr.write(new byte[] {8, 2, 0, 0, 0});
      writeChunk(png, "IHDR", ihdr.toByteArray());
      java.io.ByteArrayOutputStream deflated = new java.io.ByteArrayOutputStream();
      try (java.util.zip.DeflaterOutputStream deflater =
          new java.util.zip.DeflaterOutputStream(deflated)) {
        deflater.write(raw);
      }
      writeChunk(png, "IDAT", deflated.toByteArray());
      writeChunk(png, "IEND", new byte[0]);
    } catch (java.io.IOException impossible) {
      throw new IllegalStateException("in-memory PNG assembly cannot fail", impossible);
    }
    return png.toByteArray();
  }

  private static void writeChunk(java.io.ByteArrayOutputStream out, String type, byte[] data)
      throws java.io.IOException {
    out.write(intBytes(data.length));
    byte[] typeBytes = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    out.write(typeBytes);
    out.write(data);
    java.util.zip.CRC32 crc = new java.util.zip.CRC32();
    crc.update(typeBytes);
    crc.update(data);
    out.write(intBytes((int) crc.getValue()));
  }

  private static byte[] intBytes(int value) {
    return new byte[] {
      (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value
    };
  }

  @Test
  @Order(9)
  @DisplayName("A run missing a required input is refused before any credit is held")
  void missingInputIsRefused() {
    String token = authenticate();

    APIResponse refused =
        request.post(
            "/api/v1/studios/installations/" + installationId + "/runs",
            bearerJson(token, "{\"inputs\":{\"trade\":\"plombier\"}}"));

    assertEquals(400, refused.status(), "server-side validation is authoritative, not the form");
  }

  @Test
  @Order(10)
  @DisplayName("A run belonging to nobody is 404, indistinguishable from another actor's")
  void foreignRunIsNotFound() {
    String token = authenticate();

    assertEquals(
        404, request.get("/api/v1/studios/runs/" + UUID.randomUUID(), bearer(token)).status());
  }

  @Test
  @Order(11)
  @DisplayName("Uninstall soft-revokes, and My Studios stops listing it")
  void uninstallRemovesItFromMyStudios() {
    String token = authenticate();

    assertEquals(
        204,
        request.delete("/api/v1/studios/installations/" + installationId, bearer(token)).status());

    JsonNode mine =
        MAPPER.readTree(request.get("/api/v1/studios/installations", bearer(token)).text());
    assertFalse(
        containsField(mine, "id", installationId), "a revoked installation must leave My Studios");
  }

  private static boolean containsKey(JsonNode array, String studioKey) {
    return containsField(array, "studioKey", studioKey);
  }

  private static boolean containsField(JsonNode array, String field, String value) {
    for (JsonNode element : array) {
      if (value.equals(element.path(field).asString())) {
        return true;
      }
    }
    return false;
  }

  private static JsonNode find(JsonNode array, String studioKey) {
    for (JsonNode element : array) {
      if (studioKey.equals(element.path("studioKey").asString())) {
        return element;
      }
    }
    return null;
  }
}
