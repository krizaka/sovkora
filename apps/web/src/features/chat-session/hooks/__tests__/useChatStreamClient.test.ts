/* eslint-disable @typescript-eslint/no-require-imports, @typescript-eslint/no-explicit-any */
if (typeof global.TextEncoder === "undefined") {
  const { TextEncoder, TextDecoder } = require("util");
  global.TextEncoder = TextEncoder;
  global.TextDecoder = TextDecoder;
}

import { renderHook, act } from "@testing-library/react";
import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useChatStreamClient } from "@/core/hooks/useChatStreamClient";

// Mock router / auth
jest.mock("@/core/hooks/useAuth", () => ({
  useAuth: () => ({
    user: { id: "test-user", email: "test-user@orazaka.com" },
  }),
}));

function wrapper({ children }: { children: React.ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return React.createElement(QueryClientProvider, { client }, children);
}

// LocalStorage Mock helper
const localStorageMock = (() => {
  let store: Record<string, string> = {};
  return {
    getItem: (key: string) => store[key] || null,
    setItem: (key: string, value: string) => {
      store[key] = value.toString();
    },
    clear: () => {
      store = {};
    },
    removeItem: (key: string) => {
      delete store[key];
    },
  };
})();

Object.defineProperty(global, "localStorage", {
  value: localStorageMock,
});

describe("useChatStreamClient", () => {
  let mockReader: any;

  beforeEach(() => {
    localStorage.clear();
    jest.clearAllMocks();
    jest.useFakeTimers();

    mockReader = {
      read: jest.fn(),
    };

    const mockReadableStream = {
      getReader: () => mockReader,
    };

    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      body: mockReadableStream,
    });
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it("initializes with isChatStreaming false", () => {
    const { result } = renderHook(() => useChatStreamClient(), { wrapper });
    expect(result.current.isChatStreaming).toBe(false);
  });

  it("can stop stream and sets streaming to false", () => {
    const { result } = renderHook(() => useChatStreamClient(), { wrapper });
    act(() => {
      result.current.stopChatStream();
    });
    expect(result.current.isChatStreaming).toBe(false);
  });

  it("handles successful data stream ingestion", async () => {
    const { result } = renderHook(() => useChatStreamClient(), { wrapper });

    // Mock readable stream chunks
    mockReader.read
      .mockResolvedValueOnce({
        done: false,
        value: new Uint8Array(Buffer.from('data: {"content": "Hello "}\n')),
      })
      .mockResolvedValueOnce({
        done: false,
        value: new Uint8Array(Buffer.from('data: {"content": "world!"}\n')),
      })
      .mockResolvedValueOnce({
        done: true,
      });

    await act(async () => {
      await result.current.startChatStream("conv-123", "hi");
    });

    expect(global.fetch).toHaveBeenCalledWith(
      "/api/chat/stream/conv-123",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ prompt: "hi", assetIds: [] }),
      }),
    );

    // Stream finishes and isChatStreaming goes back to false
    expect(result.current.isChatStreaming).toBe(false);

    // Verify localStorage message storage
    const stored = localStorage.getItem("orazaka_messages_test-user_conv-123");
    expect(stored).toContain("Hello world!");
  });

  it("puts the engine's verdict on the answer: pending on the chunks, final on the last event", async () => {
    const { result } = renderHook(() => useChatStreamClient(), { wrapper });
    const sse = (o: object) => new Uint8Array(Buffer.from(`data: ${JSON.stringify(o)}\n\n`));
    mockReader.read
      .mockResolvedValueOnce({
        done: false,
        value: sse({ content: "Hello", metadata: { "control.verdict": "PENDING" } }),
      })
      .mockResolvedValueOnce({
        done: false,
        value: sse({
          content: "",
          metadata: { "control.verdict": "DEGRADED", "control.badge": "verification_unavailable" },
        }),
      })
      .mockResolvedValueOnce({ done: true });

    await act(async () => {
      await result.current.startChatStream("conv-ctl", "hi");
    });

    const stored = JSON.parse(localStorage.getItem("orazaka_messages_test-user_conv-ctl") ?? "[]");
    expect(stored.at(-1)).toMatchObject({
      content: "Hello",
      control: { state: "degraded", badge: "verification_unavailable" },
    });
  });

  it("handles fetch non-ok response and logs error", async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: false,
      statusText: "Bad Gateway",
    });

    const consoleErrorSpy = jest.spyOn(console, "error").mockImplementation(() => {});

    const { result } = renderHook(() => useChatStreamClient(), { wrapper });

    await act(async () => {
      await result.current.startChatStream("conv-123", "hi");
    });

    expect(consoleErrorSpy).toHaveBeenCalled();
    const stored = localStorage.getItem("orazaka_messages_test-user_conv-123");
    expect(stored).toContain("Connection to the AI model failed");
    consoleErrorSpy.mockRestore();
  });

  it("handles AbortError quietly", async () => {
    const abortError = new Error("Aborted");
    abortError.name = "AbortError";
    global.fetch = jest.fn().mockRejectedValue(abortError);

    const consoleErrorSpy = jest.spyOn(console, "error").mockImplementation(() => {});

    const { result } = renderHook(() => useChatStreamClient(), { wrapper });

    await act(async () => {
      await result.current.startChatStream("conv-123", "hi");
    });

    expect(consoleErrorSpy).not.toHaveBeenCalled();
    consoleErrorSpy.mockRestore();
  });

  it("updates thread title in localStorage on stream error", async () => {
    global.fetch = jest.fn().mockRejectedValue(new Error("Network Failure"));
    localStorage.setItem("orazaka_threads", JSON.stringify([{ conversationId: "conv-123", title: "Original Title" }]));

    const consoleErrorSpy = jest.spyOn(console, "error").mockImplementation(() => {});

    const { result } = renderHook(() => useChatStreamClient(), { wrapper });

    await act(async () => {
      await result.current.startChatStream("conv-123", "Long prompt content that exceeds forty characters");
    });

    const storedThreads = JSON.parse(localStorage.getItem("orazaka_threads")!);
    expect(storedThreads[0].title).toBe("Long prompt content that exceeds forty c...");
    consoleErrorSpy.mockRestore();
  });
});
