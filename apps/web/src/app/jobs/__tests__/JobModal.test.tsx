import "@testing-library/jest-dom";
import { render, screen, fireEvent } from "@testing-library/react";
import { JobModal } from "@/app/jobs/JobModal";

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: jest.requireActual("@/core/context/translations").translations.en, locale: "en" }),
}));

jest.mock("@/app/jobs/ResultDisplay", () => ({
  ResultDisplay: () => <div data-testid="result-display" />,
}));

jest.mock("@/app/jobs/JobModalParts", () => ({
  ProgressTimeline: () => <div data-testid="progress-timeline" />,
  StageLabels: () => <div data-testid="stage-labels" />,
  StatusPill: ({ status }: { status: string }) => <span>{status}</span>,
  LiveElapsed: () => <span>0s</span>,
}));

const baseJob = {
  id: "job-42",
  featureKey: "chat.text",
  status: "COMPLETED",
  createdAt: "2026-01-01T00:00:00.000Z",
  updatedAt: "2026-01-01T00:00:03.000Z",
  payload: { model: "llama3", prompt: "Hello" },
  result: { text: "Hi there", durationMs: 3000 },
  errorMessage: null,
};

describe("JobModal", () => {
  const onClose = jest.fn();
  const onCopy = jest.fn();

  afterEach(() => jest.clearAllMocks());

  it("returns null when job is null", () => {
    const { container } = render(
      <JobModal job={null} modalType="payload" onClose={onClose} copiedId={null} onCopy={onCopy} />,
    );
    expect(container.innerHTML).toBe("");
  });

  it("renders job ID", () => {
    render(<JobModal job={baseJob as never} modalType="payload" onClose={onClose} copiedId={null} onCopy={onCopy} />);
    expect(screen.getByText(/job-42/)).toBeInTheDocument();
  });

  it("renders status pill", () => {
    render(<JobModal job={baseJob as never} modalType="payload" onClose={onClose} copiedId={null} onCopy={onCopy} />);
    expect(screen.getByText("COMPLETED")).toBeInTheDocument();
  });

  it("closes on Escape key", () => {
    render(<JobModal job={baseJob as never} modalType="payload" onClose={onClose} copiedId={null} onCopy={onCopy} />);
    fireEvent.keyDown(screen.getByRole("dialog"), { key: "Escape" });
    expect(onClose).toHaveBeenCalled();
  });
});
