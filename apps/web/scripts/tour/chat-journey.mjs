// The chat journey's opening, shared by the tour (record-tour.mjs) and the Download page's videos (showcase-web.mjs): the
// question typed, the policy pasted under it, then sent. The caller waits for the answer in its own way.
import { RECORDED_DOCUMENT, RECORDED_QUESTION } from "./eric.mjs";

/** On the empty composer of /chat: types the recorded question, pastes the recorded document, sends, waits for the conversation. */
export async function askRecordedQuestion(page) {
  await page.waitForTimeout(1400);
  const box = page.locator("textarea").first();
  await box.click();
  await box.pressSequentially(RECORDED_QUESTION, { delay: 9 });
  await page.keyboard.insertText(`\n\n${RECORDED_DOCUMENT}`); // the policy, pasted
  await page.waitForTimeout(900);
  await page.getByRole("button", { name: /^Send/ }).click();
  await page.waitForURL(/conversationId=/, { timeout: 30_000 });
}
