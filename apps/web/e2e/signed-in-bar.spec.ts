import { expect, test } from "@playwright/test";

/**
 * A signed-in person never sees « Sign in » on a public page, not even for the instant the session is read (krizaka-ui#827):
 * the proxy keeps a readable hint next to the next-auth cookie and the bar reserves the account's own place meanwhile.
 * `/` needs no redirect here: it already renders the dashboard from the same cookie (src/app/page.tsx).
 */
const SIGN_IN = 'header a[href="/login"], nav a[href="/login"]';

/** Records, from the first moment of the document, whether the header ever held a link to the sign-in page. */
const watchSignInLink = () => {
  (window as unknown as { __sawSignIn: boolean }).__sawSignIn = false;
  const look = () => {
    if (document.querySelector('header a[href="/login"], nav a[href="/login"]'))
      (window as unknown as { __sawSignIn: boolean }).__sawSignIn = true;
  };
  new MutationObserver(look).observe(document, { childList: true, subtree: true });
  document.addEventListener("DOMContentLoaded", look);
};

test("signed out, a public page offers « Sign in » (so the next check means something)", async ({ page }) => {
  await page.route("**/api/auth/session", (route) => route.fulfill({ json: {} }));
  await page.goto("/pricing");
  await expect(page.locator(SIGN_IN).first()).toBeVisible();
});

test("signed in, the bar never draws « Sign in », even while the session answers slowly", async ({
  page,
  context,
  baseURL,
}) => {
  await context.addCookies([{ name: "next-auth.session-token", value: "e2e-token", url: baseURL! }]);
  await page.addInitScript(watchSignInLink);
  await page.route("**/api/auth/session", async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 1500));
    await route.fulfill({
      json: {
        user: { id: "u1", name: "Eric", email: "eric@demo.orazaka.test" },
        expires: new Date(Date.now() + 3_600_000).toISOString(),
      },
    });
  });
  await page.goto("/pricing");
  await expect(page.locator('header a[href="/"], nav a[href="/"]').filter({ hasText: /./ }).first()).toBeVisible({
    timeout: 15_000,
  });
  expect((await context.cookies()).some((c) => c.name === "kz-session" && c.value === "1")).toBe(true);
  expect(await page.evaluate(() => (window as unknown as { __sawSignIn: boolean }).__sawSignIn)).toBe(false);
});
