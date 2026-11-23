import "@testing-library/jest-dom";
import { render, screen } from "@testing-library/react";
import { translations, type Locale } from "@/core/context/translations";
import { DocsHub } from "@/features/docs/components/DocsHub";

const mockLocale: Locale = "en";
jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: translations[mockLocale], locale: mockLocale, setLocale: jest.fn() }),
}));

jest.mock("@/features/docs/components/DocsSearch", () => ({ DocsSearch: () => null }));

beforeAll(() => {
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    value: (query: string) => ({ matches: query.includes("reduce"), media: query, addEventListener: () => {} }),
  });
});

describe("the docs hub", () => {
  it("features Packs in one block right after the quickstart, linking the catalogue", () => {
    const { container } = render(<DocsHub index={[]} journeys={[]} quickstart="npx orazaka install" popular={[]} />);
    const d = translations.en.docs;
    const link = screen.getByRole("link", { name: new RegExp(d.packsEntry.title) });
    expect(link).toHaveAttribute("href", "/docs/packs");
    expect(link).toHaveTextContent(d.packsEntry.line);
    const code = container.querySelector("pre")!;
    expect(code.compareDocumentPosition(link) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("shows the recording the media registry gives the hub, with the guide it shows (krizaka-internal#4635)", () => {
    render(<DocsHub index={[]} journeys={[]} quickstart="npx sovkora install" popular={[]} />);
    const d = translations.en.docs;
    const media = screen.getByTestId("docs-media");
    expect(media).toHaveTextContent(d.media.title);
    expect(screen.getByTestId("clip-phone-studio")).toHaveAttribute(
      "aria-label",
      translations.en.clips["phone-studio"].alt,
    );
    expect(screen.getByRole("link", { name: d.media.cta })).toHaveAttribute("href", "/docs/studio-guide");
  });
});
