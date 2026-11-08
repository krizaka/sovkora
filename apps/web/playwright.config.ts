import { defineConfig, devices } from "@playwright/test";

/**
 * Playwright configuration for Orazaka UI E2E tests [ADR-033].
 *
 * - Headless Chromium by default (CI-friendly).
 * - Auto-starts the Next.js dev server on port 3000.
 * - Network mocking via page.route() — zero backend dependency.
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.CI ? "github" : "html",
  timeout: 30_000,

  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3000",
    trace: "on-first-retry",
    screenshot: "only-on-failure",
  },

  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
    // The live smoke also runs in the other two engines (Safari's and Firefox's), where the bugs a visitor meets hide.
    ...(process.env.E2E_BASE_URL
      ? [
          { name: "webkit", use: { ...devices["Desktop Safari"] } },
          { name: "firefox", use: { ...devices["Desktop Firefox"] } },
        ]
      : []),
  ],

  // E2E_BASE_URL points the suite at a deployed site (the live smoke): nothing is started then.
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : {
        command: "npm run dev",
        // A known secret so the suite can sign the session cookie it needs (a throwaway value, used nowhere else); sign-in open so the session route answers.
        env: { NEXTAUTH_SECRET: "e2e-only-secret-not-used-anywhere-else", AUTH_LOGIN_ENABLED: "true" },
        url: "http://localhost:3000",
        reuseExistingServer: !process.env.CI,
        timeout: 120_000,
      },
});
