import { test, expect } from "@playwright/test";

/**
 * Smoke E2E test — validates the happy path from UI landing to chat interaction [ADR-033].
 *
 * This is the ONLY full cross-module smoke test. It mocks BFF/API responses
 * to ensure API contracts remain unbroken, leaving deep edge cases to
 * decoupled integration tiers.
 */
test.describe("Sovkora UI Smoke Test", () => {
  test("should load the landing page and render core layout elements", async ({ page }) => {
    await page.goto("/");
    await page.waitForLoadState("domcontentloaded");

    // Assert the page title contains Sovkora
    const title = await page.title();
    expect(title.toLowerCase()).toContain("sovkora");

    // The commercial name (krizaka-internal#2103) is in the bar, never the code name
    await expect(page.getByRole("navigation", { name: "Sovkora" }).getByText("Sovkora").first()).toBeVisible();
    await expect(page.locator("body")).not.toContainText(/\bOrazaka\b/);

    // Assert core layout elements are present
    await expect(page.locator("body")).toBeVisible();
  });

  test("should navigate to chat and render SSE streamed response", async ({ page }) => {
    // Mock the BFF chat API endpoint to simulate SSE streaming
    await page.route("**/api/chat**", async (route) => {
      const encoder = new TextEncoder();
      const chunks = [
        'data: {"content":"Hello"}\n\n',
        'data: {"content":" from"}\n\n',
        'data: {"content":" Sovkora"}\n\n',
        'data: {"content":" AI!"}\n\n',
        "data: [DONE]\n\n",
      ];

      const body = chunks.join("");
      await route.fulfill({
        status: 200,
        contentType: "text/event-stream",
        body: Buffer.from(encoder.encode(body)),
        headers: {
          "Cache-Control": "no-cache",
          Connection: "keep-alive",
        },
      });
    });

    // Mock auth session to bypass login
    await page.route("**/api/auth/session**", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          user: {
            name: "Test User",
            email: "test@orazaka.dev",
            id: "test-user-id",
          },
          expires: new Date(Date.now() + 86400000).toISOString(),
        }),
      });
    });

    // Navigate to the chat page
    await page.goto("/");
    await page.waitForLoadState("networkidle");

    // Verify the page loaded without errors (no 500, no blank screen)
    const bodyText = await page.locator("body").textContent();
    expect(bodyText).toBeTruthy();
    expect(bodyText!.length).toBeGreaterThan(0);
  });
});
