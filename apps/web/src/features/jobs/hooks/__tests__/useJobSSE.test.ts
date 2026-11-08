import { renderHook, act } from "@testing-library/react";
import { useJobSSE } from "@/core/hooks/useJobSSE";

jest.mock("@/core/constants/capability.constants", () => ({
  resolveFeatureLabel: (key: string) => key.replace(".", " ").toUpperCase(),
}));

// Mock EventSource
class MockEventSource {
  static instances: MockEventSource[] = [];
  url: string;
  onopen: (() => void) | null = null;
  onerror: ((err: unknown) => void) | null = null;
  listeners: Record<string, ((event: MessageEvent) => void)[]> = {};

  constructor(url: string) {
    this.url = url;
    MockEventSource.instances.push(this);
  }

  addEventListener(type: string, listener: (event: MessageEvent) => void) {
    if (!this.listeners[type]) this.listeners[type] = [];
    this.listeners[type].push(listener);
  }

  close = jest.fn();

  dispatchEvent(type: string, data: string) {
    if (this.listeners[type]) {
      for (const listener of this.listeners[type]) {
        listener({ data } as MessageEvent);
      }
    }
  }
}

(globalThis as Record<string, unknown>).EventSource = MockEventSource;

describe("useJobSSE", () => {
  beforeEach(() => {
    MockEventSource.instances = [];
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it("does not connect when not authenticated", () => {
    const callbacks = {
      onJobUpdate: jest.fn(),
      onJobProgress: jest.fn(),
      onJobEvent: jest.fn(),
      getJobs: jest.fn().mockReturnValue([]),
    };
    const { unmount } = renderHook(() => useJobSSE(false, true, callbacks));
    expect(MockEventSource.instances).toHaveLength(0);
    unmount();
  });

  it("does not connect when no active jobs", () => {
    const callbacks = {
      onJobUpdate: jest.fn(),
      onJobProgress: jest.fn(),
      onJobEvent: jest.fn(),
      getJobs: jest.fn().mockReturnValue([]),
    };
    renderHook(() => useJobSSE(true, false, callbacks));
    expect(MockEventSource.instances).toHaveLength(0);
  });

  it("closes existing connection when active jobs or authentication changes to false", () => {
    const callbacks = {
      onJobUpdate: jest.fn(),
      onJobProgress: jest.fn(),
      onJobEvent: jest.fn(),
      getJobs: jest.fn().mockReturnValue([]),
    };
    const { rerender } = renderHook(({ auth, active }) => useJobSSE(auth, active, callbacks), {
      initialProps: { auth: true, active: true },
    });
    expect(MockEventSource.instances).toHaveLength(1);
    const es = MockEventSource.instances[0];

    rerender({ auth: false, active: true });
    expect(es.close).toHaveBeenCalled();
  });

  it("connects when authenticated with active jobs", () => {
    const callbacks = {
      onJobUpdate: jest.fn(),
      onJobProgress: jest.fn(),
      onJobEvent: jest.fn(),
      getJobs: jest.fn().mockReturnValue([]),
    };
    renderHook(() => useJobSSE(true, true, callbacks));
    expect(MockEventSource.instances).toHaveLength(1);
    expect(MockEventSource.instances[0].url).toBe("/api/v1/jobs/stream");
  });

  it("calls onJobUpdate on job-status event (PROCESSING)", () => {
    const callbacks = {
      onJobUpdate: jest.fn(),
      onJobProgress: jest.fn(),
      onJobEvent: jest.fn(),
      getJobs: jest.fn().mockReturnValue([]),
    };
    renderHook(() => useJobSSE(true, true, callbacks));
    const es = MockEventSource.instances[0];
    const job = { id: "j1", featureKey: "chat.text", status: "PROCESSING" };
    act(() => {
      es.dispatchEvent("job-status", JSON.stringify(job));
    });
    expect(callbacks.onJobUpdate).toHaveBeenCalledWith(job);
    expect(callbacks.onJobEvent).toHaveBeenCalledWith(job, "started");
  });

  it("calls onJobUpdate on job-status event (COMPLETED)", () => {
    const callbacks = {
      onJobUpdate: jest.fn(),
      onJobProgress: jest.fn(),
      onJobEvent: jest.fn(),
      getJobs: jest.fn().mockReturnValue([]),
    };
    renderHook(() => useJobSSE(true, true, callbacks));
    const es = MockEventSource.instances[0];
    const job = { id: "j1", featureKey: "chat.text", status: "COMPLETED" };
    act(() => {
      es.dispatchEvent("job-status", JSON.stringify(job));
    });
    expect(callbacks.onJobUpdate).toHaveBeenCalledWith(job);
    expect(callbacks.onJobProgress).toHaveBeenCalledWith("j1", 100);
    expect(callbacks.onJobEvent).toHaveBeenCalledWith(job, "completed");
    expect(es.close).toHaveBeenCalled();
  });

  it("calls onJobUpdate on job-status event (FAILED)", () => {
    const callbacks = {
      onJobUpdate: jest.fn(),
      onJobProgress: jest.fn(),
      onJobEvent: jest.fn(),
      getJobs: jest.fn().mockReturnValue([]),
    };
    renderHook(() => useJobSSE(true, true, callbacks));
    const es = MockEventSource.instances[0];
    const job = { id: "j1", featureKey: "chat.text", status: "FAILED", errorMessage: "Bad error" };
    act(() => {
      es.dispatchEvent("job-status", JSON.stringify(job));
    });
    expect(callbacks.onJobUpdate).toHaveBeenCalledWith(job);
    expect(callbacks.onJobProgress).toHaveBeenCalledWith("j1", 0);
    expect(callbacks.onJobEvent).toHaveBeenCalledWith(job, "failed");
    expect(es.close).toHaveBeenCalled();
  });

  it("a cancelled job is a terminal state: told to the person, and the stream can close", () => {
    const callbacks = {
      onJobUpdate: jest.fn(),
      onJobProgress: jest.fn(),
      onJobEvent: jest.fn(),
      getJobs: jest.fn().mockReturnValue([]),
    };
    renderHook(() => useJobSSE(true, true, callbacks));
    const es = MockEventSource.instances[0];
    const job = { id: "j1", featureKey: "chat.text", status: "CANCELLED" };
    act(() => {
      es.dispatchEvent("job-status", JSON.stringify(job));
    });
    expect(callbacks.onJobEvent).toHaveBeenCalledWith(job, "cancelled");
    expect(es.close).toHaveBeenCalled();
  });

  it("a progress event carries the phase and the step n of N to the caller", () => {
    const callbacks = {
      onJobUpdate: jest.fn(),
      onJobProgress: jest.fn(),
      onJobEvent: jest.fn(),
      getJobs: jest.fn().mockReturnValue([]),
    };
    renderHook(() => useJobSSE(true, true, callbacks));
    const es = MockEventSource.instances[0];
    act(() => {
      es.dispatchEvent(
        "job-progress",
        JSON.stringify({ jobId: "j1", progress: 40, phase: "generating", step: 4, steps: 10 }),
      );
    });
    expect(callbacks.onJobProgress).toHaveBeenCalledWith("j1", 40, { phase: "generating", step: 4, steps: 10 });
  });

  it("keeps SSE open if there are other active jobs", () => {
    const callbacks = {
      onJobUpdate: jest.fn(),
      onJobProgress: jest.fn(),
      onJobEvent: jest.fn(),
      getJobs: jest.fn().mockReturnValue([{ id: "j2", status: "PROCESSING" }]),
    };
    renderHook(() => useJobSSE(true, true, callbacks));
    const es = MockEventSource.instances[0];
    const job = { id: "j1", featureKey: "chat.text", status: "COMPLETED" };
    act(() => {
      es.dispatchEvent("job-status", JSON.stringify(job));
    });
    expect(es.close).not.toHaveBeenCalled();
  });

  it("calls onJobProgress on job-progress event", () => {
    const callbacks = {
      onJobUpdate: jest.fn(),
      onJobProgress: jest.fn(),
      onJobEvent: jest.fn(),
      getJobs: jest.fn().mockReturnValue([]),
    };
    renderHook(() => useJobSSE(true, true, callbacks));
    const es = MockEventSource.instances[0];
    act(() => {
      es.dispatchEvent("job-progress", JSON.stringify({ jobId: "j1", progress: 50 }));
    });
    expect(callbacks.onJobProgress).toHaveBeenCalledWith("j1", 50, {
      phase: undefined,
      step: undefined,
      steps: undefined,
    });
  });

  it("handles JSON parsing errors gracefully", () => {
    const callbacks = {
      onJobUpdate: jest.fn(),
      onJobProgress: jest.fn(),
      onJobEvent: jest.fn(),
      getJobs: jest.fn().mockReturnValue([]),
    };
    renderHook(() => useJobSSE(true, true, callbacks));
    const es = MockEventSource.instances[0];
    const consoleErrorSpy = jest.spyOn(console, "error").mockImplementation(() => {});

    act(() => {
      es.dispatchEvent("job-status", "invalid json");
      es.dispatchEvent("job-progress", "invalid json");
    });

    expect(consoleErrorSpy).toHaveBeenCalledTimes(2);
    consoleErrorSpy.mockRestore();
  });

  it("handles event source lifecycle and reconnects on error", () => {
    const callbacks = {
      onJobUpdate: jest.fn(),
      onJobProgress: jest.fn(),
      onJobEvent: jest.fn(),
      getJobs: jest.fn().mockReturnValue([]),
    };
    const { unmount } = renderHook(() => useJobSSE(true, true, callbacks));
    expect(MockEventSource.instances).toHaveLength(1);
    const es = MockEventSource.instances[0];

    // Trigger onopen
    if (es.onopen) {
      act(() => {
        es.onopen!();
      });
    }

    // Trigger onerror
    if (es.onerror) {
      act(() => {
        es.onerror!(new ErrorEvent("error"));
      });
    }

    expect(es.close).toHaveBeenCalled();

    // Fast forward reconnect timer
    act(() => {
      jest.advanceTimersByTime(2000);
    });

    // Should create a second EventSource instance
    expect(MockEventSource.instances).toHaveLength(2);

    unmount();
  });
});
