import "@testing-library/jest-dom";
import { fireEvent, render, screen, within } from "@testing-library/react";
import { translations, type Locale } from "@/core/context/translations";
import { DocBody } from "@/features/docs/components/DocBody";
import { PackIndexView, PackView } from "@/features/docs/components/PackCatalogue";
import { readDoc, splitSegments, tocOf } from "@/features/docs/utils/docs";
import { parsePack, parsePackIndex, type PackData } from "@/features/docs/utils/packs";

const mockLocale: Locale = "en";
jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: translations[mockLocale], locale: mockLocale, setLocale: jest.fn() }),
}));

const pack: PackData = {
  key: "document-validation",
  title: "Document Verification",
  tagline: "Check a contract.",
  description: "Clause by clause, against your rules.",
  version: "1.1.0",
  tier: "WORKER",
  kind: null,
  distribution: "CLOUD",
  regulatoryClass: "SENSITIVE",
  category: "Legal",
  listed: true,
  workers: ["validation"],
  controls: { scopeGuard: true, consent: false, safety: false },
  rulesets: [{ label: "Quebec lease", jurisdiction: "CA-QC", rules: 12, versions: ["2026-01-01", "2026-06-01"] }],
  studios: [
    {
      key: "check",
      label: "Check",
      summary: "Is it valid?",
      description: "Reads every clause.",
      inputs: [{ key: "doc", title: "Document" }],
      steps: 3,
      stepKinds: ["CAPABILITY"],
      credits: 90,
    },
    {
      key: "compare",
      label: "Compare",
      summary: "Two versions.",
      description: null,
      inputs: [],
      steps: 1,
      stepKinds: ["TRANSFORM"],
      credits: null,
    },
  ],
};

describe("PackView", () => {
  it("lays a pack out as chips, a calm note, a ruleset list and Studio cards — no table", () => {
    const { container } = render(<PackView pack={pack} />);
    expect(container.querySelector("table")).toBeNull();
    const facts = screen.getByRole("list", { name: translations.en.docs.packs.facts });
    expect(within(facts).getByText(/SENSITIVE/)).toBeInTheDocument();
    expect(within(facts).getByText(/Own worker validation/)).toBeInTheDocument();
    expect(screen.getByRole("note")).toHaveTextContent(translations.en.docs.packs.guard);
    const rulesets = screen.getByRole("region", { name: translations.en.docs.packs.rulesets });
    expect(rulesets).toHaveTextContent("Quebec lease");
    expect(rulesets).toHaveTextContent("CA-QC");
    expect(rulesets).toHaveTextContent("2026-06-01");
    expect(container.querySelectorAll("h4").length).toBeGreaterThanOrEqual(2);
  });

  it("shows a Studio's name, id, two-line summary, inputs, steps and credits; the rest folds", () => {
    render(<PackView pack={pack} />);
    expect(screen.getByRole("heading", { name: "Check" })).toBeInTheDocument();
    expect(screen.getByText("check")).toBeInTheDocument();
    expect(screen.getByText("Document")).toBeInTheDocument();
    expect(screen.getByText("90")).toBeInTheDocument();
    expect(screen.getAllByText(translations.en.docs.packs.more)).toHaveLength(1);
    expect(screen.getByText(translations.en.docs.packs.noInputs)).toBeInTheDocument();
  });
});

describe("PackIndexView", () => {
  const index = parsePackIndex(
    JSON.stringify([
      { key: "a", title: "Real-Estate Studio", tagline: "Reels.", tier: "DATA", category: "Sales", studios: 1 },
      {
        key: "b",
        title: "Document Verification",
        tagline: "Contracts.",
        tier: "WORKER",
        category: "Legal",
        studios: 2,
      },
    ]),
  );

  it("filters by tier and says how many remain; each card leads to its section", () => {
    render(<PackIndexView items={index} />);
    expect(screen.getAllByRole("link")).toHaveLength(2);
    fireEvent.click(screen.getByRole("button", { name: "WORKER" }));
    expect(screen.getAllByRole("link").map((a) => a.getAttribute("href"))).toEqual(["#document-verification"]);
    expect(screen.getByRole("status")).toHaveTextContent("1 of 2 packs");
    fireEvent.click(screen.getByRole("button", { name: "DATA" }));
    fireEvent.click(screen.getByRole("button", { name: "Legal" }));
    expect(screen.getByText(translations.en.docs.packs.none)).toBeInTheDocument();
  });
});

describe("the Packs & Studios page", () => {
  it("renders every pack as cards: no table for the Studios, and every block parses", () => {
    const doc = readDoc("packs")!;
    const segments = splitSegments(doc.body);
    const blocks = segments.filter((s) => s.kind === "diagram" && s.lang === "pack");
    expect(blocks.length).toBeGreaterThanOrEqual(3);
    for (const b of blocks) if (b.kind === "diagram") expect(parsePack(b.source).studios.length).toBeGreaterThan(0);
    // the contents list one entry per pack (their ### headings), after « The packs »
    const toc = tocOf(segments).map((t) => t.label);
    expect(toc).toEqual(expect.arrayContaining(["The packs", "Document Verification", "Real-Estate Studio"]));
    const { container } = render(<DocBody segments={segments} />);
    const headers = [...container.querySelectorAll("table thead th")].map((th) => th.textContent?.trim());
    expect(headers).not.toContain("Studio");
    expect(container.querySelectorAll("[data-pack]").length).toBe(blocks.length);
    for (const table of container.querySelectorAll("table")) {
      // a wall of columns is how the Studios used to read; the remaining tables are the manifest reference
      expect(table.querySelectorAll("thead th").length === 0 || !/Credits held/.test(table.textContent ?? "")).toBe(
        true,
      );
    }
  });
});
