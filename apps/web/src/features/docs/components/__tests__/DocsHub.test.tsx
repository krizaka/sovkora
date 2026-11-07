import "@testing-library/jest-dom";
import { render, screen } from "@testing-library/react";
import { translations, type Locale } from "@/core/context/translations";
import { DocsHub } from "@/features/docs/components/DocsHub";

const mockLocale: Locale = "en";
jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: translations[mockLocale], locale: mockLocale, setLocale: jest.fn() }),
}));

jest.mock("@/features/docs/components/DocsSearch", () => ({ DocsSearch: () => null }));

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
});
