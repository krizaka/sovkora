import type { Page } from "@playwright/test";
import { encode } from "next-auth/jwt";

/** The secret the e2e dev server signs its session cookie with (a throwaway value, set in playwright.config.ts). */
const E2E_SECRET = "e2e-only-secret-not-used-anywhere-else";

/** Signs the page in as the demo user, with no backend: a session cookie the dev server accepts. */
export async function signInAsDemoUser(page: Page): Promise<void> {
  const token = await encode({
    token: { name: "Eric", email: "eric@example.test", backendToken: "e2e-user", role: "user" },
    secret: E2E_SECRET,
  });
  await page.context().addCookies([{ name: "next-auth.session-token", value: token, url: "http://localhost:3000" }]);
}

/**
 * The BFF's read endpoints, answered with nothing to show (no backend): the jobs and the settings in their shape, every other list
 * empty, and `sessions` for the chat's threads. A 4xx would sign the page out.
 */
export async function mockEmptyBackend(page: Page, sessions: readonly unknown[] = []): Promise<void> {
  await page.route("**/api/v1/**", (route) => {
    const url = route.request().url();
    let body: unknown = [];
    if (url.includes("/jobs")) {
      body = { content: [], totalPages: 0, totalElements: 0 };
    } else if (url.includes("/settings")) {
      body = { id: "e2e-user", username: "Eric" };
    } else if (url.includes("/chat/sessions")) {
      body = sessions;
    }
    return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body) });
  });
}
