import { test, expect, type Page } from "@playwright/test";
import { encode } from "next-auth/jwt";

/**
 * The Studios list and « Plan & packs » (mocked, no backend, with the shapes the Studio and billing services serve).
 *
 * 1. Find a Studio by search, then run it: a VERTICAL Studio nobody set up is installed by its first run (no Install
 *    step), then the run starts and its page opens.
 * 2. See what my plan includes: the plan line on /studios, the plan and the packs on /packs, a pack's Studios in its
 *    drawer (`?pack=`, Back closes it).
 */
const E2E_SECRET = "e2e-only-secret-not-used-anywhere-else";

const summary = (key: string, label: string, over: Record<string, unknown> = {}) => ({
  studioKey: key,
  label,
  tagline: null,
  profession: "general",
  iconKey: "studio",
  heroAssetId: null,
  pricing: "INCLUDED",
  kind: "TOOLKIT",
  latestVersion: "1.0.0",
  locked: false,
  lockedReason: "NONE",
  packKey: null,
  ...over,
});
const studios = [
  summary("image-generation", "Image generation", { tagline: "A picture from a sentence." }),
  summary("art-golden-hour", "Golden hour", { profession: "creative" }),
  summary("mood-tracking", "Mood tracking", {
    kind: "VERTICAL",
    pricing: "FREE",
    tagline: "How the week felt, in your words.",
  }),
  summary("creator-suggestions", "Creator suggestions", {
    profession: "creative",
    kind: "VERTICAL",
    pricing: "PAID",
    locked: true,
    lockedReason: "REQUIRES_PURCHASE",
    packKey: "data-analytics",
  }),
  summary("media-safety", "Media safety", {
    kind: "VERTICAL",
    pricing: "FREE",
    locked: true,
    lockedReason: "REQUIRES_PLAN",
  }),
];
const pack = (packKey: string, label: string, over: Record<string, unknown>) => ({
  packKey,
  categoryKey: "creativity-media",
  label,
  tagline: null,
  iconKey: "studio",
  heroAssetId: null,
  regulatoryClass: "STANDARD",
  kind: "VERTICAL",
  access: "BUYABLE",
  studioKeys: [],
  priceCents: 0,
  includedCredits: null,
  ...over,
});
const packs = [
  pack("orazaka-media", "Media", {
    kind: "TOOLKIT",
    access: "INCLUDED",
    studioKeys: ["image-generation", "art-golden-hour"],
  }),
  pack("bien-etre", "Wellbeing", { categoryKey: "wellbeing", studioKeys: ["mood-tracking"] }),
  pack("data-analytics", "Data & analytics", {
    categoryKey: "data-analytics",
    priceCents: 4900,
    studioKeys: ["creator-suggestions"],
  }),
];
const plans = [
  {
    planKey: "free",
    label: "Free",
    tierRank: 1,
    monthlyCreditGrant: 500,
    priceCents: 0,
    currency: "CAD",
    rateLimitTierKey: "free",
    isPublic: true,
    isActive: true,
    entitlements: [],
  },
];
const detail = {
  ...summary("mood-tracking", "Mood tracking", { kind: "VERTICAL", pricing: "FREE" }),
  description: null,
  status: "PUBLISHED",
  estimatedCredits: 2,
  inputSchema: JSON.stringify({
    type: "object",
    required: ["notes"],
    properties: { notes: { type: "string", title: "Your notes", minLength: 3 } },
  }),
  configSchema: null,
};

interface Calls {
  installed: string[];
  runs: string[];
  subscribed: string[];
}

async function signIn(page: Page): Promise<Calls> {
  const calls: Calls = { installed: [], runs: [], subscribed: [] };
  const token = await encode({
    token: { name: "Eric", email: "eric@orazaka.dev", id: "e2e-user", role: "user" },
    secret: E2E_SECRET,
  });
  await page.context().addCookies([{ name: "next-auth.session-token", value: token, url: "http://localhost:3000" }]);
  await page.route("**/api/v1/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const json = (body: unknown, status = 200) =>
      route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
    if (request.method() === "POST" && path === "/api/v1/studios/mood-tracking/installations") {
      calls.installed.push("mood-tracking");
      return json(
        {
          id: "inst-1",
          studioKey: "mood-tracking",
          label: "Mood tracking",
          iconKey: "heart",
          pinnedVersion: "1.0.0",
          latestVersion: "1.0.0",
          status: "ACTIVE",
          config: {},
          installedAt: "2026-10-20T12:00:00Z",
          lastRunAt: null,
        },
        201,
      );
    }
    if (request.method() === "POST" && path === "/api/v1/studios/installations/inst-1/runs") {
      calls.runs.push("inst-1");
      return json(
        {
          id: "22222222-2222-2222-2222-222222222222",
          installationId: "inst-1",
          studioKey: "mood-tracking",
          blueprintVersion: "1.0.0",
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
    if (request.method() === "POST" && path.startsWith("/api/v1/billing/pack-subscriptions")) {
      calls.subscribed.push(path);
      return json(
        {
          id: "s1",
          actorId: "e2e-user",
          packKey: "data-analytics",
          status: "ACTIVE",
          subscribedAt: "2026-10-20T12:00:00Z",
          periodEnd: null,
        },
        201,
      );
    }
    if (path === "/api/v1/studios") return json(studios);
    if (path === "/api/v1/studios/packs") return json(packs);
    if (path === "/api/v1/studios/packs/categories") return json([]);
    if (path === "/api/v1/studios/installations") return json([]);
    if (path === "/api/v1/studios/mood-tracking") return json(detail);
    if (path === "/api/v1/billing/wallets/me/entitlements")
      return json({
        actorId: "e2e-user",
        planKey: "free",
        entitlements: {},
        availableCredits: 500,
        expiresAt: "2026-11-01T00:00:00Z",
      });
    if (path === "/api/v1/billing/plans") return json(plans);
    if (path.includes("/jobs")) return json({ content: [], totalPages: 0, totalElements: 0 });
    if (path.includes("/settings")) return json({ id: "e2e-user", username: "Eric" });
    return json([]);
  });
  return calls;
}

test.describe("Studios: one list, plan on top (mocked)", () => {
  test("finds a Studio by search, then runs it, with no Install step", async ({ page }) => {
    const calls = await signIn(page);
    await page.goto("/studios");

    await page.getByRole("searchbox", { name: "Search Studios" }).fill("mood");
    await expect(page).toHaveURL(/\?q=mood/);
    await expect(page.getByRole("link", { name: "Run Mood tracking" })).toBeVisible();
    await expect(page.getByRole("link", { name: "Run Image generation" })).toHaveCount(0);

    await page.getByRole("link", { name: "Run Mood tracking" }).click();
    await page.waitForURL(/\/studios\/mood-tracking$/);
    await expect(page.getByRole("button", { name: "Install" })).toHaveCount(0);

    await page.getByRole("textbox", { name: /Your notes/ }).fill("A calm week, two hard days.");
    await page.getByRole("button", { name: /^Run/ }).click();
    await page.waitForURL(/\/studios\/runs\//);
    expect(calls.installed).toEqual(["mood-tracking"]);
    expect(calls.runs).toEqual(["inst-1"]);
  });

  test("sees what the plan includes, and what a pack would add", async ({ page }) => {
    await signIn(page);
    await page.goto("/studios");

    const planLine = page.getByText("Free plan").first();
    await expect(planLine).toBeVisible();
    await expect(page.getByText("3 Studios ready")).toBeVisible();

    // What else exists, and the one action that gets it: the pack opens in place.
    await page.getByRole("radio", { name: /Get more/ }).click();
    await expect(page).toHaveURL(/show=more/);
    await page.getByRole("button", { name: "Get the pack for Creator suggestions" }).click();
    await expect(page).toHaveURL(/pack=data-analytics/);
    const drawer = page.getByRole("dialog", { name: "Data & analytics" });
    await expect(drawer.getByRole("link", { name: "Creator suggestions" })).toBeVisible();
    await expect(drawer.getByRole("button", { name: "Add to my account" })).toBeVisible();
    await page.goBack();
    await expect(drawer).toHaveCount(0);

    // Plan & packs: the plan, the packs it includes, the others.
    await page.getByRole("link", { name: "Plan & packs" }).first().click();
    await page.waitForURL(/\/packs/);
    await expect(page.getByRole("heading", { level: 1, name: "Plan & packs" })).toBeVisible();
    const mine = page.getByRole("list", { name: "Your packs" });
    await expect(mine.getByText("Media")).toBeVisible();
    await expect(mine.getByText(/Image generation, Golden hour/)).toBeVisible();
    await expect(page.getByRole("list", { name: "More packs" }).getByText("Data & analytics")).toBeVisible();
  });
});
