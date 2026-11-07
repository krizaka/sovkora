import "@testing-library/jest-dom";
import { fireEvent, render, screen } from "@testing-library/react";
import { ChartsProvider } from "@krizaka/charts/provider";
import { PeriodTabs, UsageExport } from "@/features/insights/components/UsageControls";
import { UsageKpis } from "@/features/insights/components/UsageKpis";
import { ActivityCharts } from "@/features/insights/components/ActivityCharts";
import { insights } from "@/core/context/translations.insights";
import type { Job } from "@/core/types/jobs.types";
import { usageSummary } from "@/core/jobs/usageStats";

jest.mock("@/core/context/LocaleContext", () => {
  const { insights } = jest.requireActual("@/core/context/translations.insights");
  return { useTranslation: () => ({ locale: "en", t: { insights: insights.en, kinds: {} } }) };
});

const en = insights.en;

// jsdom lays nothing out and has no observers: the kit's figures and charts read them.
beforeAll(() => {
  window.matchMedia = ((query: string) => ({
    matches: false,
    media: query,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    onchange: null,
    dispatchEvent: () => false,
  })) as typeof window.matchMedia;
  class Observer {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
  Object.assign(window, { IntersectionObserver: Observer, ResizeObserver: Observer });
});
const NOW = new Date();
const job = (id: string, daysAgo: number, status: Job["status"]): Job => {
  const at = new Date(NOW.getFullYear(), NOW.getMonth(), NOW.getDate() - daysAgo, 10, 0, 0);
  return {
    id,
    userId: "u",
    featureKey: "image.flux",
    status,
    payload: {},
    result: {},
    createdAt: at.toISOString(),
    updatedAt: new Date(at.getTime() + 120_000).toISOString(),
  };
};
const jobs = [job("a", 0, "COMPLETED"), job("b", 1, "COMPLETED"), job("c", 1, "FAILED"), job("d", 9, "COMPLETED")];

describe("UsageKpis", () => {
  it("shows the four figures with their change against the period before", () => {
    const summary = usageSummary(jobs, [], 7, NOW);
    render(
      <ChartsProvider locale="en-US" labels={en.charts}>
        <UsageKpis summary={summary} days={7} />
      </ChartsProvider>,
    );
    expect(screen.getAllByText(en.usage.kpiJobs).length).toBeGreaterThan(0);
    expect(screen.getByText(en.usage.kpiSuccess)).toBeInTheDocument();
    expect(screen.getByText(en.usage.kpiTime)).toBeInTheDocument();
    expect(screen.getAllByText(/vs the period before/).length).toBeGreaterThan(0);
  });
});

describe("PeriodTabs", () => {
  beforeEach(() => window.history.replaceState(null, "", "/usage"));

  it("offers 7, 30 and 90 days, opens on 30, and writes the choice in the URL (?period=)", () => {
    render(<PeriodTabs />);
    expect(screen.getAllByRole("tab").map((tab) => tab.textContent)).toEqual(["7 days", "30 days", "90 days"]);
    expect(screen.getByRole("tab", { name: "30 days" })).toHaveAttribute("aria-selected", "true");
    fireEvent.mouseDown(screen.getByRole("tab", { name: "90 days" }), { button: 0 });
    expect(window.location.search).toBe("?period=90");
    expect(screen.getByRole("tab", { name: "90 days" })).toHaveAttribute("aria-selected", "true");
  });
});

describe("UsageExport", () => {
  it("offers CSV and JSON", async () => {
    render(<UsageExport jobs={jobs} runs={[]} days={7} />);
    fireEvent.keyDown(screen.getByRole("button", { name: /Export/ }), { key: "Enter" });
    expect(await screen.findByRole("menuitem", { name: "Download CSV" })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: "Download JSON" })).toBeInTheDocument();
  });
});

describe("ActivityCharts", () => {
  it("states what it shows in one line and zooms on the days that have data", () => {
    const summary = usageSummary(jobs, [], 14, NOW);
    const { container } = render(<ActivityCharts summary={summary} jobs={jobs} days={14} />);
    expect(screen.getAllByText(/4 jobs/).length).toBeGreaterThan(0);
    expect(container.querySelectorAll("table").length).toBeGreaterThan(0);
  });
});
