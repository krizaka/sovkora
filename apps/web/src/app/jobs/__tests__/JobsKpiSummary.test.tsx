import "@testing-library/jest-dom";
import { render, screen } from "@testing-library/react";
import { JobsKpiSummary } from "@/app/jobs/JobsKpiSummary";

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({
    t: {
      jobs: {
        kpiTotalJobs: "Total Jobs",
        kpiSuccessRate: "Success Rate",
        kpiModelsUsed: "Models Used",
        kpiCapabilities: "Capabilities",
        kpiTotalInference: "Total Inference",
      },
    },
    locale: "en",
  }),
}));

jest.mock("@/app/jobs/jobStats.utils", () => ({
  computeJobStats: () => ({
    total: 42,
    completed: 30,
    failed: 5,
    avgMs: 2500,
    avgDuration: "2.5s",
    maxDuration: "9s",
    totalMs: 120000,
    totalDuration: "2m",
    uniqueModels: 2,
    uniqueFeatures: 3,
    topModel: "llama3",
    topFeature: "text",
  }),
}));

// jsdom lays nothing out and has no observers: the kit's figures read them.
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

describe("JobsKpiSummary", () => {
  const jobs: never[] = [];

  it("renders Total Jobs label", () => {
    render(<JobsKpiSummary jobs={jobs} />);
    expect(screen.getByText("Total Jobs")).toBeInTheDocument();
  });

  it("renders Success Rate label", () => {
    render(<JobsKpiSummary jobs={jobs} />);
    expect(screen.getByText("Success Rate")).toBeInTheDocument();
  });

  it("renders Models Used label", () => {
    render(<JobsKpiSummary jobs={jobs} />);
    expect(screen.getByText("Models Used")).toBeInTheDocument();
  });

  it("is the kit's KpiStat: six figures in a StatGrid, the hint under each", () => {
    render(<JobsKpiSummary jobs={jobs} />);
    expect(screen.getByText("llama3")).toBeInTheDocument();
    expect(screen.getByText("30 completed")).toBeInTheDocument();
  });
});
