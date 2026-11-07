import { expect, test } from "@playwright/test";

/**
 * Live: a finished image run shows its image. Signs in as the QA user and opens the runs named in
 * E2E_RUN_IDS (comma separated); each page must paint an <img> with naturalWidth > 0 — and never the
 * "no longer available" placeholder, which would mean the durable store lost the file.
 * Run: E2E_BASE_URL=https://www.orazaka.com E2E_USER=… E2E_PASSWORD=… E2E_RUN_IDS=id1,id2 npx playwright test live-run-image
 */
const runs = (process.env.E2E_RUN_IDS ?? "").split(",").filter(Boolean);
test.skip(
  !process.env.E2E_BASE_URL || !process.env.E2E_USER || runs.length === 0,
  "live: needs E2E_BASE_URL, E2E_USER, E2E_PASSWORD, E2E_RUN_IDS",
);

for (const id of runs) {
  test(`live: run ${id} paints its image`, async ({ page }) => {
    await page.goto("/login");
    await page.fill('input[type="email"]', process.env.E2E_USER!);
    await page.fill('input[type="password"]', process.env.E2E_PASSWORD!);
    await page.keyboard.press("Enter");
    await page.waitForURL((url) => !url.pathname.startsWith("/login"));
    await page.goto(`/studios/runs/${id}`, { waitUntil: "networkidle" });
    const image = page.locator('img[src*="/api/v1/assets/"]').first();
    await expect(image).toBeVisible();
    await expect.poll(() => image.evaluate((img: HTMLImageElement) => img.naturalWidth)).toBeGreaterThan(0);
    await expect(page.getByText("This result is no longer available")).toHaveCount(0);
  });
}
