import { deriveThreadTitle } from "@/features/chat-session/utils/threadTitle";

describe("deriveThreadTitle", () => {
  test("uses the prompt as the title when short", () => {
    expect(deriveThreadTitle("Plan my week")).toBe("Plan my week");
  });

  test("collapses whitespace and newlines into a single line", () => {
    expect(deriveThreadTitle("  hello\n  world  ")).toBe("hello world");
  });

  test("truncates long prompts with an ellipsis (<= 48 chars + …)", () => {
    const long = "a".repeat(80);
    const title = deriveThreadTitle(long);
    expect(title.endsWith("…")).toBe(true);
    expect(title.length).toBeLessThanOrEqual(49);
  });

  test("falls back to the generic label for empty input", () => {
    expect(deriveThreadTitle("   ")).toBe("New Memory Block");
  });
});
