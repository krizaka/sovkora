import { test, expect, type Page } from "@playwright/test";
import { signInAsDemoUser } from "./support/mock-session";
import { assertTextReadable } from "../scripts/text-width-on-screen.mjs";

/**
 * The Studios screens keep their text readable (krizaka-internal#3901): the estimated-cost line of a Studio was drawn 170px
 * wide beside the Run button, and the showcase recording of the Studios stopped on it. At 1280, 390 and 360 px, light and
 * dark, the catalogue and a Studio's page pass the text-width guard; a Studio with no price says so instead of a blank figure.
 *
 * Mocked (no backend), as the other suites.
 */
const summary = (key: string, label: string, profession: string) => ({
  studioKey: key,
  label,
  tagline: "Checks a document and says what it found, in plain words.",
  profession,
  iconKey: "studio",
  heroAssetId: null,
  pricing: "INCLUDED",
  kind: "VERTICAL",
  latestVersion: "1.0.0",
  locked: false,
  lockedReason: "NONE",
  packKey: null,
});
const detail = (estimatedCredits: number) => ({
  ...summary("document-authenticity", "Document Authenticity", "legal"),
  description: null,
  status: "PUBLISHED",
  estimatedCredits,
  inputSchema: JSON.stringify({
    type: "object",
    required: ["notes"],
    properties: { notes: { type: "string", title: "Your notes", minLength: 3 } },
  }),
  configSchema: "{}",
});

async function mockStudios(page: Page, estimatedCredits: number) {
  await signInAsDemoUser(page);
  await page.route("**/api/v1/**", async (route) => {
    const url = route.request().url();
    const json = (body: unknown) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body) });
    if (/\/studios\/document-authenticity(\?|$)/.test(url)) {
      return json(detail(estimatedCredits));
    }
    if (/\/studios(\?|$)/.test(url)) {
      return json([summary("document-authenticity", "Document Authenticity", "legal")]);
    }
    if (url.includes("/jobs")) {
      return json({ content: [], totalPages: 0, totalElements: 0 });
    }
    if (url.includes("/settings")) {
      return json({ id: "e2e-user", username: "Eric" });
    }
    return json([]);
  });
}

for (const width of [1280, 390, 360]) {
  for (const colorScheme of ["light", "dark"] as const) {
    for (const credits of [12, 0]) {
      test(`a Studio page (${credits} credits) keeps its text readable at ${width}px, ${colorScheme}`, async ({
        page,
      }) => {
        await page.setViewportSize({ width, height: 800 });
        await page.emulateMedia({ colorScheme });
        await mockStudios(page, credits);
        await page.goto("/studios/document-authenticity");
        await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
        const cost = page.getByTestId("estimated-cost");
        await expect(cost).toBeVisible();
        await expect(cost).toContainText(credits > 0 ? String(credits) : /not set|non défini/i);
        await assertTextReadable(page, `studio ${credits} ${width}px ${colorScheme}`);
      });
    }

    test(`the Studios catalogue keeps its text readable at ${width}px, ${colorScheme}`, async ({ page }) => {
      await page.setViewportSize({ width, height: 800 });
      await page.emulateMedia({ colorScheme });
      await mockStudios(page, 12);
      await page.goto("/studios");
      await expect(page.getByText("Document Authenticity").first()).toBeVisible();
      await assertTextReadable(page, `studios ${width}px ${colorScheme}`);
    });
  }
}
