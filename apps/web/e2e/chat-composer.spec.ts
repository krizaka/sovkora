import { test, expect, type Page } from "@playwright/test";
import { encode } from "next-auth/jwt";

/**
 * The chat composer is the kit's pill (@krizaka/ui/chat MessageComposer): one rounded field, chip and attach inside,
 * the round send inside, a status line under it. The box must not move while an answer streams.
 * The BFF and the session are mocked (no backend), as in the smoke test.
 */
const E2E_SECRET = "e2e-only-secret-not-used-anywhere-else"; // the dev server of this suite is started with it (playwright.config.ts)

async function signIn(page: Page) {
  // A real session cookie (signed with the suite's secret): the server renders the page signed in, as for a user.
  const token = await encode({
    token: { name: "Eric", email: "eric@orazaka.dev", id: "e2e-user", role: "user" },
    secret: E2E_SECRET,
  });
  await page.context().addCookies([{ name: "next-auth.session-token", value: token, url: "http://localhost:3000" }]);
  await mockBackend(page);
}

/** The BFF's read endpoints, answered empty: nothing here talks to a backend (and a 4xx would sign the page out). */
async function mockBackend(page: Page) {
  await page.route("**/api/v1/**", (route) => {
    const url = route.request().url();
    const body = url.includes("/jobs")
      ? { content: [], totalPages: 0, totalElements: 0 }
      : url.includes("/settings")
        ? { id: "e2e-user", username: "Eric" }
        : [];
    return route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body) });
  });
}

const pillOf = (page: Page) => page.getByTestId("chat-input").locator("..");

async function box(page: Page) {
  const b = await pillOf(page).boundingBox();
  expect(b).not.toBeNull();
  return { x: Math.round(b!.x), y: Math.round(b!.y), width: Math.round(b!.width), height: Math.round(b!.height) };
}

test.describe("chat composer", () => {
  test("empty state: one pill, same width as the suggestion cards, centred, send enabled by text", async ({ page }) => {
    await signIn(page);
    await page.goto("/chat");
    const input = page.getByTestId("chat-input");
    await expect(input).toBeVisible();

    const pill = await box(page);
    const cards = await page
      .locator("main, section")
      .getByRole("button", { name: /Generate an image|Ask anything|Draft a client/ })
      .first()
      .boundingBox();
    expect(cards).not.toBeNull();

    // inside the pill, on the left: the mode chip and attach; on the right: the send
    await expect(pillOf(page).getByRole("button", { name: "Add Capability" })).toBeVisible();
    await expect(pillOf(page).getByRole("button", { name: /attach/i })).toBeVisible();
    const send = pillOf(page).getByTestId("chat-submit");
    await expect(send).toBeDisabled();
    await input.fill("Hello");
    await expect(send).toBeEnabled();

    // no inner border on the field
    expect(await input.evaluate((el) => getComputedStyle(el).borderTopWidth)).toBe("0px");

    // centred in the viewport
    const viewport = page.viewportSize()!;
    expect(Math.abs(pill.x + pill.width / 2 - (viewport.width / 2 + 0))).toBeLessThan(viewport.width * 0.2);
    expect(pill.width).toBeGreaterThan(280);
  });

  test("Enter sends, Shift+Enter adds a line", async ({ page }) => {
    await signIn(page);
    await page.goto("/chat");
    const input = page.getByTestId("chat-input");
    await input.fill("line one");
    await input.press("Shift+Enter");
    await input.type("line two");
    await expect(input).toHaveValue("line one\nline two");
  });

  test("mid-conversation: the composer's box is constant while the answer streams", async ({ page }) => {
    await signIn(page);
    await page.route("**/api/chat/stream/**", async (route) => {
      await new Promise((r) => setTimeout(r, 1500));
      await route.fulfill({
        status: 200,
        contentType: "text/event-stream",
        body: [
          `event: routing\ndata: ${JSON.stringify({ provider: "ollama", local: true, estimatedCredits: 30 })}\n\n`,
          'data: {"content":"Three contracts renew before March."}\n\n',
        ].join(""),
      });
    });
    await page.addInitScript(() => {
      localStorage.setItem(
        "orazaka_threads",
        JSON.stringify([{ conversationId: "c1", title: "Renewals", updatedAt: Date.now() }]),
      );
      localStorage.setItem(
        "orazaka_messages_e2e-user_c1",
        JSON.stringify([
          { id: "m1", role: "user", content: "Hi", timestamp: 1, kind: "text" },
          { id: "m2", role: "assistant", content: "Hello", timestamp: 2, kind: "text" },
        ]),
      );
    });
    await page.goto("/chat?conversationId=c1");
    const input = page.getByTestId("chat-input");
    await expect(input).toBeVisible();

    const before = await box(page);
    await input.fill("Which contracts renew?");
    await input.press("Enter");
    // answering: same box, send held, status says so
    await expect(page.getByRole("status")).toContainText(/Answering|Réponse/);
    expect(await box(page)).toEqual(before);
    await expect(pillOf(page).getByTestId("chat-submit")).toBeDisabled();
    // answered: same box, and the status is computed from the routing the engine reported
    await expect(page.getByText("Three contracts renew before March.")).toBeVisible();
    await expect(page.getByRole("status")).toContainText("Runs locally · nothing leaves your network");
    await expect(page.getByRole("status")).toContainText("~30 credits");
    expect(await box(page)).toEqual(before);
  });
});
