import "@testing-library/jest-dom";
import { render, screen } from "@testing-library/react";
import { translations, type Locale } from "@/core/context/translations";
import { AuthFlagsProvider } from "@/core/context/AuthFlagsContext";
import { AuthGate } from "@/features/auth/components/AuthGate";

let mockLocale: Locale = "en";
jest.mock("@/core/context/LocaleContext", () => ({
  useTranslation: () => ({ t: translations[mockLocale], locale: mockLocale, setLocale: jest.fn() }),
}));

const Form = jest.fn(() => <p>the form</p>);

function renderGate(flag: "login" | "signup", flags: { login: boolean; signup: boolean }, locale: Locale = "en") {
  mockLocale = locale;
  return render(
    <AuthFlagsProvider flags={{ ...flags, github: false, google: false }}>
      <AuthGate flag={flag}>
        <Form />
      </AuthGate>
    </AuthFlagsProvider>,
  );
}

beforeEach(() => Form.mockClear());

describe("AuthGate", () => {
  it("shows the calm closed notice and never mounts the form (no backend call) when the flag is off", () => {
    renderGate("login", { login: false, signup: true });
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(
      "Sovkora is in development — sign-in is closed on this site",
    );
    expect(Form).not.toHaveBeenCalled();
    expect(screen.queryByText("the form")).toBeNull();
  });

  it("says it in French too", () => {
    renderGate("signup", { login: true, signup: false }, "fr");
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(translations.fr.landing.closed.title);
  });

  it("mounts the form when its flag is on", () => {
    renderGate("signup", { login: false, signup: true });
    expect(screen.getByText("the form")).toBeInTheDocument();
  });

  it("is closed without any provider (the default)", () => {
    mockLocale = "en";
    render(
      <AuthGate flag="login">
        <Form />
      </AuthGate>,
    );
    expect(Form).not.toHaveBeenCalled();
  });
});
