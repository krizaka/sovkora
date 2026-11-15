import type { Page } from "@playwright/test";
import { signInAsDemoUser } from "./mock-session";

/**
 * Signs the page in as the demo user and answers the BFF with no backend: `answer` returns the body for the URLs a spec cares
 * about (or `undefined`), the jobs and the settings come in their shape, every other list is empty (a 4xx would sign the page out).
 */
export async function mockApi(page: Page, answer: (url: string) => unknown): Promise<void> {
  await signInAsDemoUser(page);
  await page.route("**/api/v1/**", async (route) => {
    const url = route.request().url();
    let body = answer(url);
    if (body === undefined) {
      if (url.includes("/jobs")) {
        body = { content: [], totalPages: 0, totalElements: 0 };
      } else if (url.includes("/settings")) {
        body = { id: "e2e-user", username: "Eric" };
      } else {
        body = [];
      }
    }
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body) });
  });
}
