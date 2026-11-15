import { test, expect, type Page } from "@playwright/test";
import { mockApi } from "./support/mock-api";
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
  await mockApi(page, (url) => {
    if (/\/studios\/document-authenticity(\?|$)/.test(url)) {
      return detail(estimatedCredits);
    }
    return /\/studios(\?|$)/.test(url)
      ? [summary("document-authenticity", "Document Authenticity", "legal")]
      : undefined;
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
