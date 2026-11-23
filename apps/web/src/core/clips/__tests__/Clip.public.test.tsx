import "@testing-library/jest-dom";
import { render, screen } from "@testing-library/react";
import { translations } from "@/core/context/translations";
import { Clip } from "@/core/clips/Clip";

jest.mock("@/core/config/build-target", () => ({ publicBuild: true }));
jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: translations.fr, locale: "fr", setLocale: jest.fn() }),
}));
jest.mock("@/core/clips/useLook", () => ({ useLook: () => "light" }));

beforeAll(() => {
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    value: (query: string) => ({ matches: query.includes("reduce"), media: query, addEventListener: () => {} }),
  });
});

describe("a demo clip in the public export (krizaka-internal#4635)", () => {
  it("is not shown when the media registry blocks it (the retired code name on screen)", () => {
    const { container } = render(<Clip id="take" />);
    expect(container).toBeEmptyDOMElement();
  });

  it("shows a test recording under the product's name, its captions written into the page in the build's language", () => {
    const { container } = render(<Clip id="phone-studio" />);
    const video = screen.getByTestId("clip-phone-studio");
    expect(video).toHaveAttribute("aria-label", translations.fr.clips["phone-studio"].alt);
    expect(video.getAttribute("poster")).toContain("/sites/sovkora/mobile/studio-run-light-916-poster.");
    const tracks = container.querySelectorAll("track");
    expect(tracks).toHaveLength(1);
    expect(tracks[0]).toHaveAttribute("srcLang", "fr");
    const src = tracks[0].getAttribute("src") ?? "";
    expect(src.startsWith("data:text/vtt;charset=utf-8,")).toBe(true);
    expect(decodeURIComponent(src)).toContain(translations.fr.clips["phone-studio"].cues[0]);
  });
});
