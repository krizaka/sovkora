import "@testing-library/jest-dom";
import { render, screen, fireEvent } from "@testing-library/react";
import { AiProvidersSection } from "@/features/profile/components/AiProvidersSection";

interface HookState {
  isLoading: boolean;
  configuredIds: string[];
  availableIds: string[];
  modal: { mode: "closed" | "add" | "edit"; providerId: string | null };
  draftKey: string;
  isRevealed: boolean;
  isSaving: boolean;
  error: boolean;
  openAdd: jest.Mock;
  openEdit: jest.Mock;
  selectProvider: jest.Mock;
  closeModal: jest.Mock;
  setDraftKey: jest.Mock;
  toggleReveal: jest.Mock;
  submit: jest.Mock;
  remove: jest.Mock;
}

let mockHookState: HookState;

jest.mock("@/features/profile/hooks/useAiProviders", () => ({
  useAiProviders: () => mockHookState,
}));

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({
    t: {
      providers: {
        title: "AI Providers",
        subtitle: "Connect your AI providers",
        addConnection: "Add connection",
        editConnection: "Edit connection",
        modalSubtitle: "Keys are encrypted.",
        loading: "Loading connections…",
        allConfigured: "All available providers are connected.",
        cancel: "Cancel",
      },
    },
    locale: "en",
  }),
}));

jest.mock("@krizaka/orazaka-design-system", () => ({
  Card: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  CardHeader: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  CardTitle: ({ children }: { children: React.ReactNode }) => <h3>{children}</h3>,
  CardDescription: ({ children }: { children: React.ReactNode }) => <p>{children}</p>,
  CardContent: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  Button: ({ children, ...props }: React.ButtonHTMLAttributes<HTMLButtonElement>) => (
    <button {...props}>{children}</button>
  ),
  Icon: () => <span />,
}));

jest.mock("@/features/profile/components/AiProvidersParts", () => ({
  ProviderRow: ({
    provider,
    onEdit,
    onDelete,
  }: {
    provider: { id: string; name: string };
    onEdit: () => void;
    onDelete: () => void;
  }) => (
    <li data-testid={`row-${provider.id}`}>
      {provider.name}
      <button onClick={onEdit}>edit-{provider.id}</button>
      <button onClick={onDelete}>del-{provider.id}</button>
    </li>
  ),
  ProviderEmptyState: ({ onAdd }: { onAdd: () => void }) => (
    <div data-testid="empty">
      <button onClick={onAdd}>empty-add</button>
    </div>
  ),
}));

jest.mock("@/features/profile/components/AiProviderConnectionForm", () => ({
  ProviderConnectionForm: ({ mode }: { mode: string }) => <div data-testid="form">{mode}</div>,
}));

function buildState(overrides: Partial<HookState> = {}): HookState {
  return {
    isLoading: false,
    configuredIds: [],
    availableIds: ["gemini", "claude", "openai", "mistral", "groq", "ollama"],
    modal: { mode: "closed", providerId: null },
    draftKey: "",
    isRevealed: false,
    isSaving: false,
    error: false,
    openAdd: jest.fn(),
    openEdit: jest.fn(),
    selectProvider: jest.fn(),
    closeModal: jest.fn(),
    setDraftKey: jest.fn(),
    toggleReveal: jest.fn(),
    submit: jest.fn(),
    remove: jest.fn(),
    ...overrides,
  };
}

describe("AiProvidersSection", () => {
  it("shows the loading state", () => {
    mockHookState = buildState({ isLoading: true });
    render(<AiProvidersSection fetchHeaders={async () => ({})} />);
    expect(screen.getByText("Loading connections…")).toBeInTheDocument();
  });

  it("shows the empty state when nothing is configured", () => {
    mockHookState = buildState({ configuredIds: [] });
    render(<AiProvidersSection fetchHeaders={async () => ({})} />);
    expect(screen.getByTestId("empty")).toBeInTheDocument();
    expect(screen.queryByText("Add connection")).not.toBeInTheDocument();
  });

  it("renders a row per configured provider with an add button", () => {
    mockHookState = buildState({
      configuredIds: ["openai", "gemini"],
      availableIds: ["claude"],
    });
    render(<AiProvidersSection fetchHeaders={async () => ({})} />);
    expect(screen.getByTestId("row-openai")).toBeInTheDocument();
    expect(screen.getByTestId("row-gemini")).toBeInTheDocument();
    expect(screen.getByText("Add connection")).toBeInTheDocument();
  });

  it("invokes openAdd from the header button", () => {
    const state = buildState({ configuredIds: ["openai"], availableIds: ["claude"] });
    mockHookState = state;
    render(<AiProvidersSection fetchHeaders={async () => ({})} />);
    fireEvent.click(screen.getByText("Add connection"));
    expect(state.openAdd).toHaveBeenCalledTimes(1);
  });

  it("invokes remove and openEdit from row actions", () => {
    const state = buildState({ configuredIds: ["openai"], availableIds: [] });
    mockHookState = state;
    render(<AiProvidersSection fetchHeaders={async () => ({})} />);
    fireEvent.click(screen.getByText("edit-openai"));
    fireEvent.click(screen.getByText("del-openai"));
    expect(state.openEdit).toHaveBeenCalledWith("openai");
    expect(state.remove).toHaveBeenCalledWith("openai");
  });

  it("opens the dialog with the form when modal mode is add", () => {
    mockHookState = buildState({ modal: { mode: "add", providerId: "gemini" } });
    render(<AiProvidersSection fetchHeaders={async () => ({})} />);
    // The real @krizaka/ui dialog (portalled, Radix): found by its role.
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByTestId("form")).toHaveTextContent("add");
  });

  it("keeps the dialog closed when modal mode is closed", () => {
    mockHookState = buildState();
    render(<AiProvidersSection fetchHeaders={async () => ({})} />);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("shows the all-configured note when no providers remain", () => {
    mockHookState = buildState({ configuredIds: ["openai"], availableIds: [] });
    render(<AiProvidersSection fetchHeaders={async () => ({})} />);
    expect(screen.getByText("All available providers are connected.")).toBeInTheDocument();
  });
});
