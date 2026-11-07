import { renderHook, act } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { useChatThreadActions } from "@/features/chat-session/hooks/useChatThreadActions";

const push = jest.fn();
jest.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));
jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: { chat: { sessionTitle: "Chat" } } }),
}));

function setup() {
  const params = {
    activeConversationId: "",
    setActiveConversationId: jest.fn(),
    setChatInput: jest.fn(),
    startChatStream: jest.fn(),
    userId: "eric",
    threads: [],
    createThread: jest.fn().mockResolvedValue({ conversationId: "new-1" }),
    renameThread: jest.fn(),
    deleteThread: jest.fn(),
  };
  const client = new QueryClient();
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  const { result } = renderHook(() => useChatThreadActions(params), { wrapper });
  return { params, result, client };
}

describe("useChatThreadActions — the first message of a conversation", () => {
  beforeEach(() => push.mockClear());

  it("opens the chat stream when nothing is staged", async () => {
    const { params, result, client } = setup();
    await act(() => result.current.handleFirstSend("Summarise the Q3 report"));
    expect(params.startChatStream).toHaveBeenCalledWith("new-1", "Summarise the Q3 report");
    expect(client.getQueryData(["chatMessages", "eric", "new-1"])).toHaveLength(1);
    expect(push).toHaveBeenCalledWith("/chat?conversationId=new-1");
  });

  it("runs the staged Studio in the new thread instead of chatting", async () => {
    const { params, result, client } = setup();
    const runStagedIn = jest.fn().mockReturnValue(true);
    await act(() => result.current.handleFirstSend("A ceramic lamp, product photo", runStagedIn));
    expect(runStagedIn).toHaveBeenCalledWith("new-1");
    expect(params.startChatStream).not.toHaveBeenCalled();
    // The run echoes the request itself; the text path's echo is not added twice.
    expect(client.getQueryData(["chatMessages", "eric", "new-1"])).toBeUndefined();
    expect(params.setActiveConversationId).toHaveBeenCalledWith("new-1");
    expect(push).toHaveBeenCalledWith("/chat?conversationId=new-1");
  });

  it("falls back to the chat stream when the composer had nothing staged after all", async () => {
    const { params, result } = setup();
    await act(() => result.current.handleFirstSend("Hello", () => false));
    expect(params.startChatStream).toHaveBeenCalledWith("new-1", "Hello");
  });
});
