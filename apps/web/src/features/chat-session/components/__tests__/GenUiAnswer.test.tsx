import "@testing-library/jest-dom";
import { render, screen } from "@testing-library/react";

import { GenUiAnswer, hasGenUi } from "@/features/chat-session/components/GenUiAnswer";

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({
    t: { chat: { genui: { surface: "The answer as a dashboard", loading: "Building…", noData: "No data" } } },
    locale: "en",
  }),
}));

// jsdom has no matchMedia nor IntersectionObserver (the KpiStat's number ticker and the charts use them).
beforeAll(() => {
  (globalThis as { IntersectionObserver?: unknown }).IntersectionObserver ??= class {
    observe() {}
    unobserve() {}
    disconnect() {}
    takeRecords() {
      return [];
    }
  };
  (globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
  window.matchMedia ??= ((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener() {},
    removeEventListener() {},
    addListener() {},
    removeListener() {},
    dispatchEvent: () => false,
  })) as unknown as typeof window.matchMedia;
});

const line = (m: unknown) => JSON.stringify(m);
const V = "v0.9";
const ANSWER = [
  "Montréal leads this month.",
  "```a2ui",
  line({ version: V, createSurface: { surfaceId: "answer", catalogId: "krizaka:genui/v1" } }),
  line({
    version: V,
    updateComponents: {
      surfaceId: "answer",
      components: [
        { id: "root", component: "Column", children: ["kpi", "note", "rows"] },
        { id: "kpi", component: "KpiStat", label: "Passes sold", value: 720, previous: 610, format: "integer" },
        { id: "note", component: "Callout", tone: "info", title: "Montréal leads", text: "412 passes." },
        {
          id: "rows",
          component: "Table",
          caption: "By city",
          columns: [
            { key: "city", label: "City" },
            { key: "passes", label: "Passes", format: "integer" },
          ],
          rows: { path: "/rows" },
        },
      ],
    },
  }),
  line({
    version: V,
    updateDataModel: {
      surfaceId: "answer",
      path: "/rows",
      value: [
        { city: "Montréal", passes: 412 },
        { city: "Laval", passes: 121 },
      ],
    },
  }),
  "```",
].join("\n");

const prose = (p: string) => <p>{p}</p>;

describe("GenUiAnswer — a model's answer drawn as a dashboard in the chat", () => {
  it("detects an a2ui answer", () => {
    expect(hasGenUi(ANSWER)).toBe(true);
    expect(hasGenUi("plain text")).toBe(false);
  });

  it("draws the prose, a figure, a callout and a table from the answer", () => {
    render(<GenUiAnswer content={ANSWER} streaming={false} renderProse={prose} />);
    expect(screen.getByText("Montréal leads this month.")).toBeInTheDocument();
    expect(screen.getByText("Passes sold")).toBeInTheDocument();
    expect(screen.getByText("412 passes.")).toBeInTheDocument();
    expect(screen.getByRole("table")).toBeInTheDocument();
    expect(screen.getByText("Laval")).toBeInTheDocument();
  });

  it("while streaming, draws what has arrived and keeps the half-written line for later", () => {
    const partial = ANSWER.slice(0, ANSWER.indexOf('{"version":"v0.9","updateDataModel"') + 30);
    render(<GenUiAnswer content={partial} streaming renderProse={prose} />);
    expect(screen.getByText("Passes sold")).toBeInTheDocument();
    expect(screen.getByLabelText("The answer as a dashboard")).toHaveAttribute("aria-busy", "true");
  });

  it("never draws what is outside the catalog: a script or HTML falls back to the text", () => {
    const evil = [
      "Hi",
      "```a2ui",
      line({ version: V, createSurface: { surfaceId: "s", catalogId: "krizaka:genui/v1" } }),
      line({
        version: V,
        updateComponents: {
          surfaceId: "s",
          components: [{ id: "root", component: "Html", html: "<script>alert(1)</script>" }],
        },
      }),
      "```",
    ].join("\n");
    const { container } = render(<GenUiAnswer content={evil} streaming={false} renderProse={prose} />);
    expect(container.querySelector("script")).toBeNull();
    expect(container.querySelector("[data-genui-surface]")).toBeNull();
  });
});
