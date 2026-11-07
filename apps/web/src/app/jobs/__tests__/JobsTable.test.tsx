import "@testing-library/jest-dom";
import { fireEvent, render, screen, within } from "@testing-library/react";
import { JobsTable, type JobsTableProps } from "@/app/jobs/JobsTable";
import { translations } from "@/core/context/translations";
import type { Job } from "@/core/types/jobs.types";

jest.mock("@/core/context/LocaleContext", () => {
  const { translations: tr } = jest.requireActual("@/core/context/translations");
  return { useTranslation: () => ({ t: tr.en, locale: "en" }) };
});

const video: Job = {
  id: "job-video-1",
  userId: "u",
  featureKey: "orazaka.core.media.video",
  status: "COMPLETED",
  createdAt: "2026-10-16T10:00:00Z",
  updatedAt: "2026-10-16T10:02:47Z",
  payload: { prompt: "A heron", model: "wan2.2-ti2v-5b", width: 1280, height: 704, frames: 121, fps: 24 },
  result: {
    url: "/clip.mp4",
    durationMs: 768,
    metrics: { gpu: "NVIDIA H100 80GB HBM3", provider: "runpod", gpu_allocated_mb: 29800, inference_time_sec: 167.7 },
  },
};
const failed: Job = {
  id: "job-text-2",
  userId: "u",
  featureKey: "orazaka.core.chat",
  status: "FAILED",
  createdAt: "2026-10-16T10:30:00Z",
  updatedAt: "2026-10-16T10:30:04Z",
  payload: {},
  result: {},
  errorMessage: "Provider timeout",
};

const base: JobsTableProps = {
  jobs: [video, failed],
  loading: false,
  totalElements: 2,
  currentPage: 0,
  pageSize: 10,
  onPageChange: jest.fn(),
  onPageSizeChange: jest.fn(),
  onOpenModal: jest.fn(),
  copiedId: null,
  onCopy: jest.fn(),
};

describe("JobsTable", () => {
  it("is one line per job: task, a model that is always a name, where it ran, the real wall time", () => {
    render(<JobsTable {...base} />);
    const row = screen.getByText("Video Generation").closest("tr")!;
    expect(within(row).getByText("Wan 2.2 TI2V 5B")).toBeInTheDocument();
    expect(within(row).getByText("H100 · RunPod")).toBeInTheDocument();
    expect(within(row).getByText("2 min 47 s")).toBeInTheDocument();
    expect(within(row).queryByText("768 ms")).not.toBeInTheDocument();
    const other = screen.getByText("Text Generation").closest("tr")!;
    expect(within(other).getByText("Chosen by the router")).toBeInTheDocument();
  });

  it("puts the facts in a grouped detail, with units and human labels, never raw keys", () => {
    render(<JobsTable {...base} />);
    fireEvent.click(screen.getAllByRole("button", { name: /Video Generation/ })[0]);
    expect(screen.getByText("Hardware")).toBeInTheDocument();
    expect(screen.getByText("29.1 GB")).toBeInTheDocument();
    expect(screen.getByText("167.7 s")).toBeInTheDocument();
    expect(screen.getByText("1280×704")).toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/gpu_allocated_mb|inference_time_sec|peak_memory_rss_mb/);
  });

  it("shows the failure in the detail, and the delete action only when the page provides it", () => {
    const onDelete = jest.fn();
    const { rerender } = render(<JobsTable {...base} />);
    expect(screen.queryByRole("button", { name: "Delete" })).not.toBeInTheDocument();
    rerender(<JobsTable {...base} onDelete={onDelete} />);
    fireEvent.click(screen.getAllByRole("button", { name: "Delete" })[0]);
    expect(onDelete).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getAllByRole("button", { name: /Text Generation/ })[0]);
    expect(screen.getByText("Provider timeout")).toBeInTheDocument();
  });

  it("opens the result of a finished task and the request of an unfinished one", () => {
    render(<JobsTable {...base} />);
    fireEvent.click(screen.getAllByRole("button", { name: "Open the result" })[0]);
    expect(base.onOpenModal).toHaveBeenCalledWith(video, "result");
    fireEvent.click(screen.getByRole("button", { name: "View the request" }));
    expect(base.onOpenModal).toHaveBeenCalledWith(failed, "payload");
  });
});

describe("jobsTable words", () => {
  it("has a label for every fact id, in English and French", () => {
    const en = Object.keys(translations.en.jobsTable.facts).sort();
    expect(Object.keys(translations.fr.jobsTable.facts).sort()).toEqual(en);
    for (const l of ["en", "fr"] as const)
      for (const v of Object.values(translations[l].jobsTable.facts)) expect(v).not.toBe("");
  });
});
