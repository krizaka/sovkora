import "@testing-library/jest-dom";
import { render, screen } from "@testing-library/react";
import { ChatMessage } from "@/features/chat-session/components/ChatMessage";

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const { translations } = require("@/core/context/translations");
    return { t: { ...translations.en, chat: { user: "You", ai: "AI" } }, locale: "en" };
  },
}));

jest.mock("react-markdown", () => ({
  __esModule: true,
  default: ({ children }: { children: string }) => <p>{children}</p>,
}));

const baseMsg = {
  id: "m1",
  role: "user" as const,
  content: "Hello world",
  kind: "text" as const,
  timestamp: new Date("2026-01-01T14:30:00Z").getTime(),
};

describe("ChatMessage", () => {
  it("renders text content", () => {
    render(<ChatMessage message={baseMsg} />);
    expect(screen.getByText("Hello world")).toBeInTheDocument();
  });

  it("renders user avatar with first letter", () => {
    render(<ChatMessage message={baseMsg} />);
    expect(screen.getByText("Y")).toBeInTheDocument();
  });

  it("renders the brand mark (no 'AI' text) for assistant messages", () => {
    const { container } = render(<ChatMessage message={{ ...baseMsg, role: "assistant" }} />);
    expect(screen.queryByText("AI")).not.toBeInTheDocument();
    expect(container.querySelector("svg")).toBeInTheDocument();
  });

  it("renders timestamp", () => {
    render(<ChatMessage message={baseMsg} />);
    // format(timestamp, "HH:mm") should produce the time
    expect(screen.getByText(/\d{2}:\d{2}/)).toBeInTheDocument();
  });

  it("renders image content for image kind", () => {
    render(<ChatMessage message={{ ...baseMsg, kind: "image", content: "data:image/png;base64,abc" }} />);
    const img = screen.getByAltText("Generated Asset");
    expect(img).toBeInTheDocument();
    expect(img).toHaveAttribute("src", "data:image/png;base64,abc");
  });

  it("renders audio element for audio kind", () => {
    const { container } = render(
      <ChatMessage message={{ ...baseMsg, kind: "audio", content: "data:audio/mp3;base64,abc" }} />,
    );
    const audio = container.querySelector("audio");
    expect(audio).toBeInTheDocument();
    expect(audio).toHaveAttribute("src", "data:audio/mp3;base64,abc");
  });

  describe("control badge", () => {
    const answer = { ...baseMsg, role: "assistant" as const };

    it("says « Verifying… » while the answer streams and the verdict is pending", () => {
      render(<ChatMessage message={{ ...answer, control: { state: "pending" } }} streaming />);
      expect(screen.getByTestId("chat-control-pending")).toHaveTextContent("Verifying…");
    });

    it("shows the verdict once it is final", () => {
      render(<ChatMessage message={{ ...answer, control: { state: "validated" } }} />);
      expect(screen.getByTestId("chat-control-validated")).toHaveTextContent("Verified");
    });

    it("shows the degraded badge with its explanation", () => {
      render(
        <ChatMessage message={{ ...answer, control: { state: "degraded", badge: "verification_unavailable" } }} />,
      );
      const badge = screen.getByTestId("chat-control-unavailable");
      expect(badge).toHaveTextContent("Verification unavailable");
      expect(badge).toHaveAttribute("title", expect.stringContaining("judge"));
    });

    it("shows nothing when no control ran, or on a user message", () => {
      const { rerender } = render(<ChatMessage message={{ ...answer, control: { state: "not_checked" } }} />);
      expect(screen.queryByRole("status")).toBeNull();
      rerender(<ChatMessage message={{ ...baseMsg, control: { state: "validated" } }} />);
      expect(screen.queryByRole("status")).toBeNull();
    });
  });
});
