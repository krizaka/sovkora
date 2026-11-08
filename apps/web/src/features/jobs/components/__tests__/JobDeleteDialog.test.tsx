import "@testing-library/jest-dom";
import { fireEvent, render, screen } from "@testing-library/react";
import { JobDeleteDialog } from "@/features/jobs/components/JobDeleteDialog";
import type { Job } from "@/core/types/jobs.types";

jest.mock("@/core/context/LocaleContext", () => {
  const { translations } = jest.requireActual("@/core/context/translations");
  return { useTranslation: () => ({ t: translations.en, locale: "en" }) };
});

const job: Job = {
  id: "j1",
  userId: "u",
  featureKey: "orazaka.core.media.video",
  status: "COMPLETED",
  payload: {},
  result: {},
  createdAt: "2026-10-16T10:00:00Z",
  updatedAt: "2026-10-16T10:01:00Z",
};

describe("JobDeleteDialog", () => {
  it("asks by name, and confirming calls back", () => {
    const onConfirm = jest.fn();
    render(<JobDeleteDialog job={job} onConfirm={onConfirm} onDismiss={jest.fn()} />);
    expect(screen.getByRole("alertdialog")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Delete" }));
    expect(onConfirm).toHaveBeenCalled();
  });

  it("Keep it closes without deleting", () => {
    const onConfirm = jest.fn();
    const onDismiss = jest.fn();
    render(<JobDeleteDialog job={job} onConfirm={onConfirm} onDismiss={onDismiss} />);
    fireEvent.click(screen.getByRole("button", { name: "Keep it" }));
    expect(onConfirm).not.toHaveBeenCalled();
    expect(onDismiss).toHaveBeenCalled();
  });

  it("renders nothing when no job is being asked about", () => {
    render(<JobDeleteDialog job={null} onConfirm={jest.fn()} onDismiss={jest.fn()} />);
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });
});
