import "@testing-library/jest-dom";
import { render, screen, fireEvent } from "@testing-library/react";
import { ThreadList } from "@/features/chat-session/components/ThreadList";

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({
    t: {
      chat: {
        memoryBlocks: "Memory Blocks",
        newBlock: "New Block",
      },
      a11y: { deleteConversation: "Delete this conversation" },
    },
    locale: "en",
  }),
}));

const threads = [
  {
    conversationId: "c1",
    title: "First Thread",
    updatedAt: new Date("2026-01-10T00:00:00Z").getTime(),
  },
  {
    conversationId: "c2",
    title: "Second Thread",
    updatedAt: new Date("2026-01-11T00:00:00Z").getTime(),
  },
];

const baseProps = {
  threads,
  activeId: "c1",
  onSelectThread: jest.fn(),
  isLoading: false,
  onCreateThread: jest.fn(),
  onDeleteThread: jest.fn(),
};

describe("ThreadList", () => {
  afterEach(() => jest.clearAllMocks());

  it("renders loading skeletons when loading", () => {
    const { container } = render(<ThreadList {...baseProps} isLoading={true} />);
    const skeletons = container.querySelectorAll(".h-12");
    expect(skeletons).toHaveLength(3);
  });

  it("renders section title", () => {
    render(<ThreadList {...baseProps} />);
    expect(screen.getByText("Memory Blocks")).toBeInTheDocument();
  });

  it("renders new block button", () => {
    render(<ThreadList {...baseProps} />);
    expect(screen.getByText("New Block")).toBeInTheDocument();
  });

  it("renders thread titles", () => {
    render(<ThreadList {...baseProps} />);
    expect(screen.getByText("First Thread")).toBeInTheDocument();
    expect(screen.getByText("Second Thread")).toBeInTheDocument();
  });

  it("calls onSelectThread when clicking a thread", () => {
    render(<ThreadList {...baseProps} />);
    fireEvent.click(screen.getByText("Second Thread"));
    expect(baseProps.onSelectThread).toHaveBeenCalledWith("c2");
  });

  it("calls onCreateThread when new block clicked", () => {
    render(<ThreadList {...baseProps} />);
    fireEvent.click(screen.getByText("New Block"));
    expect(baseProps.onCreateThread).toHaveBeenCalled();
  });
});
