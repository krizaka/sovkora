import { jobKind, jobMediaUrl, jobPrompt } from "@/core/jobs/jobMedia";
import { fill } from "@/core/context/fill";

describe("jobMedia", () => {
  it("reads the kind from the capability key", () => {
    expect(jobKind({ featureKey: "orazaka.core.media.image" })).toBe("image");
    expect(jobKind({ featureKey: "orazaka.core.media.video" })).toBe("video");
    expect(jobKind({ featureKey: "orazaka.core.media.speech" })).toBe("audio");
    expect(jobKind({ featureKey: "orazaka.core.chat.completion" })).toBe("text");
  });

  it("gives a media URL only for a completed job that published one", () => {
    expect(jobMediaUrl({ status: "COMPLETED", result: { url: "/api/v1/assets/j/image.png" } })).toBe(
      "/api/v1/assets/j/image.png",
    );
    expect(jobMediaUrl({ status: "PROCESSING", result: { url: "/x" } })).toBeNull();
    expect(jobMediaUrl({ status: "COMPLETED", result: {} })).toBeNull();
  });

  it("reads the prompt (prompt, then text)", () => {
    expect(jobPrompt({ payload: { prompt: " A lamp " } })).toBe("A lamp");
    expect(jobPrompt({ payload: { text: "Hello" } })).toBe("Hello");
    expect(jobPrompt({ payload: {} })).toBeNull();
  });

  it("fills placeholders and leaves unknown ones", () => {
    expect(fill("{done} of {total}", { done: 2, total: 5 })).toBe("2 of 5");
    expect(fill("{x} {y}", { x: 1 })).toBe("1 {y}");
  });
});
