import "@testing-library/jest-dom";
import { render, screen, within } from "@testing-library/react";
import { RunOutputs } from "@/features/studio/components/RunOutputs";
import drawn from "./fixtures/data-driven-ui-answer.json";
import refused from "./fixtures/run-28954cdf-answer.json";

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: jest.requireActual("@/core/context/translations").translations.en, locale: "en" }),
}));

// jsdom has no IntersectionObserver, ResizeObserver nor matchMedia (the KpiStat's ticker and the charts use them).
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

const answer = (value: string) =>
  render(<RunOutputs outputs={[{ key: "answer", label: "The answer and its dashboard", type: "TEXT", value }]} />);

describe("RunOutputs — a data-driven-ui answer is drawn as its dashboard (krizaka-internal#3628)", () => {
  it("draws the summary, the figures, the chart and the table the worker wrote", () => {
    answer(drawn.answer);
    expect(screen.getByText(/The stoneware lamp earned the most \(\$3,770\)/)).toBeInTheDocument();
    const dashboard = screen.getByLabelText("The answer as a dashboard");
    expect(within(dashboard).getByText("Lamp")).toBeInTheDocument();
    expect(within(dashboard).getByText("Best week (Oct 19–25)")).toBeInTheDocument();
    expect(within(dashboard).getByText("Revenue per week")).toBeInTheDocument();
    const table = within(dashboard).getByRole("table", { name: "Revenue per product" });
    expect(within(table).getByRole("columnheader", { name: /produit/ })).toBeInTheDocument();
    expect(within(table).getByRole("cell", { name: "lampe-gres-creme" })).toBeInTheDocument();
  });

  it("never prints the dashboard's JSON", () => {
    answer(drawn.answer);
    expect(screen.queryByText(/updateComponents/)).not.toBeInTheDocument();
    expect(screen.queryByText(/```a2ui/)).not.toBeInTheDocument();
  });

  it("refuses the answer mistral wrote on dev as a whole: a notice, neither its figures nor its JSON", () => {
    answer(refused.answer);
    expect(screen.getByRole("alert")).toHaveTextContent("This dashboard cannot be shown");
    expect(screen.queryByText(/2,310/)).not.toBeInTheDocument();
    expect(screen.queryByText(/updateComponents/)).not.toBeInTheDocument();
    expect(screen.queryByLabelText("The answer as a dashboard")).not.toBeInTheDocument();
  });

  it("refuses an answer whose fence never closes", () => {
    answer(drawn.answer.slice(0, drawn.answer.lastIndexOf("```")));
    expect(screen.getByRole("alert")).toHaveTextContent("This dashboard cannot be shown");
  });

  it("refuses a surface that names a child it never sends", () => {
    answer(
      drawn.answer.replace('"children":["figures","chart","table"]', '"children":["figures","chart","table","ghost"]'),
    );
    expect(screen.getByRole("alert")).toHaveTextContent("This dashboard cannot be shown");
  });

  it("still prints a plain text result as text", () => {
    answer("A plain caption.");
    expect(screen.getByText("A plain caption.")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});
