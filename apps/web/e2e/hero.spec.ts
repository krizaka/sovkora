import { test, expect } from "@playwright/test";

/**
 * The hero is LCP-safe and does not move: the heading is the first paint, the recorded take reserves its box (width, height,
 * aspect ratio) and its poster is the finished answer, so nothing shifts while the video loads or plays (CLS 0).
 */
for (const [name, viewport] of [
  ["desktop", { width: 1440, height: 900 }],
  ["phone", { width: 390, height: 844 }],
] as const) {
  test(`the hero keeps its box while the recorded take loads (${name})`, async ({ page }) => {
    await page.setViewportSize(viewport);
    await page.addInitScript(() => {
      (window as unknown as { shifts: number }).shifts = 0;
      new PerformanceObserver((list) => {
        for (const e of list.getEntries() as unknown as { value: number; hadRecentInput: boolean }[])
          if (!e.hadRecentInput) (window as unknown as { shifts: number }).shifts += e.value;
      }).observe({ type: "layout-shift", buffered: true });
    });
    await page.goto("/");
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    const take = page.getByTestId("clip-take");
    await expect(take).toHaveAttribute(
      "poster",
      /sites\/orazaka\/home\/take-en-(dark|light)-poster\.[0-9a-f]{10}\.webp$/,
    );
    await expect(take).toHaveAttribute("width", "1140");
    await expect(take).toHaveAttribute("height", "960");
    const boxes = new Set<number>();
    for (let t = 0; t < 6; t++) {
      boxes.add(Math.round((await take.boundingBox())!.height));
      await page.waitForTimeout(400);
    }
    expect(boxes.size).toBe(1);
    expect(await page.evaluate(() => (window as unknown as { shifts: number }).shifts)).toBeLessThan(0.01);
  });
}
