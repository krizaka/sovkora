import "@testing-library/jest-dom";
import { render, screen, fireEvent } from "@testing-library/react";
import { WelcomeHero } from "@/features/chat-session/components/WelcomeHero";

jest.mock("@/core/hooks/useAuth", () => ({
  useAuth: () => ({ user: { name: "Jordan Doe" } }),
}));
jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({
    t: {
      dashboard: {
        welcome: "Welcome",
        greeting: {
          morning: "Good morning, {name}",
          afternoon: "Good afternoon, {name}",
          evening: "Good evening, {name}",
        },
      },
    },
  }),
}));

const t = {
  chat: {
    startConversationDesc: "Choose a topic or type anything",
    suggestionImage: "Generate an image",
    suggestionCode: "Draft a client e-mail",
    suggestionAsk: "Ask anything",
  },
} as never;

describe("WelcomeHero", () => {
  it("greets the user by first name without emoji", () => {
    const { container } = render(<WelcomeHero t={t} onPrompt={jest.fn()} />);
    expect(screen.getByText("Jordan")).toBeInTheDocument();
    // No emoji characters in the greeting block.
    expect(container.textContent).not.toMatch(/[\u{1F300}-\u{1FAFF}☀-➿]/u);
  });

  it("renders the three prompt cards", () => {
    render(<WelcomeHero t={t} onPrompt={jest.fn()} />);
    expect(screen.getByText("Generate an image")).toBeInTheDocument();
    expect(screen.getByText("Draft a client e-mail")).toBeInTheDocument();
    expect(screen.getByText("Ask anything")).toBeInTheDocument();
  });

  it("submits the prompt when a card is clicked", () => {
    const onPrompt = jest.fn();
    render(<WelcomeHero t={t} onPrompt={onPrompt} />);
    fireEvent.click(screen.getByText("Draft a client e-mail"));
    expect(onPrompt).toHaveBeenCalledWith("Draft a client e-mail");
  });
});
