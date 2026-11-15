import type { Page } from "@playwright/test";
import { encode } from "next-auth/jwt";

/** The secret the e2e dev server signs its session cookie with (a throwaway value, set in playwright.config.ts). */
const E2E_SECRET = "e2e-only-secret-not-used-anywhere-else";

/** Signs the page in as the demo user, with no backend: a session cookie the dev server accepts. */
export async function signInAsDemoUser(page: Page): Promise<void> {
  const token = await encode({
    token: { name: "Eric", email: "eric@example.test", id: "e2e-user", role: "user" },
    secret: E2E_SECRET,
  });
  await page.context().addCookies([{ name: "next-auth.session-token", value: token, url: "http://localhost:3000" }]);
}
