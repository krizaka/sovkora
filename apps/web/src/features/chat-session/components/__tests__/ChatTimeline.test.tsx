import "@testing-library/jest-dom";
import { render, screen } from "@testing-library/react";
import { ChatTimeline } from "@/features/chat-session/components/ChatTimeline";

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({
    t: {
      chat: {
        loadingMessages: "Loading messages...",
        noActiveConversation: "No active conversation",
        ai: "AI",
        connectionError: "Connection error",
        you: "You",
        copyMessage: "Copy",
        copiedMessage: "Copied",
      },
    },
    locale: "en",
  }),
}));

jest.mock("@/features/chat-session/components/ChatMessage", () => ({
  ChatMessage: ({ message }: { message: { id: string; content: string } }) => (
    <div data-testid={`msg-${message.id}`}>{message.content}</div>
  ),
}));

const createRef = () => ({ current: null });

describe("ChatTimeline", () => {
  it("shows loading state when isLoadingMessages is true", () => {
    render(
      <ChatTimeline
        messages={[]}
        isLoadingMessages={true}
        isGenerating={false}
        isImagePending={false}
        isSpeechPending={false}
        error={null}
        messagesEndRef={createRef()}
      />,
    );
    expect(screen.getByText("Loading messages...")).toBeInTheDocument();
  });

  it("shows empty state when messages is empty", () => {
    render(
      <ChatTimeline
        messages={[]}
        isLoadingMessages={false}
        isGenerating={false}
        isImagePending={false}
        isSpeechPending={false}
        error={null}
        messagesEndRef={createRef()}
      />,
    );
    expect(screen.getByText("No active conversation")).toBeInTheDocument();
  });

  it("renders messages when provided", () => {
    const messages = [
      { id: "1", role: "user", content: "Hello", timestamp: "2024-01-01T00:00:00Z" },
      { id: "2", role: "assistant", content: "Hi there", timestamp: "2024-01-01T00:00:01Z" },
    ];
    render(
      <ChatTimeline
        messages={messages as never[]}
        isLoadingMessages={false}
        isGenerating={false}
        isImagePending={false}
        isSpeechPending={false}
        error={null}
        messagesEndRef={createRef()}
      />,
    );
    expect(screen.getByTestId("msg-1")).toBeInTheDocument();
    expect(screen.getByTestId("msg-2")).toBeInTheDocument();
  });

  it("shows typing indicator when isGenerating", () => {
    const messages = [{ id: "1", role: "user", content: "Hello", timestamp: "2024-01-01T00:00:00Z" }];
    render(
      <ChatTimeline
        messages={messages as never[]}
        isLoadingMessages={false}
        isGenerating={true}
        isImagePending={false}
        isSpeechPending={false}
        error={null}
        messagesEndRef={createRef()}
      />,
    );
    expect(screen.getByText("AI")).toBeInTheDocument();
  });

  it("shows typing indicator when isImagePending", () => {
    const messages = [{ id: "1", role: "user", content: "Gen img", timestamp: "2024-01-01T00:00:00Z" }];
    render(
      <ChatTimeline
        messages={messages as never[]}
        isLoadingMessages={false}
        isGenerating={false}
        isImagePending={true}
        isSpeechPending={false}
        error={null}
        messagesEndRef={createRef()}
      />,
    );
    expect(screen.getByText("AI")).toBeInTheDocument();
  });

  it("shows typing indicator when isSpeechPending", () => {
    const messages = [{ id: "1", role: "user", content: "Speak", timestamp: "2024-01-01T00:00:00Z" }];
    render(
      <ChatTimeline
        messages={messages as never[]}
        isLoadingMessages={false}
        isGenerating={false}
        isImagePending={false}
        isSpeechPending={true}
        error={null}
        messagesEndRef={createRef()}
      />,
    );
    expect(screen.getByText("AI")).toBeInTheDocument();
  });

  it("shows error message when error is present", () => {
    const messages = [{ id: "1", role: "user", content: "Test", timestamp: "2024-01-01T00:00:00Z" }];
    render(
      <ChatTimeline
        messages={messages as never[]}
        isLoadingMessages={false}
        isGenerating={false}
        isImagePending={false}
        isSpeechPending={false}
        error={new Error("timeout")}
        messagesEndRef={createRef()}
      />,
    );
    expect(screen.getByText("Connection error")).toBeInTheDocument();
  });

  it("does not show error when error is null", () => {
    const messages = [{ id: "1", role: "user", content: "Test", timestamp: "2024-01-01T00:00:00Z" }];
    render(
      <ChatTimeline
        messages={messages as never[]}
        isLoadingMessages={false}
        isGenerating={false}
        isImagePending={false}
        isSpeechPending={false}
        error={null}
        messagesEndRef={createRef()}
      />,
    );
    expect(screen.queryByText("Connection error")).not.toBeInTheDocument();
  });
});
