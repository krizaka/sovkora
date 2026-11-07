import "@testing-library/jest-dom";
import { render, screen, fireEvent } from "@testing-library/react";
import { ChatHeader } from "@/features/chat-session/components/ChatHeader";

const mockT = {
  chat: {
    sessionTitle: "New Chat",
    id: "ID",
  },
  a11y: { renameConversation: "Rename this conversation", openConversations: "Open your conversations" },
} as never;

describe("ChatHeader", () => {
  const defaultProps = {
    activeConversationId: "conv-123",
    threadTitle: "My Chat",
    onOpenDrawer: jest.fn(),
    onRename: jest.fn(),
    t: mockT,
  };

  afterEach(() => jest.clearAllMocks());

  it("renders thread title", () => {
    render(<ChatHeader {...defaultProps} />);
    expect(screen.getByText("My Chat")).toBeInTheDocument();
  });

  it("does not show the conversation's technical id", () => {
    render(<ChatHeader {...defaultProps} />);
    expect(screen.queryByText(/conv-123/)).not.toBeInTheDocument();
  });

  it("renders fallback title when empty", () => {
    render(<ChatHeader {...defaultProps} threadTitle="" />);
    expect(screen.getByText("New Chat")).toBeInTheDocument();
  });

  it("enters edit mode on rename click", () => {
    render(<ChatHeader {...defaultProps} />);
    fireEvent.click(screen.getByTitle("Rename this conversation"));
    expect(screen.getByDisplayValue("My Chat")).toBeInTheDocument();
  });

  it("calls onRename on Enter key", () => {
    render(<ChatHeader {...defaultProps} />);
    fireEvent.click(screen.getByTitle("Rename this conversation"));
    const input = screen.getByDisplayValue("My Chat");
    fireEvent.change(input, { target: { value: "Renamed Chat" } });
    fireEvent.keyDown(input, { key: "Enter" });
    expect(defaultProps.onRename).toHaveBeenCalledWith("Renamed Chat");
  });

  it("cancels edit on Escape key", () => {
    render(<ChatHeader {...defaultProps} />);
    fireEvent.click(screen.getByTitle("Rename this conversation"));
    const input = screen.getByDisplayValue("My Chat");
    fireEvent.change(input, { target: { value: "Changed" } });
    fireEvent.keyDown(input, { key: "Escape" });
    expect(screen.getByText("My Chat")).toBeInTheDocument();
    expect(defaultProps.onRename).not.toHaveBeenCalled();
  });

  it("calls onRename on blur", () => {
    render(<ChatHeader {...defaultProps} />);
    fireEvent.click(screen.getByTitle("Rename this conversation"));
    const input = screen.getByDisplayValue("My Chat");
    fireEvent.change(input, { target: { value: "New Name" } });
    fireEvent.blur(input);
    expect(defaultProps.onRename).toHaveBeenCalledWith("New Name");
  });

  it("does not call onRename if title unchanged", () => {
    render(<ChatHeader {...defaultProps} />);
    fireEvent.click(screen.getByTitle("Rename this conversation"));
    fireEvent.keyDown(screen.getByDisplayValue("My Chat"), { key: "Enter" });
    expect(defaultProps.onRename).not.toHaveBeenCalled();
  });

  it("calls onOpenDrawer on hamburger click", () => {
    render(<ChatHeader {...defaultProps} />);
    // The hamburger button is the first button
    const buttons = screen.getAllByRole("button");
    fireEvent.click(buttons[0]);
    expect(defaultProps.onOpenDrawer).toHaveBeenCalled();
  });
});
