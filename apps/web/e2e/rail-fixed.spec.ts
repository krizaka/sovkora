import { test, expect } from "@playwright/test";
import { encode } from "next-auth/jwt";

const SECRET = "e2e-only-secret-not-used-anywhere-else"; // the suite's dev server (playwright.config.ts)

// The rail stays where it is while the page scrolls (Oussama, 2026-10-20, on a product's rail): after 2000 px of scrolling,
// on the window and on the content beside it, its bounding box is unchanged.
test("the rail does not move when the page scrolls", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  const token = await encode({
    token: { name: "Eric", email: "eric@orazaka.dev", id: "e2e-user", role: "user" },
    secret: SECRET,
  });
  await page.context().addCookies([{ name: "next-auth.session-token", value: token, url: "http://localhost:3000" }]);
  await page.route("**/api/v1/**", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: "[]" }),
  );
  await page.goto("/jobs");
  await page.waitForLoadState("networkidle");

  const rail = page.getByRole("navigation").first();
  const before = await rail.boundingBox();
  expect(before).not.toBeNull();

  await page.mouse.move(700, 400);
  await page.mouse.wheel(0, 2000);
  await page.evaluate(() => {
    window.scrollTo(0, 2000);
    for (const el of document.querySelectorAll("main, main *")) (el as HTMLElement).scrollTop = 2000;
  });
  await page.waitForTimeout(300);

  const after = await rail.boundingBox();
  expect(after!.y).toBeCloseTo(before!.y, 0);
  expect(after!.height).toBeCloseTo(before!.height, 0);
  // Its last item (the foot of the rail) is still on screen.
  const last = await page.getByRole("navigation").first().locator("a").last().boundingBox();
  expect(last!.y).toBeGreaterThanOrEqual(0);
  expect(last!.y + last!.height).toBeLessThanOrEqual(800);
});
