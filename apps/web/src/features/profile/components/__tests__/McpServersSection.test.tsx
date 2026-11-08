import "@testing-library/jest-dom";
import { render, screen, waitFor } from "@testing-library/react";
import { queryWrapper } from "@/core/testing/queryWrapper";
import { McpServersSection } from "@/features/profile/components/McpServersSection";

jest.mock("@/core/hooks/useAuth", () => ({
  useAuth: () => ({
    user: { name: "Test", email: "t@t.com", role: "admin" },
    isAuthenticated: true,
  }),
}));

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({
    t: {
      settings: {
        mcpServersTitle: "MCP Servers",
        mcpServersDesc: "Configure Model Context Protocol servers",
        mcpLabel: "Server Name",
        mcpLabelPlaceholder: "Enter server name",
        mcpTransportType: "Transport Type",
        mcpRemoteLabel: "Remote SSE",
        mcpLocalLabel: "Local STDIO",
        mcpUrl: "Server URL",
        mcpUrlPlaceholder: "https://...",
        mcpAuthToken: "Auth Token",
        mcpAuthTokenPlaceholder: "Optional",
        mcpCommand: "Command",
        mcpCommandPlaceholder: "npx ...",
        mcpArgs: "Arguments",
        mcpArgsPlaceholder: "--arg",
        mcpRegister: "Register Server",
        mcpDelete: "Delete",
        mcpNoServers: "No MCP servers configured",
        mcpLoading: "Loading...",
        mcpSuccess: "Saved",
        mcpError: "Error",
        mcpDeleteSuccess: "Deleted",
        mcpDeleteError: "Delete failed",
      },
      errors: { generic: "An error occurred" },
    },
    locale: "en",
  }),
}));

jest.mock("@krizaka/orazaka-design-system", () => ({
  ...jest.requireActual("@krizaka/orazaka-design-system"),
  Input: (props: React.InputHTMLAttributes<HTMLInputElement>) => <input {...props} />,
  Button: ({ children, ...props }: { children: React.ReactNode } & React.ButtonHTMLAttributes<HTMLButtonElement>) => (
    <button {...props}>{children}</button>
  ),
}));

describe("McpServersSection", () => {
  beforeEach(() => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve([]),
    }) as jest.Mock;
  });

  afterEach(() => jest.restoreAllMocks());

  const fetchHeaders = jest.fn().mockResolvedValue({});

  it("renders title and description", async () => {
    render(<McpServersSection fetchHeaders={fetchHeaders} />, { wrapper: queryWrapper() });
    expect(screen.getByText("MCP Servers")).toBeInTheDocument();
    expect(screen.getByText("Configure Model Context Protocol servers")).toBeInTheDocument();
  });

  it("renders register button", () => {
    render(<McpServersSection fetchHeaders={fetchHeaders} />, { wrapper: queryWrapper() });
    expect(screen.getByText("Register Server")).toBeInTheDocument();
  });

  it("renders server name input", () => {
    render(<McpServersSection fetchHeaders={fetchHeaders} />, { wrapper: queryWrapper() });
    expect(screen.getByText("Server Name")).toBeInTheDocument();
  });

  it("shows no servers message after load", async () => {
    render(<McpServersSection fetchHeaders={fetchHeaders} />, { wrapper: queryWrapper() });
    await waitFor(() => {
      expect(screen.getByText("No MCP servers configured")).toBeInTheDocument();
    });
  });

  it("renders transport type selector for admin", () => {
    render(<McpServersSection fetchHeaders={fetchHeaders} />, { wrapper: queryWrapper() });
    expect(screen.getByText("Transport Type")).toBeInTheDocument();
  });
});
