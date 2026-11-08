import "@testing-library/jest-dom";
import { render, screen, waitFor } from "@testing-library/react";
import { RunsInProgress } from "@/features/studio/components/RunsInProgress";

jest.mock("@/core/context/LocaleContext", () => {
  const { translations } = jest.requireActual("@/core/context/translations");
  return { useTranslation: () => ({ t: translations.en, locale: "en" }) };
});
jest.mock("next/link", () => ({
  __esModule: true,
  default: ({ children, href }: { children: React.ReactNode; href: string }) => <a href={href}>{children}</a>,
}));
const fetchRuns = jest.fn();
jest.mock("@/services/studio.api", () => ({ StudioApi: { fetchRuns: () => fetchRuns() } }));

const run = (id: string, status: string) => ({
  id,
  studioKey: "video-generation",
  status,
  startedAt: "2026-10-16T10:00:00Z",
  steps: [],
  outputs: [],
});

describe("RunsInProgress — the way back to a run you left", () => {
  it("lists the runs still going, each linking to its page, and only those", async () => {
    fetchRuns.mockResolvedValue([
      run("r1", "RUNNING"),
      run("r2", "SUCCEEDED"),
      run("r3", "AWAITING_INPUT"),
      run("r4", "CANCELLED"),
    ]);
    render(<RunsInProgress />);
    await waitFor(() => expect(screen.getAllByRole("link")).toHaveLength(2));
    const links = screen.getAllByRole("link").map((a) => a.getAttribute("href"));
    expect(links).toEqual(["/studios/runs/r1", "/studios/runs/r3"]);
  });

  it("renders nothing when nothing runs", async () => {
    fetchRuns.mockResolvedValue([run("r2", "SUCCEEDED")]);
    const { container } = render(<RunsInProgress />);
    await waitFor(() => expect(fetchRuns).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });

  it("a failing read stays silent: the run pages and the toasts still work", async () => {
    fetchRuns.mockRejectedValue(new Error("boom"));
    const { container } = render(<RunsInProgress />);
    await waitFor(() => expect(fetchRuns).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });
});
