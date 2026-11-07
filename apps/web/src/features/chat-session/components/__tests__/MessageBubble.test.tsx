import "@testing-library/jest-dom";
import { render, screen, fireEvent } from "@testing-library/react";
import { MessageBubble } from "@/features/chat-session/components/MessageBubble";

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({
    t: {
      chat: {
        copyMessage: "Copy",
        copiedMessage: "Copied",
      },
    },
    locale: "en",
  }),
}));

// Mock clipboard API
Object.assign(navigator, {
  clipboard: {
    writeText: jest.fn().mockResolvedValue(undefined),
  },
});

describe("MessageBubble", () => {
  const userMsg = {
    id: "1",
    role: "user" as const,
    kind: "text" as const,
    content: "Hello there!",
    timestamp: new Date("2024-06-01T14:30:00Z").getTime(),
  };

  const aiMsg = {
    id: "2",
    role: "assistant" as const,
    kind: "text" as const,
    content: "Hi! How can I help?",
    timestamp: new Date("2024-06-01T14:30:05Z").getTime(),
  };

  it("renders user message content", () => {
    render(<MessageBubble message={userMsg} />);
    expect(screen.getByText("Hello there!")).toBeInTheDocument();
  });

  it("renders AI message content", () => {
    render(<MessageBubble message={aiMsg} />);
    expect(screen.getByText("Hi! How can I help?")).toBeInTheDocument();
  });

  it("shows 'U' avatar for user messages", () => {
    render(<MessageBubble message={userMsg} />);
    expect(screen.getByText("U")).toBeInTheDocument();
  });

  it("shows the brand mark (no 'AI' text) for assistant messages", () => {
    const { container } = render(<MessageBubble message={aiMsg} />);
    expect(screen.queryByText("AI")).not.toBeInTheDocument();
    expect(container.querySelector("svg")).toBeInTheDocument();
  });

  it("renders formatted timestamp", () => {
    render(<MessageBubble message={userMsg} />);
    // date-fns format(timestamp, "HH:mm") => "14:30" (depends on TZ)
    // Just verify there's some time-like text
    const timeElements = screen.getAllByText(/\d{2}:\d{2}/);
    expect(timeElements.length).toBeGreaterThanOrEqual(1);
  });

  it("renders copy button", () => {
    render(<MessageBubble message={userMsg} />);
    expect(screen.getByLabelText("Copy")).toBeInTheDocument();
  });

  it("calls clipboard API when copy button clicked", async () => {
    render(<MessageBubble message={userMsg} />);
    const copyBtn = screen.getByLabelText("Copy");
    fireEvent.click(copyBtn);
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith("Hello there!");
  });

  it("applies stagger animation delay based on index", () => {
    const { container } = render(<MessageBubble message={userMsg} index={3} />);
    const article = container.querySelector("article");
    expect(article?.style.animationDelay).toBe("150ms");
  });

  it("caps animation delay at 300ms", () => {
    const { container } = render(<MessageBubble message={userMsg} index={10} />);
    const article = container.querySelector("article");
    expect(article?.style.animationDelay).toBe("300ms");
  });

  it("aligns user messages to the right", () => {
    const { container } = render(<MessageBubble message={userMsg} />);
    const article = container.querySelector("article");
    expect(article?.className).toContain("justify-end");
  });

  it("aligns AI messages to the left", () => {
    const { container } = render(<MessageBubble message={aiMsg} />);
    const article = container.querySelector("article");
    expect(article?.className).toContain("justify-start");
  });
});
