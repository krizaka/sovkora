import { test, expect, type Page } from "@playwright/test";
import { mockEmptyBackend, signInAsDemoUser } from "./support/mock-session";
import { assertTextReadable } from "../scripts/text-width-on-screen.mjs";
import { RECORDED_DOCUMENT, RECORDED_QUESTION } from "../scripts/tour/eric.mjs";

/**
 * The chat bubbles keep a readable measure (krizaka-internal#3896): the recorded question was squeezed to 245px beside its avatar.
 * The persona's recorded conversation (the question with the policy pasted under it, and an answer) is seeded as the history the
 * page reads; at the recorded 1280px and on a phone (360, 390), light and dark, no bubble text is squeezed. Mocked, no backend.
 */
const CONVERSATION_ID = "22222222-2222-2222-2222-222222222222";
const USER_ID = "e2e-user"; // the id of the session cookie (support/mock-session.ts)
const THREAD_TITLE = "Ms Laurent wants to return her custom-glaze Halo lamp after 20 days";
const ANSWER =
  "Her lamp is made to order, so the policy does not allow a return after 20 days unless it is defective. Tell her we cannot take it back, and offer a repair if the glaze is damaged.";

async function seedConversation(page: Page) {
  await signInAsDemoUser(page);
  await mockEmptyBackend(page, [{ id: CONVERSATION_ID, title: THREAD_TITLE, updatedAt: new Date().toISOString() }]);
  const now = Date.now();
  const messages = [
    {
      id: "m1",
      role: "user",
      kind: "text",
      content: `${RECORDED_QUESTION}\n\n${RECORDED_DOCUMENT}`,
      timestamp: now - 2000,
    },
    { id: "m2", role: "assistant", kind: "text", content: ANSWER, timestamp: now - 1000 },
  ];
  // The history is read from the browser's storage under the app's own key; answer that one key (whatever its prefix) for this thread.
  await page.addInitScript(
    ([suffix, value]) => {
      const read = Storage.prototype.getItem;
      Storage.prototype.getItem = function (key: string) {
        return key.endsWith(suffix) ? value : read.call(this, key);
      };
    },
    [`_messages_${USER_ID}_${CONVERSATION_ID}`, JSON.stringify(messages)],
  );
}

for (const width of [1280, 390, 360]) {
  for (const colorScheme of ["light", "dark"] as const) {
    test(`the chat bubbles keep their text readable at ${width}px, ${colorScheme}`, async ({ page }) => {
      await page.setViewportSize({ width, height: 800 });
      await page.emulateMedia({ colorScheme });
      await seedConversation(page);
      await page.goto(`/chat?conversationId=${CONVERSATION_ID}`);
      await expect(page.getByTestId("chat-message-user")).toBeVisible();
      await expect(page.getByTestId("chat-message-assistant")).toBeVisible();
      await page.waitForTimeout(600); // the bubbles' entrance spring
      await assertTextReadable(page, `chat ${width}px ${colorScheme}`);
    });
  }
}
