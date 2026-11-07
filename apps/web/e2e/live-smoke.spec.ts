import { expect, test } from "@playwright/test";

/**
 * Runs against the DEPLOYED site (E2E_BASE_URL, see .github/workflows/live-smoke.yml) — what a visitor gets, not what
 * `next dev` serves. The public site is small (epic krizaka/orazaka-web-client#231): the home, the bar, and the
 * removed addresses that still answer.
 */
test.skip(!process.env.E2E_BASE_URL, "live smoke: set E2E_BASE_URL to a deployed site");

test("live: the home loads and every link of the bar leads to a page", async ({ page, request }) => {
  await page.goto("/", { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  const hrefs = await page.$$eval("header a[href^='/']", (as) => [
    ...new Set(as.map((a) => (a.getAttribute("href") ?? "").split("#")[0])),
  ]);
  expect(hrefs.length).toBeGreaterThan(2);
  for (const href of hrefs.filter(Boolean)) {
    const res = await request.get(href);
    expect(res.status(), href).toBeLessThan(400);
  }
});

for (const [from, to] of [
  ["/gallery", "/#records"],
  ["/features/unified-engine", "/#features"],
  ["/models", "/docs/models"],
] as const) {
  test(`live: ${from} answers 308 to ${to}`, async ({ request }) => {
    const res = await request.get(from, { maxRedirects: 0 });
    expect(res.status()).toBe(308);
    expect(res.headers()["location"]).toContain(to);
  });
}
