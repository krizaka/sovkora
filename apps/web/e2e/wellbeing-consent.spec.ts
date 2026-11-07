import { test, expect, type Page } from "@playwright/test";
import { encode } from "next-auth/jwt";

/**
 * The consent step of a wellbeing (REGULATED) Studio, krizaka/orazaka-web-client#194.
 *
 * Mocked (CI, no backend): « Guided journaling » answers its install with 451 consent_required, the statement is shown
 * verbatim, nothing starts until the age, the region and the consent are given, the install then carries the three
 * declarations and the run starts. Live (E2E_BASE_URL, E2E_USER, E2E_PASSWORD, a persona that holds the wellbeing pack):
 * the same journey on www.orazaka.com, which ends on a real run.
 * Run live: E2E_BASE_URL=https://www.orazaka.com E2E_USER=… E2E_PASSWORD=… npx playwright test wellbeing-consent
 */
const E2E_SECRET = "e2e-only-secret-not-used-anywhere-else";
const STATEMENT =
  "Ce Studio enregistre ce que vous y écrivez sur votre humeur. Vous consentez explicitement à ce traitement.";
const live = Boolean(process.env.E2E_BASE_URL);

const summary = {
  studioKey: "guided-journaling",
  label: "Guided journaling",
  tagline: "Your words, set down.",
  profession: "general",
  iconKey: "heart",
  heroAssetId: null,
  pricing: "FREE",
  kind: "VERTICAL",
  latestVersion: "1.2.0",
  locked: false,
  lockedReason: "NONE",
  packKey: null,
};
const detail = {
  ...summary,
  description: null,
  status: "PUBLISHED",
  estimatedCredits: 40,
  configSchema: null,
  inputSchema: JSON.stringify({
    type: "object",
    required: ["entry"],
    properties: { entry: { type: "string", title: "What you want to write down", minLength: 3 } },
  }),
};
const wellbeingPack = {
  packKey: "bien-etre",
  categoryKey: "wellbeing",
  label: "Wellbeing",
  tagline: null,
  iconKey: "heart",
  heroAssetId: null,
  regulatoryClass: "REGULATED",
  kind: "VERTICAL",
  access: "BUYABLE",
  studioKeys: ["guided-journaling"],
  priceCents: 0,
  includedCredits: null,
};
const installation = {
  id: "inst-7",
  studioKey: "guided-journaling",
  label: "Guided journaling",
  iconKey: "heart",
  pinnedVersion: "1.2.0",
  latestVersion: "1.2.0",
  status: "ACTIVE",
  config: {},
  installedAt: "2026-10-20T12:00:00Z",
  lastRunAt: null,
};

interface Calls {
  installs: Record<string, unknown>[];
  runs: number;
  uninstalled: number;
}

async function mock(page: Page): Promise<Calls> {
  const calls: Calls = { installs: [], runs: 0, uninstalled: 0 };
  const token = await encode({
    token: { name: "Eric", email: "eric@orazaka.dev", id: "e2e-user", role: "user" },
    secret: E2E_SECRET,
  });
  await page.context().addCookies([{ name: "next-auth.session-token", value: token, url: "http://localhost:3000" }]);
  let installed = false;
  await page.route("**/api/v1/**", async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const json = (body: unknown, status = 200) =>
      route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
    if (request.method() === "POST" && path === "/api/v1/studios/guided-journaling/installations") {
      const body = JSON.parse(request.postData() ?? "{}") as Record<string, unknown>;
      calls.installs.push(body);
      if (body.consentVersion !== "1.0") {
        return json(
          {
            status: "consent_required",
            message: "consent needed",
            studioKey: "guided-journaling",
            consentVersion: "1.0",
            statement: STATEMENT,
          },
          451,
        );
      }
      installed = true;
      return json(installation, 201);
    }
    if (request.method() === "POST" && path === "/api/v1/studios/installations/inst-7/runs") {
      calls.runs += 1;
      return json(
        {
          id: "33333333-3333-3333-3333-333333333333",
          installationId: "inst-7",
          studioKey: "guided-journaling",
          blueprintVersion: "1.2.0",
          status: "RUNNING",
          startedAt: "2026-10-20T12:00:00Z",
          finishedAt: null,
          errorMessage: null,
          steps: [],
          outputs: [],
        },
        201,
      );
    }
    if (request.method() === "DELETE" && path === "/api/v1/studios/installations/inst-7") {
      calls.uninstalled += 1;
      installed = false;
      return route.fulfill({ status: 204 });
    }
    if (path === "/api/v1/studios") return json([summary]);
    if (path === "/api/v1/studios/packs") return json([wellbeingPack]);
    if (path === "/api/v1/studios/installations") return json(installed ? [installation] : []);
    if (path === "/api/v1/studios/guided-journaling") return json(detail);
    if (path === "/api/v1/studios/guided-journaling/versions") return json([]);
    if (path.includes("/jobs")) return json({ content: [], totalPages: 0, totalElements: 0 });
    if (path.includes("/settings")) return json({ id: "e2e-user", username: "Eric" });
    return json([]);
  });
  return calls;
}

async function agree(page: Page, region: string) {
  const dialog = page.getByRole("dialog", { name: /Before you start Guided journaling/ });
  await expect(dialog).toBeVisible();
  const agreeButton = dialog.getByRole("button", { name: "Agree and continue" });
  await expect(agreeButton).toBeDisabled();
  await dialog.getByRole("checkbox", { name: /legal age/ }).click();
  await dialog.getByRole("combobox").selectOption(region);
  await expect(agreeButton).toBeDisabled();
  await dialog.getByRole("checkbox", { name: /explicitly consent/ }).click();
  await agreeButton.click();
}

test.describe("Wellbeing Studio: consent before the first run (mocked)", () => {
  test.skip(live, "mocked: runs against the dev server only");

  test("the 451 becomes the consent step; agreeing installs with the three declarations, then runs", async ({
    page,
  }) => {
    const calls = await mock(page);
    await page.goto("/studios/guided-journaling");
    await expect(page.getByText(/Consent asked/)).toBeVisible();

    await page.getByRole("textbox", { name: /What you want to write down/ }).fill("A hard week, a good Friday.");
    await page.getByRole("button", { name: /^Run/ }).click();

    const dialog = page.getByRole("dialog", { name: /Before you start Guided journaling/ });
    await expect(dialog.getByText(STATEMENT)).toBeVisible();
    await expect(dialog.getByText(/not medical advice or care/)).toBeVisible();
    expect(calls.runs).toBe(0);

    await agree(page, "CA-QC");
    await page.waitForURL(/\/studios\/runs\//);
    expect(calls.installs.at(-1)).toEqual({ config: {}, consentVersion: "1.0", ageAttested: true, region: "CA-QC" });
    expect(calls.runs).toBe(1);
  });

  test("declining starts nothing; an installed Studio can have its consent withdrawn", async ({ page }) => {
    const calls = await mock(page);
    await page.goto("/studios/guided-journaling");
    await page.getByRole("textbox", { name: /What you want to write down/ }).fill("A calm week.");
    await page.getByRole("button", { name: /^Run/ }).click();
    await page.getByRole("dialog").getByRole("button", { name: "Not now" }).click();
    await expect(page.getByRole("dialog")).toHaveCount(0);
    expect(calls.runs).toBe(0);

    await page.getByRole("button", { name: /^Run/ }).click();
    await agree(page, "FR");
    await page.waitForURL(/\/studios\/runs\//);

    await page.goto("/studios/guided-journaling");
    await page.getByRole("button", { name: "Withdraw my consent" }).click();
    await page
      .getByRole("dialog", { name: "Withdraw your consent?" })
      .getByRole("button", { name: "Withdraw consent" })
      .click();
    await expect(page.getByText(/Consent asked/)).toBeVisible();
    expect(calls.uninstalled).toBe(1);
  });
});

test.describe("Wellbeing Studio on dev: Guided journaling after consenting (live)", () => {
  test.beforeEach((_fixtures, testInfo) => {
    testInfo.skip(
      !live || !process.env.E2E_USER || !process.env.E2E_PASSWORD,
      "live: needs E2E_BASE_URL, E2E_USER, E2E_PASSWORD",
    );
  });

  test("consent, then a real run that succeeds", async ({ page }) => {
    test.setTimeout(240_000);
    await page.goto("/login");
    await page.fill('input[type="email"]', process.env.E2E_USER!);
    await page.fill('input[type="password"]', process.env.E2E_PASSWORD!);
    await page.keyboard.press("Enter");
    await page.waitForURL((url) => !url.pathname.startsWith("/login"));

    await page.goto("/studios/guided-journaling");
    await page
      .getByRole("textbox")
      .first()
      .fill("This week was long. Monday went badly, Thursday was better, and Friday evening I finally rested.");
    await page.getByRole("button", { name: /^Run/ }).click();
    // First time: the consent step. Later runs of the same persona go straight through.
    const dialog = page.getByRole("dialog", { name: /Before you start/ });
    if (await dialog.isVisible({ timeout: 15_000 }).catch(() => false)) await agree(page, "CA-QC");
    await page.waitForURL(/\/studios\/runs\/[0-9a-f-]{36}/, { timeout: 60_000 });
    const runId = page.url().split("/").pop()!;
    test.info().annotations.push({ type: "run", description: runId });
    await expect
      .poll(async () => (await (await page.request.get(`/api/v1/studios/runs/${runId}`)).json()).status, {
        timeout: 180_000,
        intervals: [3_000],
      })
      .toBe("SUCCEEDED");
  });
});
