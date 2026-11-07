import { test, expect, type Page } from "@playwright/test";
import { encode } from "next-auth/jwt";

/**
 * The console page « Tool rounds per feature » (orazaka-ai-engine#169, rule « admin configures everything »), as an
 * administrator persona with the admin API stubbed: it reads the bounds, refuses a value outside the range before any
 * request, saves one, and lists the routes. The console answers only on an `admin.` host (`admin.localhost` is loopback);
 * the persona is a throwaway token signed with the suite's own key, never a real operator account.
 */
const CONSOLE = "http://admin.localhost:3000";
const SIGNING_KEY = "e2e-only-secret-not-used-anywhere-else"; // the suite's dev server (playwright.config.ts)
const RANGE = { min: 1, max: 32 };

async function signInAsAdminPersona(page: Page) {
  const token = await encode({
    token: {
      name: "Eric (demo admin)",
      email: "eric@orazaka.dev",
      id: "e2e-admin",
      role: "admin",
    },
    secret: SIGNING_KEY,
  });
  await page.context().addCookies([{ name: "orazaka-admin.session-token", value: token, url: CONSOLE }]);
}

async function stubAdminApi(page: Page): Promise<string[]> {
  let bounds = [
    { featureKey: "*", maxToolRounds: 8, isDefault: true },
    {
      featureKey: "agent.code.issue-to-pr",
      maxToolRounds: 3,
      isDefault: false,
    },
  ];
  const writes: string[] = [];
  await page.route("**/api/v1/models/admin/**", async (route) => {
    const request = route.request();
    const url = request.url();
    const json = (status: number, body: unknown) =>
      route.fulfill({
        status,
        contentType: "application/json",
        body: JSON.stringify(body),
      });
    if (url.endsWith("/feature-routes")) {
      return json(200, [
        {
          featureKey: "agent.code.issue-to-pr",
          models: ["qwen2.5-coder:7b", "mistral-3-14B"],
        },
      ]);
    }
    if (url.endsWith("/feature-structured-bounds")) {
      return json(200, {
        min: 1,
        max: 5,
        bounds: [
          { featureKey: "*", maxAttempts: 2, isDefault: true },
          { featureKey: "agent.code.issue-to-pr", maxAttempts: 4, isDefault: false },
        ],
      });
    }
    if (url.endsWith("/feature-tool-bounds")) {
      return json(200, { ...RANGE, bounds });
    }
    const key = decodeURIComponent(url.split("/feature-tool-bounds/")[1]);
    if (request.method() === "PUT") {
      const n = (request.postDataJSON() as { maxToolRounds: number }).maxToolRounds;
      writes.push(`PUT ${key} ${n}`);
      bounds = bounds.map((b) => (b.featureKey === key ? { ...b, maxToolRounds: n } : b));
      return json(200, { featureKey: key, maxToolRounds: n, isDefault: false });
    }
    return route.fulfill({ status: 204 });
  });
  return writes;
}

for (const theme of ["dark", "light"] as const) {
  for (const viewport of [
    { name: "desktop", width: 1280, height: 900 },
    { name: "phone", width: 390, height: 844 },
  ]) {
    test(`an administrator reads, refuses and saves a tool bound (${theme}, ${viewport.name})`, async ({ page }) => {
      await page.setViewportSize({
        width: viewport.width,
        height: viewport.height,
      });
      await page.addInitScript((t) => {
        try {
          localStorage.setItem("kz-theme", t);
        } catch {}
      }, theme);
      await page.emulateMedia({ colorScheme: theme });
      await signInAsAdminPersona(page);
      const writes = await stubAdminApi(page);

      await page.goto(`${CONSOLE}/engine/tools`, { waitUntil: "networkidle" });
      await expect(
        page.getByRole("heading", {
          level: 1,
          name: "Tool rounds per feature",
        }),
      ).toBeVisible();
      const bound = page.locator('[data-bound="agent.code.issue-to-pr"]');
      await expect(bound.getByLabel("Rounds")).toHaveValue("3");
      await expect(page.locator('[data-route="agent.code.issue-to-pr"]')).toContainText("qwen2.5-coder:7b");

      await bound.getByLabel("Rounds").fill("99");
      await bound.getByRole("button", { name: "Save" }).click();
      await expect(bound).toContainText("Enter a whole number from 1 to 32.");
      expect(writes).toEqual([]);

      await bound.getByLabel("Rounds").fill("5");
      await bound.getByRole("button", { name: "Save" }).click();
      await expect(bound).toContainText("Saved");
      expect(writes).toEqual(["PUT agent.code.issue-to-pr 5"]);

      const structured = page.locator('[data-structured-bound="agent.code.issue-to-pr"]');
      await expect(structured.getByLabel("Answers")).toHaveValue("4");
      await structured.getByLabel("Answers").fill("9");
      await structured.getByRole("button", { name: "Save" }).click();
      await expect(structured).toContainText("Enter a whole number of answers from 1 to 5.");
      expect(writes).toEqual(["PUT agent.code.issue-to-pr 5"]);

      const overflow = await page.evaluate(
        () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
      );
      expect(overflow).toBeLessThanOrEqual(0);
      if (process.env.OUT) {
        await page.screenshot({
          path: `${process.env.OUT}/feature-tools-${theme}-${viewport.name}.png`,
          fullPage: true,
        });
      }
    });
  }
}
