import "@testing-library/jest-dom";
import { render, screen, fireEvent } from "@testing-library/react";
import { ChatDrawer } from "@/features/chat-session/components/ChatDrawer";

jest.mock("@/features/chat-session/components/ThreadList", () => ({
  ThreadList: () => <div data-testid="thread-list">threads</div>,
}));

const baseProps = {
  isOpen: true,
  onClose: jest.fn(),
  threads: [],
  activeConversationId: "c1",
  onSelectThread: jest.fn(),
  isLoadingThreads: false,
  onCreateThread: jest.fn(),
  onDeleteThread: jest.fn(),
  t: { chat: { memoryBlocks: "Memory Blocks" }, a11y: { closeHistory: "Close the conversations" } },
};

describe("ChatDrawer", () => {
  afterEach(() => jest.clearAllMocks());

  it("renders nothing when closed", () => {
    render(<ChatDrawer {...baseProps} isOpen={false} />);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("is a dialog named by its title when open", () => {
    render(<ChatDrawer {...baseProps} />);
    expect(screen.getByRole("dialog", { name: "Memory Blocks" })).toBeInTheDocument();
  });

  it("renders thread list", () => {
    render(<ChatDrawer {...baseProps} />);
    expect(screen.getByTestId("thread-list")).toBeInTheDocument();
  });

  it("closes from its translated close button", () => {
    render(<ChatDrawer {...baseProps} />);
    fireEvent.click(screen.getByRole("button", { name: "Close the conversations" }));
    expect(baseProps.onClose).toHaveBeenCalled();
  });

  it("closes on Escape", () => {
    render(<ChatDrawer {...baseProps} />);
    fireEvent.keyDown(screen.getByRole("dialog"), { key: "Escape" });
    expect(baseProps.onClose).toHaveBeenCalled();
  });
});
