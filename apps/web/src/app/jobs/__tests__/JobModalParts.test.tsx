import "@testing-library/jest-dom";
import { render, screen } from "@testing-library/react";
import { ProgressTimeline, StageLabels, StatusPill, LiveElapsed } from "@/app/jobs/JobModalParts";

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({
    t: {
      jobs: {
        colCreated: "Submitted",
        running: "Processing",
        statusCompleted: "Completed",
        statusFailed: "Failed",
      },
    },
    locale: "en",
  }),
}));

jest.mock("@/core/constants/http.constants", () => ({
  JOB_STATUS: {
    COMPLETED: "COMPLETED",
    FAILED: "FAILED",
    PROCESSING: "PROCESSING",
    PENDING: "PENDING",
  },
}));

jest.mock("@/core/context/TenantContext", () => ({
  useTenant: () => ({
    accentClasses: { text: "text-fg-muted", accentGradient: "from-surface-3 to-surface-3" },
  }),
}));

describe("StatusPill", () => {
  it("renders Completed status", () => {
    render(<StatusPill status="COMPLETED" />);
    expect(screen.getByText("Completed")).toBeInTheDocument();
  });

  it("renders Failed status", () => {
    render(<StatusPill status="FAILED" />);
    expect(screen.getByText("Failed")).toBeInTheDocument();
  });

  it("renders Processing status", () => {
    render(<StatusPill status="PROCESSING" />);
    expect(screen.getByText("Processing")).toBeInTheDocument();
  });

  it("renders Pending status", () => {
    render(<StatusPill status="PENDING" />);
    expect(screen.getByText("Pending")).toBeInTheDocument();
  });

  it("defaults to Pending for unknown status", () => {
    render(<StatusPill status="UNKNOWN" />);
    expect(screen.getByText("Pending")).toBeInTheDocument();
  });
});

describe("ProgressTimeline", () => {
  it("renders without crashing", () => {
    const { container } = render(<ProgressTimeline status="COMPLETED" />);
    expect(container.querySelector("section")).toBeInTheDocument();
  });
});

describe("StageLabels", () => {
  it("renders three labels for completed", () => {
    render(<StageLabels status="COMPLETED" />);
    expect(screen.getByText("Submitted")).toBeInTheDocument();
    expect(screen.getByText("Processing")).toBeInTheDocument();
    expect(screen.getByText("Completed")).toBeInTheDocument();
  });

  it("shows Failed label for failed status", () => {
    render(<StageLabels status="FAILED" />);
    expect(screen.getByText("Failed")).toBeInTheDocument();
  });
});

describe("LiveElapsed", () => {
  it("renders formatted elapsed time", () => {
    render(<LiveElapsed createdAt="2026-01-01T00:00:00.000Z" updatedAt="2026-01-01T00:00:03.500Z" isLive={false} />);
    expect(screen.getByText("3.5s")).toBeInTheDocument();
  });

  it("renders pulse indicator when live", () => {
    const { container } = render(<LiveElapsed createdAt="2026-01-01T00:00:00.000Z" isLive={true} />);
    expect(container.querySelector(".animate-pulse")).toBeInTheDocument();
  });
});
