import "@testing-library/jest-dom";
import { render } from "@testing-library/react";
import { translations, type Locale } from "@/core/context/translations";
import { DocArticle } from "@/features/docs/components/DocArticle";
import { FOLDED_JOURNEY, JOURNEY_IDS, type NavItem } from "@/features/docs/utils/docs-shared";

const mockLocale: Locale = "en";
jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: translations[mockLocale], locale: mockLocale, setLocale: jest.fn() }),
}));

const nav: NavItem[] = JOURNEY_IDS.map((journey, i) => ({
  slug: `page-${i}`,
  title: `Page ${i}`,
  journey,
  group: null,
}));

describe("the docs sidebar (krizaka/orazaka#443)", () => {
  it("is drawn twice by the template (desktop column, mobile disclosure) without a duplicate id and with every section named", () => {
    const { container } = render(
      <DocArticle
        slug="page-0"
        journey={JOURNEY_IDS[0]}
        title="T"
        intro="I"
        audience="developer"
        generated={false}
        editUrl={null}
        toc={[]}
        nav={nav}
        previous={null}
        next={null}
      >
        body
      </DocArticle>,
    );
    const navs = container.querySelectorAll(`nav[aria-label="${translations.en.docs.sidebarLabel}"]`);
    expect(navs).toHaveLength(2);
    const ids = [...container.querySelectorAll("[id]")].map((el) => el.id);
    expect(ids.filter((id, i) => ids.indexOf(id) !== i)).toEqual([]);
    for (const section of container.querySelectorAll("[aria-labelledby]")) {
      const label = container.querySelector(`[id="${section.getAttribute("aria-labelledby")}"]`);
      expect(label?.textContent?.trim()).toBeTruthy();
    }
    // every journey but the folded one is a named section in each copy; the folded one is a <details> with a summary
    expect(container.querySelectorAll("section[aria-labelledby]")).toHaveLength(
      (JOURNEY_IDS.length - (JOURNEY_IDS.includes(FOLDED_JOURNEY) ? 1 : 0)) * 2,
    );
  });
});
