import { test, expect } from "@playwright/test";
import { encode } from "next-auth/jwt";

const SECRET = "e2e-only-secret-not-used-anywhere-else"; // the suite's dev server (playwright.config.ts)
const video = {
  id: "9f3a21c4-1111-4a5b-8c7d-000000000001",
  userId: "u",
  featureKey: "orazaka.core.media.video",
  status: "COMPLETED",
  createdAt: "2026-10-16T10:00:00Z",
  updatedAt: "2026-10-16T10:02:47Z",
  payload: {
    prompt: "A heron lifting off a misty lake",
    model: "wan2.2-ti2v-5b",
    width: 1280,
    height: 704,
    frames: 121,
    fps: 24,
  },
  result: {
    url: "/clip.mp4",
    durationMs: 768,
    metrics: {
      provider: "runpod",
      gpu: "NVIDIA H100 80GB HBM3",
      gpu_allocated_mb: 29800,
      inference_time_sec: 167.7,
      peak_memory_rss_mb: 4096,
    },
  },
};

test("the Jobs page is one line per task; the facts are grouped, in words and units, in the row's detail", async ({
  page,
}) => {
  const token = await encode({
    token: { name: "Eric", email: "eric@orazaka.dev", id: "e2e-user", role: "user" },
    secret: SECRET,
  });
  await page.context().addCookies([{ name: "next-auth.session-token", value: token, url: "http://localhost:3000" }]);
  await page.route("**/api/v1/**", (route) => {
    const url = route.request().url();
    if (/\/jobs(\?|$)/.test(url))
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ content: [video], totalPages: 1, totalElements: 1, number: 0, size: 10 }),
      });
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: url.includes("/settings") ? JSON.stringify({ id: "e2e-user", username: "Eric" }) : "[]",
    });
  });
  await page.goto("/jobs");
  const row = page.getByRole("row").filter({ hasText: "Video Generation" }).first();
  await expect(row).toContainText("Wan 2.2 TI2V 5B");
  await expect(row).toContainText("H100 · RunPod");
  await expect(row).toContainText("2 min 47 s");
  await row.getByRole("button", { name: /Video Generation/ }).click();
  await expect(page.getByText("29.1 GB")).toBeVisible();
  await expect(page.getByText("167.7 s")).toBeVisible();
  expect(await page.locator("body").innerText()).not.toMatch(/gpu_allocated_mb|inference_time_sec|peak_memory_rss_mb/);
});
