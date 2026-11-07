import { test, expect, type Page } from "@playwright/test";
import { encode } from "next-auth/jwt";

/**
 * The video Studio's « Model » is a populated, readable list: a short name and a line per model, the facts of the model
 * in view as chips, every model side by side under « Compare models », and a run that carries the chosen id.
 *
 * Mocked (no backend), as the other suites, with the shape the Studio service serves: the catalogue's `oneOf` of
 * `const` written into the schema, and the pack's words in both languages (`x-i18n`).
 *
 * Live (needs E2E_BASE_URL and the QA sign-in of live-run-image.spec.ts): the same on www.orazaka.com, then a real run
 * on the chosen model (E2E_LIVE_RUN=1 starts it: it is the one that costs a few cents).
 */
const E2E_SECRET = "e2e-only-secret-not-used-anywhere-else";

const schema = {
  type: "object",
  required: ["prompt"],
  properties: {
    prompt: {
      type: "string",
      format: "prose",
      minLength: 3,
      maxLength: 2000,
      title: "Ce qui doit se passer à l'écran",
    },
    model: {
      type: "string",
      title: "Modèle",
      description: "Auto choisit le meilleur modèle disponible.",
      "x-options": {
        source: "models",
        category: "video",
        facts: { ltx: ["1216×704", "5 s"], wan: ["832×480", "5 s"] },
      },
      "x-i18n": {
        en: {
          title: "Model",
          description: "Auto picks the best model that is running.",
          options: {
            ltx: { label: "LTX-Video · fast", hint: "Image or text to video in about 15 s." },
            wan: { label: "Wan 2.2 · cloud", hint: "Text to video, hosted." },
          },
        },
        fr: {
          title: "Modèle",
          description: "Auto choisit le meilleur modèle disponible.",
          options: {
            ltx: { label: "LTX-Video · rapide", hint: "Image ou texte vers vidéo en 15 s." },
            wan: { label: "Wan 2.2 · infonuagique", hint: "Texte vers vidéo, hébergé." },
          },
        },
      },
      default: "wan",
      oneOf: [
        {
          const: "wan",
          title: "Wan 2.2 T2V A14B (serverless)",
          "x-meta": { runsOn: "cloud", speed: "slow", typicalSeconds: 131, priceCents: 60, available: true },
        },
        {
          const: "ltx",
          title: "LTX-Video 0.9.8",
          "x-meta": {
            runsOn: "local",
            typicalSeconds: 15,
            available: false,
            unavailableReason: "LTX-Video is not running",
          },
        },
      ],
    },
  },
};
const detail = {
  studioKey: "video-generation",
  label: "Video generation",
  tagline: "A vertical short.",
  description: null,
  profession: "general",
  iconKey: "video",
  heroAssetId: null,
  pricing: "INCLUDED",
  kind: "TOOLKIT",
  status: "PUBLISHED",
  latestVersion: "1.8.0",
  estimatedCredits: 3600,
  inputSchema: JSON.stringify(schema),
  configSchema: "{}",
  locked: false,
  lockedReason: "NONE",
  packKey: null,
};

type Started = { inputs?: Record<string, unknown> };

async function signInMocked(page: Page, started: Started, inputSchema = detail.inputSchema) {
  const token = await encode({
    token: { name: "Eric", email: "eric@orazaka.dev", id: "e2e-user", role: "user" },
    secret: E2E_SECRET,
  });
  await page.context().addCookies([{ name: "next-auth.session-token", value: token, url: "http://localhost:3000" }]);
  await page.route("**/api/v1/**", async (route) => {
    const request = route.request();
    const url = request.url();
    const json = (body: unknown, status = 200) =>
      route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
    if (request.method() === "POST" && url.includes("/studios/video-generation/runs")) {
      started.inputs = (request.postDataJSON() as { inputs: Record<string, unknown> }).inputs;
      return json(
        {
          id: "11111111-1111-1111-1111-111111111111",
          installationId: null,
          studioKey: "video-generation",
          blueprintVersion: "1.8.0",
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
    if (/\/studios\/video-generation(\?|$)/.test(url)) return json({ ...detail, inputSchema });
    if (url.includes("/jobs")) return json({ content: [], totalPages: 0, totalElements: 0 });
    if (url.includes("/settings")) return json({ id: "e2e-user", username: "Eric" });
    return json([]);
  });
}

test.describe("video Studio — the model list (mocked)", () => {
  test("opens populated, shows the chips of the chosen model, compares them, and runs", async ({ page }) => {
    const started: Started = {};
    await signInMocked(page, started);
    await page.goto("/studios/video-generation");

    const model = page.getByRole("combobox", { name: "Model" });
    await expect(model).toBeVisible();
    const helper = page.getByText("Auto picks the best model that is running.");
    await expect(helper).toBeVisible();
    expect((await helper.innerText()).length).toBeLessThanOrEqual(140); // a line, not the catalogue

    // the model that will run, its facts as chips
    const chips = page.getByRole("list", { name: "About this model: Wan 2.2 · cloud" });
    await expect(chips).toContainText("Cloud");
    await expect(chips).toContainText("$0.60 per run");
    await expect(chips).toContainText("832×480");

    // the list: a short name and a line; the one that is off cannot be chosen and says why
    await model.click();
    const options = page.getByRole("listbox").getByRole("option");
    await expect(options).toHaveCount(2);
    await expect(options.nth(0)).toContainText("Text to video, hosted.");
    await expect(options.nth(1)).toHaveAttribute("aria-disabled", "true");
    await expect(options.nth(1)).toContainText("LTX-Video is not running");
    await page.keyboard.press("Escape");

    // every model side by side
    await page.getByText("Compare models").click();
    await expect(page.getByText("1216×704")).toBeVisible();

    await page.getByRole("textbox", { name: /What should happen/ }).fill("A paper boat crossing a puddle");
    await page.getByRole("button", { name: "Run" }).click();
    await page.waitForURL(/\/studios\/runs\//);
    // « Automatic »: the platform picks, so the form sends no model
    expect(started.inputs).toEqual({ prompt: "A paper boat crossing a puddle" });
  });

  test("an empty catalogue list is an error you can read, not a blank select", async ({ page }) => {
    const empty = { ...schema, properties: { ...schema.properties, model: { ...schema.properties.model, oneOf: [] } } };
    await signInMocked(page, {}, JSON.stringify(empty));
    await page.goto("/studios/video-generation");
    await expect(
      page.getByRole("alert").filter({ hasText: "No model available: contact the administrator." }),
    ).toBeVisible();
    await expect(page.getByRole("combobox", { name: "Model" })).toHaveCount(0);
  });
});

const live = Boolean(process.env.E2E_BASE_URL && process.env.E2E_USER);

test.describe("video Studio — the model list (live on dev)", () => {
  test.skip(!live, "live: needs E2E_BASE_URL and the QA sign-in");

  test("the model is a populated list with chips, and a real run starts on the chosen model", async ({ page }) => {
    await page.goto("/login");
    await page.fill('input[type="email"]', process.env.E2E_USER!);
    await page.fill('input[type="password"]', process.env.E2E_PASSWORD!);
    await page.keyboard.press("Enter");
    await page.waitForURL((url) => !url.pathname.startsWith("/login"));
    await page.goto("/studios/video-generation", { waitUntil: "networkidle" });

    const model = page.getByRole("combobox", { name: /Model|Modèle/ });
    await expect(model).toBeVisible();
    await model.click();
    const options = page.getByRole("listbox").getByRole("option");
    expect(await options.count()).toBeGreaterThanOrEqual(2);
    // no option is a raw id or a paragraph
    for (const text of await options.allInnerTexts()) expect(text.length).toBeLessThan(220);
    await options.and(page.locator(':not([aria-disabled="true"])')).first().click();
    await expect(page.getByRole("list", { name: /About this model|À propos de ce modèle/ })).toBeVisible();

    if (process.env.E2E_LIVE_RUN) {
      await page
        .getByRole("textbox", { name: /What should happen|Ce qui doit se passer/ })
        .fill("A paper boat crossing a puddle, slow dolly in");
      await page.getByRole("button", { name: /^(Run|Lancer)/ }).click();
      await page.waitForURL(/\/studios\/runs\//, { timeout: 60_000 });
    }
  });
});
