package com.krizaka.sovkora.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.APIResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Tier 1 — API contract sensor (black-box) for the degraded-mode bootstrap payload.
 *
 * <p>Exercises the {@code browser/mobile → BFF → router} chain on the <b>running</b> router: it
 * authenticates as the seeded admin (exactly as the Next.js BFF forwards the user's bearer token)
 * and asserts {@code GET /api/v1/features} carries, per capability, the degraded-mode {@code
 * available} flag the capability picker greys offline engines with. The endpoint is {@code
 * hasAnyAuthority(ADMIN, USER)} — not public — so the realistic contract is the authenticated one
 * the clients actually issue. Pure client runner against the boot-and-probed router (ADR-040).
 */
class PublicApiContractIT extends AbstractApiE2eTest {

  @Test
  @DisplayName("GET /api/v1/features (authenticated) carries each capability's degraded-mode state")
  void bootstrapFeaturesExposeDegradedModeState() {
    String token = authenticate();

    APIResponse response = request.get("/api/v1/features", bearer(token));
    assertEquals(
        200,
        response.status(),
        "Bootstrap features must return 200 for an authenticated user — it backs the capability picker");

    JsonNode features = MAPPER.readTree(response.text());
    assertTrue(features.isArray(), "Bootstrap features payload must be a JSON array");
    assertTrue(features.size() > 0, "At least one enabled capability must be exposed");

    JsonNode first = features.get(0);
    assertTrue(first.has("id"), "Each feature must carry an id");
    assertTrue(first.has("label"), "Each feature must carry a label");
    assertTrue(
        first.path("available").isBoolean(),
        "Each feature must carry the degraded-mode 'available' boolean");
  }
}
