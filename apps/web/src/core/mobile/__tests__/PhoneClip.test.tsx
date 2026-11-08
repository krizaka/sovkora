import { readFileSync } from "node:fs";
import { join } from "node:path";
import { render, screen } from "@testing-library/react";
import { PhoneClip } from "@/core/mobile/PhoneClip";

describe("PhoneClip", () => {
  it("is a recording of the real app in dark and in light, played only on demand — never a drawn screen", () => {
    const { container } = render(
      <PhoneClip id="ask" label="Ask" alt="A request is typed and the answer streams in." />,
    );
    const videos = Array.from(container.querySelectorAll("video"));
    expect(videos.map((v) => v.getAttribute("data-appearance")).sort()).toEqual(["dark", "light"]);
    for (const video of videos) {
      expect(video.getAttribute("preload")).toBe("none");
      expect(video.hasAttribute("autoplay")).toBe(false);
      expect(video.getAttribute("poster")).toMatch(/^https:\/\/.*ask-(dark|light)-916-poster\.[0-9a-f]{10}\.webp$/);
      expect(Array.from(video.querySelectorAll("source")).map((s) => s.getAttribute("type"))).toEqual([
        "video/webm",
        "video/mp4",
      ]);
    }
    // Which one is shown is the stylesheet's job (html.light hides the dark recording): the rule is in globals.css.
    expect(readFileSync(join(process.cwd(), "src/app/globals.css"), "utf8")).toMatch(
      /html\.light video\[data-appearance="dark"\]/,
    );
    expect(screen.getAllByLabelText(/Ask\. A request is typed/)).toHaveLength(2);
  });

  it("is only video: no image of a screen and no scripted markup stands in for the app", () => {
    const { container } = render(<PhoneClip id="jobs" label="Jobs" alt="Charts and a list." />);
    expect(container.querySelectorAll("img, canvas, svg")).toHaveLength(0);
  });
});
