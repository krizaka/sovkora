import "@testing-library/jest-dom";
import { render, screen, fireEvent } from "@testing-library/react";
import { ProviderRow, ProviderEmptyState } from "@/features/profile/components/AiProvidersParts";
import { ProviderConnectionForm } from "@/features/profile/components/AiProviderConnectionForm";
import { PROVIDERS } from "@/features/profile/components/AiProviderLogos";

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({
    t: {
      providers: {
        connected: "Connected",
        deleteProvider: "Remove",
        editConnection: "Edit",
        apiKey: "API Key",
        keyReveal: "Reveal",
        keyHide: "Hide",
        keyPlaceholder: "Enter key",
        selectProvider: "Choose a provider",
        editKeyHint: "Enter a new key",
        saveError: "Save failed",
        cancel: "Cancel",
        update: "Update key",
        emptyTitle: "No providers connected",
        emptyDesc: "Connect a provider to unlock models.",
        emptyCta: "Add a connection",
      },
      settings: { saveCredentials: "Save Credentials" },
    },
    locale: "en",
  }),
}));

const gemini = PROVIDERS[0];
const openai = PROVIDERS[2];

describe("ProviderRow", () => {
  const props = {
    provider: gemini,
    onEdit: jest.fn(),
    onDelete: jest.fn(),
    disabled: false,
  };
  afterEach(() => jest.clearAllMocks());

  it("renders the provider name and connected status", () => {
    render(
      <ul>
        <ProviderRow {...props} />
      </ul>,
    );
    expect(screen.getByText("Google Gemini")).toBeInTheDocument();
    expect(screen.getByText("Connected")).toBeInTheDocument();
  });

  it("calls onEdit and onDelete", () => {
    render(
      <ul>
        <ProviderRow {...props} />
      </ul>,
    );
    fireEvent.click(screen.getByLabelText(/Edit/));
    fireEvent.click(screen.getByLabelText(/Remove/));
    expect(props.onEdit).toHaveBeenCalledTimes(1);
    expect(props.onDelete).toHaveBeenCalledTimes(1);
  });
});

describe("ProviderEmptyState", () => {
  it("renders the empty message and triggers onAdd", () => {
    const onAdd = jest.fn();
    render(<ProviderEmptyState onAdd={onAdd} />);
    expect(screen.getByText("No providers connected")).toBeInTheDocument();
    fireEvent.click(screen.getByText("Add a connection"));
    expect(onAdd).toHaveBeenCalledTimes(1);
  });
});

describe("ProviderConnectionForm (add)", () => {
  const baseProps = {
    mode: "add" as const,
    pickable: [gemini, openai],
    selected: undefined,
    onSelect: jest.fn(),
    draftKey: "",
    onKeyChange: jest.fn(),
    isRevealed: false,
    onToggleReveal: jest.fn(),
    isSaving: false,
    error: false,
    onCancel: jest.fn(),
    onSubmit: jest.fn(),
  };
  afterEach(() => jest.clearAllMocks());

  it("renders the provider picker and selects a provider", () => {
    render(<ProviderConnectionForm {...baseProps} />);
    expect(screen.getByText("Choose a provider")).toBeInTheDocument();
    fireEvent.click(screen.getByText("OpenAI"));
    expect(baseProps.onSelect).toHaveBeenCalledWith("openai");
  });

  it("disables save until a provider and key are present", () => {
    const { rerender } = render(<ProviderConnectionForm {...baseProps} />);
    expect(screen.getByText("Save Credentials")).toBeDisabled();
    rerender(<ProviderConnectionForm {...baseProps} selected={gemini} draftKey="AIza123" />);
    expect(screen.getByText("Save Credentials")).not.toBeDisabled();
  });

  it("calls onKeyChange while typing", () => {
    render(<ProviderConnectionForm {...baseProps} selected={gemini} />);
    fireEvent.change(screen.getByPlaceholderText(gemini.placeholder), {
      target: { value: "new-key" },
    });
    expect(baseProps.onKeyChange).toHaveBeenCalledWith("new-key");
  });

  it("shows the error message when error is true", () => {
    render(<ProviderConnectionForm {...baseProps} error />);
    expect(screen.getByText("Save failed")).toBeInTheDocument();
  });
});

describe("ProviderConnectionForm (edit)", () => {
  it("shows the fixed provider, hint and update label", () => {
    render(
      <ProviderConnectionForm
        mode="edit"
        pickable={[openai]}
        selected={openai}
        onSelect={jest.fn()}
        draftKey=""
        onKeyChange={jest.fn()}
        isRevealed={false}
        onToggleReveal={jest.fn()}
        isSaving={false}
        error={false}
        onCancel={jest.fn()}
        onSubmit={jest.fn()}
      />,
    );
    expect(screen.getByText("OpenAI")).toBeInTheDocument();
    expect(screen.getByText("Enter a new key")).toBeInTheDocument();
    expect(screen.getByText("Update key")).toBeInTheDocument();
    expect(screen.queryByText("Choose a provider")).not.toBeInTheDocument();
  });
});

describe("PROVIDERS catalogue", () => {
  it("has the 6 expected providers in order", () => {
    expect(PROVIDERS.map((p) => p.id)).toEqual(["gemini", "claude", "openai", "mistral", "groq", "ollama"]);
  });
});
