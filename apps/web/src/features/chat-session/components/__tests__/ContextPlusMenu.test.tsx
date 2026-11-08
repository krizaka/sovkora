import "@testing-library/jest-dom";
import { render, screen, fireEvent } from "@testing-library/react";
import { ContextPlusMenu } from "@/features/chat-session/components/ContextPlusMenu";
import type { ComposerStudio } from "@krizaka/orazaka-shared";

// The grouping is read off what each Studio's input HOLDS, never off a path: an
// attachment Studio is an analysis, whatever it is called (ADR-068 §3).
const studios: ComposerStudio[] = [
  {
    studioKey: "image-generation",
    label: "Image Generation",
    iconKey: "image",
    version: "1.0.0",
    capabilityKey: "orazaka.core.media.image",
    inputKey: "prompt",
    inputKind: "TEXT",
    promptKey: "prompt",
    available: true,
    lockedReason: "NONE",
  },
  {
    studioKey: "image-analysis",
    label: "Image Analysis",
    iconKey: "vision",
    version: "1.1.0",
    capabilityKey: "orazaka.core.media.vision",
    inputKey: "assetId",
    inputKind: "ASSET",
    promptKey: "prompt",
    available: true,
    lockedReason: "NONE",
  },
];

const t = {
  chat: {
    capGeneration: "Generation",
    capAnalysis: "Analysis",
    noActiveConversation: "No active conversation.",
  },
} as never;

const baseProps = {
  isOpen: true,
  onClose: jest.fn(),
  onExecuteNode: jest.fn(),
  studios,
  t,
};

describe("ContextPlusMenu", () => {
  afterEach(() => jest.clearAllMocks());

  it("renders nothing when closed", () => {
    const { container } = render(<ContextPlusMenu {...baseProps} isOpen={false} />);
    expect(container.innerHTML).toBe("");
  });

  it("groups Studios into Generation and Analysis by what their input holds", () => {
    render(<ContextPlusMenu {...baseProps} />);
    expect(screen.getByText("Generation")).toBeInTheDocument();
    expect(screen.getByText("Analysis")).toBeInTheDocument();
  });

  it("renders Studio labels with vector icons (no emoji)", () => {
    const { container } = render(<ContextPlusMenu {...baseProps} />);
    expect(screen.getByText("Image Generation")).toBeInTheDocument();
    expect(screen.getByText("Image Analysis")).toBeInTheDocument();
    expect(container.textContent).not.toMatch(/[\u{1F300}-\u{1FAFF}]/u);
    expect(container.querySelectorAll("svg").length).toBeGreaterThanOrEqual(2);
  });

  it("calls onExecuteNode and onClose when a Studio is clicked", () => {
    render(<ContextPlusMenu {...baseProps} />);
    fireEvent.click(screen.getByText("Image Generation"));
    expect(baseProps.onExecuteNode).toHaveBeenCalledWith(studios[0]);
    expect(baseProps.onClose).toHaveBeenCalled();
  });

  it("shows an empty state when there are no Studios", () => {
    render(<ContextPlusMenu {...baseProps} studios={[]} />);
    expect(screen.getByText("No active conversation.")).toBeInTheDocument();
  });
});
