import "@testing-library/jest-dom";
import { render, screen, fireEvent } from "@testing-library/react";
import ConnectorCatalogue from "@/features/automation/components/ConnectorCatalogue";
import { translations } from "@/core/context/translations";

jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: jest.requireActual("@/core/context/translations").translations.en, locale: "en" }),
}));

const t = translations.en.automation;

describe("ConnectorCatalogue", () => {
  it("renders the translated title and subtitle", () => {
    render(<ConnectorCatalogue />);
    expect(screen.getByRole("heading", { name: t.connectorsTitle })).toBeInTheDocument();
    expect(screen.getByText(t.connectorsSubtitle)).toBeInTheDocument();
  });

  it("renders every connector with its description", () => {
    render(<ConnectorCatalogue />);
    for (const name of ["Jira Cloud", "WhatsApp Business", "Messenger", "Slack", "Local CLI Agent"]) {
      expect(screen.getByText(name)).toBeInTheDocument();
    }
    expect(screen.getByText(t.connectors.jira)).toBeInTheDocument();
  });

  it("starts with every connector inactive, each with a connect button", () => {
    render(<ConnectorCatalogue />);
    expect(screen.getAllByText(t.status.disconnected)).toHaveLength(5);
    expect(screen.getAllByText(t.connect)).toHaveLength(5);
  });

  it("connects and disconnects a connector", () => {
    render(<ConnectorCatalogue />);
    const button = screen.getByRole("button", { name: t.toggle.replace("{name}", "Slack") });
    fireEvent.click(button);
    expect(screen.getByText(t.status.connected)).toBeInTheDocument();
    expect(screen.getByText(t.disconnect)).toBeInTheDocument();
    fireEvent.click(button);
    expect(screen.queryByText(t.disconnect)).toBeNull();
  });

  it("opens and closes the credentials panel", () => {
    render(<ConnectorCatalogue />);
    const configure = screen.getByRole("button", { name: t.configure.replace("{name}", "Jira Cloud") });
    fireEvent.click(configure);
    expect(configure).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByLabelText(t.apiKey)).toHaveAttribute("type", "password");
    expect(screen.getByRole("button", { name: t.saveCredentials })).toBeInTheDocument();
    fireEvent.click(configure);
    expect(screen.queryByLabelText(t.apiKey)).toBeNull();
  });

  it("draws no emoji and no inline brand colour", () => {
    const { container } = render(<ConnectorCatalogue />);
    expect(container.innerHTML).not.toMatch(/style="[^"]*hsl\(/);
    expect(container.textContent).not.toMatch(/\p{Extended_Pictographic}/u);
  });
});
