import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@krizaka/query";
import type { ReactNode } from "react";
import { useComposerRunReconciler } from "@/features/chat-session/hooks/useComposerRunReconciler";
import { StudioApi } from "@/services/studio.api";
import type { ChatMessage } from "@/core/types/chat.types";

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: { chat: { awaitingInput: "Waiting" } } }),
}));
jest.mock("@/services/studio.api", () => ({ StudioApi: { fetchRun: jest.fn() } }));

const KEY = ["chatMessages", "eric", "thread-1"];

function wrap(client: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  };
}

describe("useComposerRunReconciler", () => {
  it("resumes a placeholder left by the component that started the run, and swaps in the image", async () => {
    (StudioApi.fetchRun as jest.Mock).mockResolvedValue({
      id: "run-1",
      status: "SUCCEEDED",
      outputs: [{ key: "image", label: "Image", type: "IMAGE", value: "/api/v1/assets/job/image.png" }],
    });
    const client = new QueryClient();
    const pending: ChatMessage = {
      id: "m-2",
      role: "assistant",
      content: "",
      timestamp: 1,
      kind: "run-pending",
      runId: "run-1",
    };
    client.setQueryData<ChatMessage[]>(KEY, [pending]);

    renderHook(() => useComposerRunReconciler("eric", "thread-1"), { wrapper: wrap(client) });

    await waitFor(() =>
      expect(client.getQueryData<ChatMessage[]>(KEY)?.[0]).toMatchObject({
        kind: "image",
        content: "/api/v1/assets/job/image.png",
      }),
    );
    expect(StudioApi.fetchRun).toHaveBeenCalledWith("run-1");
  });

  it("leaves a conversation with no pending run alone", async () => {
    (StudioApi.fetchRun as jest.Mock).mockClear();
    const client = new QueryClient();
    client.setQueryData<ChatMessage[]>(KEY, [{ id: "m-1", role: "user", content: "hi", timestamp: 1, kind: "text" }]);
    renderHook(() => useComposerRunReconciler("eric", "thread-1"), { wrapper: wrap(client) });
    await new Promise((r) => setTimeout(r, 20));
    expect(StudioApi.fetchRun).not.toHaveBeenCalled();
  });
});
