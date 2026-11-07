import { test, expect } from "@playwright/test";
import { encode } from "next-auth/jwt";

const SECRET = "e2e-only-secret-not-used-anywhere-else"; // the suite's dev server (playwright.config.ts)
const SHOTS = process.env.SHOT_DIR; // set to keep before/after captures (PR evidence goes to Bunny, never the repo)

const widths = [
  { name: "phone", width: 390, height: 780 },
  { name: "desktop", width: 1280, height: 800 },
] as const;

for (const vp of widths) {
  test(`the Sovkora mark shows once on ${vp.name}: in the rail on a desktop, in the bar on a phone`, async ({
    page,
  }) => {
    await page.setViewportSize({ width: vp.width, height: vp.height });
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
    if (SHOTS) await page.screenshot({ path: `${SHOTS}/${vp.name}.png` });

    const marks = page.locator("[data-brand-mark]:visible");
    await expect(marks).toHaveCount(1);
    await expect(marks.first().locator("xpath=ancestor::*[self::nav or self::header][1]")).toHaveJSProperty(
      "tagName",
      vp.name === "desktop" ? "NAV" : "HEADER",
    );

    if (vp.name === "desktop") {
      // The word « Sovkora » sits on the mark's line, right after the rail.
      const word = page.locator("header").getByText("Sovkora", { exact: true });
      const [m, w] = [await marks.first().boundingBox(), await word.boundingBox()];
      expect(Math.abs(m!.y + m!.height / 2 - (w!.y + w!.height / 2))).toBeLessThanOrEqual(1);
      expect(w!.x - 56).toBeLessThan(16);
    }
  });
}
