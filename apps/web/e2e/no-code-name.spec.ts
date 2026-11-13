import { expect, test } from "@playwright/test";
import { encode } from "next-auth/jwt";
import { RETIRED_NAME } from "@/core/utils/visible-words.testkit";

const SECRET = "e2e-only-secret-not-used-anywhere-else"; // the suite's dev server (playwright.config.ts)

/**
 * No code name a user can see (krizaka-internal#2684): the landing bar and the signed-in frame, in both themes, at phone
 * width, say Sovkora and never the retired name. `E2E_SHOTS=<dir>` also writes the screenshots.
 */
const SHOTS = process.env.E2E_SHOTS;
const PHONE = { width: 360, height: 740 };

for (const scheme of ["light", "dark"] as const) {
  test.describe(`${scheme} theme, phone width`, () => {
    test.use({ viewport: PHONE, colorScheme: scheme });

    test("the landing bar", async ({ page }) => {
      await page.route("**/api/auth/session", (route) => route.fulfill({ json: {} }));
      await page.goto("/");
      await expect(page.locator("header").first()).toBeVisible();
      expect(await page.locator("body").innerText()).not.toMatch(RETIRED_NAME);
      if (SHOTS) {
        await page.screenshot({ path: `${SHOTS}/landing-${scheme}.png` });
      }
    });

    test("the signed-in app frame", async ({ page, context, baseURL }) => {
      const token = await encode({
        token: { name: "Mira", email: "mira@demo.sovkora.test", id: "e2e-user", role: "user" },
        secret: SECRET,
      });
      await context.addCookies([{ name: "next-auth.session-token", value: token, url: baseURL! }]);
      await page.route("**/api/v1/**", (route) => route.fulfill({ status: 200, json: [] }));
      await page.route("**/api/auth/session", (route) =>
        route.fulfill({
          json: {
            user: { id: "u1", name: "Mira", email: "mira@demo.sovkora.test" },
            expires: new Date(Date.now() + 3_600_000).toISOString(),
          },
        }),
      );
      await page.goto("/jobs");
      await expect(page.locator("header").first()).toBeVisible();
      expect(await page.locator("body").innerText()).not.toMatch(RETIRED_NAME);
      if (SHOTS) {
        await page.screenshot({ path: `${SHOTS}/app-${scheme}.png` });
      }
    });
  });
}
