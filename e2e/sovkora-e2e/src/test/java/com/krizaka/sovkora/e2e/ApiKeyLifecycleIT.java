package com.krizaka.sovkora.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.microsoft.playwright.APIResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Tier 1 — API-key lifecycle sensor (black-box) against the <b>running</b> router.
 *
 * <p>Proves the full inbound Personal Access Token contract on the real stack: a
 * session-authenticated user mints a key, the returned plaintext then authenticates an
 * otherwise-protected endpoint exactly as a programmatic client would, and once revoked the same
 * key is rejected. Mirrors the {@code browser/mobile → BFF → router} bearer chain (ADR-040).
 */
class ApiKeyLifecycleIT extends AbstractApiE2eTest {

  @Test
  @DisplayName("An API key authenticates its owner's calls and is rejected once revoked")
  void apiKeyLifecycle() {
    String session = authenticate();

    // Mint a key — the plaintext secret is returned exactly once.
    APIResponse created =
        request.post("/api/v1/api-keys", bearerJson(session, "{\"name\":\"e2e-key\"}"));
    assertEquals(201, created.status(), "Creating an API key must return 201");
    JsonNode createdJson = MAPPER.readTree(created.text());
    String keyId = createdJson.path("id").asString();
    String plaintext = createdJson.path("key").asString();
    assertFalse(plaintext.isBlank(), "Create response must carry the one-time plaintext key");
    assertTrue(plaintext.startsWith("oz_"), "The key must carry the oz_ prefix");

    // The listing exposes metadata only — never the plaintext secret.
    APIResponse listed = request.get("/api/v1/api-keys", bearer(session));
    assertEquals(200, listed.status(), "Listing keys must return 200 for the owner");
    JsonNode listJson = MAPPER.readTree(listed.text());
    assertTrue(
        listJson.isArray() && listJson.size() >= 1, "The freshly created key must be listed");
    assertFalse(
        listed.text().contains(plaintext), "The listing must never expose the plaintext secret");

    // The plaintext key authenticates a protected endpoint, just like a programmatic client.
    APIResponse withKey = request.get("/api/v1/features", bearer(plaintext));
    assertEquals(200, withKey.status(), "A valid API key must authenticate API calls");

    // Revoke it.
    APIResponse revoked = request.delete("/api/v1/api-keys/" + keyId, bearer(session));
    assertEquals(204, revoked.status(), "Revoking a key must return 204");

    // The revoked key no longer authenticates anything.
    APIResponse afterRevoke = request.get("/api/v1/features", bearer(plaintext));
    assertEquals(401, afterRevoke.status(), "A revoked API key must be rejected");
  }
}
