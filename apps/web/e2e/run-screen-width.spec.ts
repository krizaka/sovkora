import { test, expect, type Page } from "@playwright/test";
import { signInAsDemoUser } from "./support/mock-session";
import { assertTextReadable } from "../scripts/text-width-on-screen.mjs";

/**
 * The run screen keeps its text readable on a phone (krizaka-internal#3834): « En cours… » was squeezed to one word per line
 * beside the mascot and the Cancel button. At 360 and 390 px, light and dark, in every state a run can be in, no block of text is
 * narrower than 280 px or reads three words a line. The same check guards every recorded screen (scripts/text-width-on-screen.mjs).
 *
 * Mocked (no backend), as the other suites.
 */
const RUN_ID = "11111111-1111-1111-1111-111111111111";

const step = (stepId: string, ordinal: number, status: string) => ({
  stepId,
  ordinal,
  status,
  jobId: null,
  attempts: 1,
  error: status === "FAILED" ? "The worker stopped before the clip was written." : null,
});

const STATES = {
  queued: {
    status: "PENDING_APPROVAL",
    errorMessage: null,
    steps: [step("script", 0, "PENDING"), step("clip", 1, "PENDING")],
  },
  running: {
    status: "RUNNING",
    errorMessage: null,
    steps: [step("script", 0, "SUCCEEDED"), step("clip", 1, "RUNNING")],
  },
  awaiting: {
    status: "AWAITING_INPUT",
    errorMessage: null,
    steps: [step("script", 0, "SUCCEEDED"), step("clip", 1, "PENDING")],
  },
  failed: {
    status: "FAILED",
    errorMessage: "The video worker stopped before the clip was written, so nothing was charged to your credits.",
    steps: [step("script", 0, "SUCCEEDED"), step("clip", 1, "FAILED")],
  },
  done: {
    status: "SUCCEEDED",
    errorMessage: null,
    steps: [step("script", 0, "SUCCEEDED"), step("clip", 1, "SUCCEEDED")],
  },
} as const;

async function mockRun(page: Page, state: (typeof STATES)[keyof typeof STATES]) {
  await signInAsDemoUser(page);
  await page.route("**/api/v1/**", async (route) => {
    const url = route.request().url();
    const json = (body: unknown) =>
      route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body) });
    if (url.includes(`/studios/runs/${RUN_ID}`)) {
      return json({
        id: RUN_ID,
        installationId: null,
        studioKey: "video-generation",
        blueprintVersion: "1.8.0",
        startedAt: "2026-10-20T12:00:00Z",
        finishedAt: null,
        outputs: [],
        ...state,
      });
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

for (const width of [360, 390]) {
  for (const colorScheme of ["light", "dark"] as const) {
    for (const [name, state] of Object.entries(STATES)) {
      test(`the run screen (${name}) keeps its text readable at ${width}px, ${colorScheme}`, async ({ page }) => {
        await page.setViewportSize({ width, height: 800 });
        await page.emulateMedia({ colorScheme });
        await mockRun(page, state);
        await page.goto(`/studios/runs/${RUN_ID}`);
        await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
        await assertTextReadable(page, `run ${name} ${width}px ${colorScheme}`);
      });
    }
  }
}
