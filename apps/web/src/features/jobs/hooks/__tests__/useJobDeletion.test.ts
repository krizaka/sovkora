import { act, renderHook } from "@testing-library/react";
import { useJobDeletion } from "@/features/jobs/hooks/useJobDeletion";
import type { Job } from "@/core/types/jobs.types";

jest.mock("@/core/context/LocaleContext", () => {
  const { translations } = jest.requireActual("@/core/context/translations");
  return { useTranslation: () => ({ t: translations.en, locale: "en" }) };
});
const deleteJob = jest.fn();
jest.mock("@/services/jobs.api", () => ({ JobsApi: { deleteJob: (id: string) => deleteJob(id) } }));
const undoToast = jest.fn();
const warning = jest.fn();
const error = jest.fn();
jest.mock("@krizaka/ui/toast", () => ({
  undoToast: (...args: unknown[]) => undoToast(...args),
  toast: { warning: (...a: unknown[]) => warning(...a), error: (...a: unknown[]) => error(...a) },
}));

const job = (status: Job["status"]): Job => ({
  id: "j1",
  userId: "u",
  featureKey: "orazaka.core.media.video",
  status,
  payload: {},
  result: {},
  createdAt: "2026-10-16T10:00:00Z",
  updatedAt: "2026-10-16T10:01:00Z",
});

beforeEach(() => {
  jest.clearAllMocks();
  deleteJob.mockResolvedValue(undefined);
});

describe("useJobDeletion", () => {
  it("asks first, and hides the row only after the person confirmed", () => {
    const { result } = renderHook(() => useJobDeletion(jest.fn()));
    act(() => result.current.request(job("COMPLETED")));
    expect(result.current.asking?.id).toBe("j1");
    expect(result.current.hidden.has("j1")).toBe(false);
    act(() => result.current.confirm());
    expect(result.current.asking).toBeNull();
    expect(result.current.hidden.has("j1")).toBe(true);
    expect(deleteJob).not.toHaveBeenCalled(); // the undo window: nothing is deleted yet
  });

  it("an undo puts the row back and never reaches the server", () => {
    const { result } = renderHook(() => useJobDeletion(jest.fn()));
    act(() => result.current.request(job("COMPLETED")));
    act(() => result.current.confirm());
    act(() => undoToast.mock.calls[0][1].onUndo());
    expect(result.current.hidden.has("j1")).toBe(false);
    expect(deleteJob).not.toHaveBeenCalled();
  });

  it("when the window ends the server deletes and the lists refresh", async () => {
    const done = jest.fn();
    const { result } = renderHook(() => useJobDeletion(done));
    act(() => result.current.request(job("COMPLETED")));
    act(() => result.current.confirm());
    await act(async () => undoToast.mock.calls[0][1].onCommit());
    expect(deleteJob).toHaveBeenCalledWith("j1");
    expect(done).toHaveBeenCalled();
  });

  it("a refusal from the server puts the row back and says why", async () => {
    deleteJob.mockRejectedValue(new Error("store unavailable"));
    const { result } = renderHook(() => useJobDeletion(jest.fn()));
    act(() => result.current.request(job("COMPLETED")));
    act(() => result.current.confirm());
    await act(async () => undoToast.mock.calls[0][1].onCommit());
    expect(result.current.hidden.has("j1")).toBe(false);
    expect(error).toHaveBeenCalledWith(expect.stringContaining("store unavailable"));
  });

  it("a task still running is not offered for deletion", () => {
    const { result } = renderHook(() => useJobDeletion(jest.fn()));
    act(() => result.current.request(job("PROCESSING")));
    expect(result.current.asking).toBeNull();
    expect(warning).toHaveBeenCalledWith("Stop the task before deleting it.");
  });
});
