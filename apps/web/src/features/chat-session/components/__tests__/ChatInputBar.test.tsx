import "@testing-library/jest-dom";
import { render, screen, fireEvent } from "@testing-library/react";
import { ChatInputBar } from "@/features/chat-session/components/ChatInputBar";

jest.mock("@krizaka/orazaka-design-system", () => ({
  ...jest.requireActual("@krizaka/orazaka-design-system"),
  Button: ({ children, ...props }: { children: React.ReactNode } & React.ButtonHTMLAttributes<HTMLButtonElement>) => (
    <button {...props}>{children}</button>
  ),
}));

jest.mock("@/features/chat-session/components/ContextPlusMenu", () => ({
  ContextPlusMenu: () => <div data-testid="context-plus-menu" />,
}));

jest.mock("@/features/chat-session/components/CapabilityOptions", () => ({
  CapabilityOptions: () => <div data-testid="capability-options" />,
}));

const defaultT = {
  a11y: { attachFile: "Attach File" },
  chat: {
    typeMessage: "Ask your private AI…",
    typing: "Typing...",
    modeChat: "Chat",
    clear: "Clear",
    localBadge: "Runs locally · nothing leaves your network",
    statusRoutedTo: "Routed to {provider}",
    statusCredits: "~{credits} credits",
    composerBusy: "Answering…",
    send: "Send",
    sending: "Sending...",
    uploadingFile: "Uploading...",
    addCapability: "Add Capability",
  },
} as never;

const baseProps = {
  input: "",
  onInputChange: jest.fn(),
  onSend: jest.fn(),
  isSending: false,
  isGenerating: false,
  isUploadingAttachment: false,
  selectedStudio: null,
  selectedOptions: {},
  onOptionsChange: jest.fn(),
  attachment: null,
  onClearStudio: jest.fn(),
  onClearAttachment: jest.fn(),
  isPlusMenuOpen: false,
  onTogglePlusMenu: jest.fn(),
  onClosePlusMenu: jest.fn(),
  onExecuteNode: jest.fn(),
  composerStudios: [],
  fileInputRef: { current: null },
  onFileChange: jest.fn(),
  routing: null,
  t: defaultT,
};

const PLACEHOLDER = "Ask your private AI…";
const studio = {
  studioKey: "image-generation",
  label: "Text Generation",
  iconKey: "chat",
  version: "1.0.0",
  capabilityKey: "orazaka.core.media.image",
  inputKey: "prompt",
  inputKind: "TEXT",
  promptKey: "prompt",
  available: true,
  lockedReason: "NONE",
} as never;

describe("ChatInputBar", () => {
  afterEach(() => jest.clearAllMocks());

  it("is one pill: the mode chip, attach, the field and the round send share one container", () => {
    render(<ChatInputBar {...baseProps} />);
    const field = screen.getByPlaceholderText(PLACEHOLDER);
    const pill = field.parentElement!;
    expect(pill).toContainElement(screen.getByLabelText("Add Capability"));
    expect(pill).toContainElement(screen.getByLabelText("Attach File"));
    expect(pill).toContainElement(screen.getByRole("button", { name: "Send" }));
    // no second border on the field itself
    expect(field.className).toContain("border-0");
  });

  it("disables the send when empty and enables it with text", () => {
    const { rerender } = render(<ChatInputBar {...baseProps} />);
    expect(screen.getByRole("button", { name: "Send" })).toBeDisabled();
    rerender(<ChatInputBar {...baseProps} input="hello" />);
    expect(screen.getByRole("button", { name: "Send" })).not.toBeDisabled();
  });

  it("sends with Enter, not with Shift+Enter", () => {
    render(<ChatInputBar {...baseProps} input="hello" />);
    const field = screen.getByPlaceholderText(PLACEHOLDER);
    fireEvent.keyDown(field, { key: "Enter", shiftKey: true });
    expect(baseProps.onSend).not.toHaveBeenCalled();
    fireEvent.keyDown(field, { key: "Enter" });
    expect(baseProps.onSend).toHaveBeenCalledTimes(1);
  });

  it("calls onInputChange on change", () => {
    render(<ChatInputBar {...baseProps} />);
    fireEvent.change(screen.getByPlaceholderText(PLACEHOLDER), { target: { value: "test" } });
    expect(baseProps.onInputChange).toHaveBeenCalledWith("test");
  });

  it("while answering the box does not change: same placeholder, send held, chip locked, status says so", () => {
    const { rerender } = render(<ChatInputBar {...baseProps} input="hello" />);
    const before = screen.getByPlaceholderText(PLACEHOLDER).parentElement!.className;
    rerender(<ChatInputBar {...baseProps} input="hello" isGenerating />);
    expect(screen.getByPlaceholderText(PLACEHOLDER).parentElement!.className).toBe(before);
    expect(screen.getByRole("button", { name: "Send" })).toBeDisabled();
    expect(screen.getByLabelText("Add Capability")).toBeDisabled();
    fireEvent.keyDown(screen.getByPlaceholderText(PLACEHOLDER), { key: "Enter" });
    expect(baseProps.onSend).not.toHaveBeenCalled();
    expect(screen.getByRole("status")).toHaveTextContent("Answering…");
  });

  it("claims 'runs locally' only when the last routing was local, and shows the estimate", () => {
    const { rerender } = render(<ChatInputBar {...baseProps} />);
    expect(screen.getByRole("status")).toBeEmptyDOMElement();
    rerender(<ChatInputBar {...baseProps} routing={{ provider: "ollama", local: true, estimatedCredits: 30 }} />);
    expect(screen.getByRole("status")).toHaveTextContent("Runs locally · nothing leaves your network");
    expect(screen.getByRole("status")).toHaveTextContent("~30 credits");
    rerender(<ChatInputBar {...baseProps} routing={{ provider: "openai", local: false }} />);
    expect(screen.getByRole("status")).not.toHaveTextContent("Runs locally");
    expect(screen.getByRole("status")).toHaveTextContent("Routed to openai");
  });

  it("shows the staged Studio's label when one is selected, and can send without text", () => {
    render(<ChatInputBar {...baseProps} selectedStudio={studio} />);
    expect(screen.getByText(/Text Generation/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Send" })).not.toBeDisabled();
  });

  it("shows attachment and uploading indicators", () => {
    const { rerender } = render(<ChatInputBar {...baseProps} attachment={{ assetId: "a1", name: "doc.pdf" }} />);
    expect(screen.getByText(/doc.pdf/)).toBeInTheDocument();
    rerender(<ChatInputBar {...baseProps} isUploadingAttachment />);
    expect(screen.getByText("Uploading...")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Send" })).toBeDisabled();
  });

  it("renders ContextPlusMenu", () => {
    render(<ChatInputBar {...baseProps} />);
    expect(screen.getByTestId("context-plus-menu")).toBeInTheDocument();
  });
});
