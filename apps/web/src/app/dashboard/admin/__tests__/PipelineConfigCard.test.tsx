import "@testing-library/jest-dom";
import { render as plainRender, screen, fireEvent, act } from "@testing-library/react";
import { queryWrapper } from "@/core/testing/queryWrapper";
import { PipelineConfigCard } from "@/app/dashboard/admin/PipelineConfigCard";

/** These screens read server state: every render gets a fresh query client. */
const render = (ui: Parameters<typeof plainRender>[0], options?: Parameters<typeof plainRender>[1]) =>
  plainRender(ui, { wrapper: queryWrapper(), ...options });

const mockTranslations = {
  admin: {
    pipeline: {
      title: "Interceptor Pipeline",
      subtitle: "Configure execution order",
      resetToDefault: "Reset",
      saveOrder: "Save Order",
      saving: "Saving...",
      dragHint: "Drag to reorder",
      errorLoading: "Failed to load",
      errorSaving: "Failed to save",
      savedSuccess: "Saved",
      resetConfirm: "Reset to defaults?",
      disabled: "Disabled",
      enabledLabel: "Toggle",
    },
  },
};

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({
    t: mockTranslations,
    locale: "en",
  }),
}));

const mockInterceptors = [
  {
    interceptorKey: "rag",
    displayLabel: "RAG Interceptor",
    executionOrder: 1,
    enabled: true,
    description: "Vector search",
  },
  {
    interceptorKey: "memory",
    displayLabel: "Memory Interceptor",
    executionOrder: 2,
    enabled: true,
    description: "Conversation history",
  },
  { interceptorKey: "router", displayLabel: "Router Interceptor", executionOrder: 3, enabled: false, description: "" },
];

function makeFetch(data: unknown = mockInterceptors, ok = true) {
  return jest.fn(() => Promise.resolve({ ok, json: () => Promise.resolve(data) } as unknown as Response));
}

describe("PipelineConfigCard", () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  it("renders interceptors after load", async () => {
    render(<PipelineConfigCard fetchWithAuth={makeFetch()} />);
    expect(await screen.findByText("RAG Interceptor")).toBeInTheDocument();
    expect(screen.getByText("Memory Interceptor")).toBeInTheDocument();
    expect(screen.getByText("Router Interceptor")).toBeInTheDocument();
  });

  it("renders title and subtitle", async () => {
    render(<PipelineConfigCard fetchWithAuth={makeFetch()} />);
    expect(await screen.findByText("Interceptor Pipeline")).toBeInTheDocument();
    expect(screen.getByText("Configure execution order")).toBeInTheDocument();
  });

  it("renders drag hint", async () => {
    render(<PipelineConfigCard fetchWithAuth={makeFetch()} />);
    expect(await screen.findByText("Drag to reorder")).toBeInTheDocument();
  });

  it("shows disabled badge", async () => {
    render(<PipelineConfigCard fetchWithAuth={makeFetch()} />);
    expect(await screen.findByText("Disabled")).toBeInTheDocument();
  });

  it("renders execution orders", async () => {
    render(<PipelineConfigCard fetchWithAuth={makeFetch()} />);
    await screen.findByText("RAG Interceptor");
    expect(screen.getByText("1")).toBeInTheDocument();
    expect(screen.getByText("2")).toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();
  });

  it("shows descriptions", async () => {
    render(<PipelineConfigCard fetchWithAuth={makeFetch()} />);
    expect(await screen.findByText("Vector search")).toBeInTheDocument();
    expect(screen.getByText("Conversation history")).toBeInTheDocument();
  });

  it("save button disabled when clean", async () => {
    render(<PipelineConfigCard fetchWithAuth={makeFetch()} />);
    await screen.findByText("Save Order");
    expect(screen.getByText("Save Order")).toBeDisabled();
  });

  it("handles drag start, over, and end to reorder interceptors", async () => {
    render(<PipelineConfigCard fetchWithAuth={makeFetch()} />);
    await screen.findByText("RAG Interceptor");

    const firstItem = screen.getByLabelText("Interceptor RAG Interceptor");
    const secondItem = screen.getByLabelText("Interceptor Memory Interceptor");

    fireEvent.dragStart(firstItem);
    fireEvent.dragOver(secondItem);
    fireEvent.dragEnd(firstItem);

    // Save button should become enabled because it's dirty now
    expect(screen.getByText("Save Order")).toBeEnabled();
  });

  it("toggles interceptor enabled state", async () => {
    render(<PipelineConfigCard fetchWithAuth={makeFetch()} />);
    await screen.findByText("RAG Interceptor");

    const toggleBtn = screen.getByLabelText("Toggle RAG Interceptor");
    fireEvent.click(toggleBtn);

    // Save button should become enabled
    expect(screen.getByText("Save Order")).toBeEnabled();
  });

  it("saves changes when Save Order is clicked", async () => {
    const fetchMock = makeFetch(mockInterceptors, true);
    render(<PipelineConfigCard fetchWithAuth={fetchMock} />);
    await screen.findByText("RAG Interceptor");

    const toggleBtn = screen.getByLabelText("Toggle RAG Interceptor");
    fireEvent.click(toggleBtn);

    const saveBtn = screen.getByText("Save Order");
    expect(saveBtn).toBeEnabled();

    // Mock PUT response
    fetchMock.mockImplementationOnce(() =>
      Promise.resolve({
        ok: true,
        json: () => Promise.resolve(mockInterceptors),
      } as unknown as Response),
    );

    await act(async () => {
      fireEvent.click(saveBtn);
    });

    expect(saveBtn).toBeDisabled();
    expect(screen.getByText("Saved")).toBeInTheDocument();
  });

  it("handles saving errors gracefully", async () => {
    const fetchMock = makeFetch(mockInterceptors, true);
    render(<PipelineConfigCard fetchWithAuth={fetchMock} />);
    await screen.findByText("RAG Interceptor");

    const toggleBtn = screen.getByLabelText("Toggle RAG Interceptor");
    fireEvent.click(toggleBtn);

    const saveBtn = screen.getByText("Save Order");

    // Mock PUT error
    fetchMock.mockImplementationOnce(() =>
      Promise.resolve({
        ok: false,
      } as unknown as Response),
    );

    await act(async () => {
      fireEvent.click(saveBtn);
    });

    expect(screen.getByText("Failed to save")).toBeInTheDocument();
  });

  it("resets to defaults when Reset is clicked and confirmed", async () => {
    const confirmSpy = jest.spyOn(window, "confirm").mockReturnValue(true);
    const fetchMock = makeFetch(mockInterceptors, true);

    render(<PipelineConfigCard fetchWithAuth={fetchMock} />);
    await screen.findByText("RAG Interceptor");

    const resetBtn = screen.getByText("Reset");

    fetchMock.mockImplementationOnce(() =>
      Promise.resolve({
        ok: true,
        json: () => Promise.resolve(mockInterceptors),
      } as unknown as Response),
    );

    await act(async () => {
      fireEvent.click(resetBtn);
    });

    expect(confirmSpy).toHaveBeenCalledWith("Reset to defaults?");
    expect(screen.getByText("Saved")).toBeInTheDocument();

    confirmSpy.mockRestore();
  });

  it("does not reset when Reset is clicked and canceled", async () => {
    const confirmSpy = jest.spyOn(window, "confirm").mockReturnValue(false);
    const fetchMock = makeFetch(mockInterceptors, true);

    render(<PipelineConfigCard fetchWithAuth={fetchMock} />);
    await screen.findByText("RAG Interceptor");

    const resetBtn = screen.getByText("Reset");
    fireEvent.click(resetBtn);

    expect(confirmSpy).toHaveBeenCalled();
    expect(fetchMock).not.toHaveBeenCalledWith("/api/v1/pipeline/interceptors/reset", expect.anything());

    confirmSpy.mockRestore();
  });
});
