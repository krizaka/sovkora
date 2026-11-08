package com.krizaka.sovkora.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import tools.jackson.databind.ObjectMapper;

/**
 * Shared black-box API-contract harness for ITs that exercise the <b>running</b> router (the same
 * {@code browser/mobile → BFF → router} bearer-token chain, ADR-040).
 *
 * <p>Each test class gets its own Playwright {@link APIRequestContext} (base URL = {@code
 * router.target.url}) via {@code @TestInstance(PER_CLASS)} instance state, so classes run
 * concurrently. Provides the seeded-admin login + bearer/json request helpers.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractApiE2eTest {

  protected static final String ROUTER_URL = System.getProperty("router.target.url");
  protected static final String ADMIN_EMAIL = System.getProperty("admin.email");
  protected static final String ADMIN_PASSWORD = System.getProperty("admin.password");
  protected static final ObjectMapper MAPPER = new ObjectMapper();

  private Playwright playwright;
  protected APIRequestContext request;

  @BeforeAll
  void openApi() {
    if (ROUTER_URL == null || ROUTER_URL.isBlank()) {
      throw new IllegalStateException("router.target.url is missing. Check your root .env file.");
    }
    playwright = Playwright.create();
    request =
        playwright.request().newContext(new APIRequest.NewContextOptions().setBaseURL(ROUTER_URL));
  }

  @AfterAll
  void closeApi() {
    if (request != null) {
      request.dispose();
    }
    if (playwright != null) {
      playwright.close();
    }
  }

  /** Logs in as the seeded admin and returns the bearer token the BFF forwards downstream. */
  protected String authenticate() {
    APIResponse login =
        request.post(
            "/api/v1/auth/login",
            RequestOptions.create()
                .setHeader("Content-Type", "application/json")
                .setData(
                    "{\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"));
    assertEquals(200, login.status(), "Admin login must return 200");
    String token = MAPPER.readTree(login.text()).path("token").asString();
    assertNotNull(token, "Login response must carry a token");
    assertFalse(token.isBlank(), "Login token must not be blank");
    return token;
  }

  /** Authorization-only options (GET/DELETE). */
  protected RequestOptions bearer(String token) {
    return RequestOptions.create().setHeader("Authorization", "Bearer " + token);
  }

  /** Authorization + JSON body options (POST/PUT). */
  protected RequestOptions bearerJson(String token, String jsonBody) {
    return RequestOptions.create()
        .setHeader("Authorization", "Bearer " + token)
        .setHeader("Content-Type", "application/json")
        .setData(jsonBody);
  }
}
