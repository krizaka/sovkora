import React from "react";
import { render, screen, waitFor } from "@testing-library/react-native";
import { ThemeProvider } from "@krizaka/ui/native";
import { brands } from "@krizaka/tokens/native";
import { AppNavigator } from "../../navigation/AppNavigator";
import { AuthProvider } from "../../core/AuthContext";
import { resetAppConfigClient, signupOpenFrom } from "../../config/signupOpen";
import { answerFetch } from "../../__tests__/test-api";

// krizaka-internal#2402: sign-up is closed everywhere, so the sign-in screen offers « Créer un compte » only when the
// server's capability says so (`flags.signupOpen === true`). The server keeps refusing a registration either way.
jest.mock("expo-secure-store", () => ({
  getItemAsync: async () => null,
  setItemAsync: async () => undefined,
  deleteItemAsync: async () => undefined,
}));

const config = (flags: Record<string, boolean | number | string>) => ({
  platform: "ios",
  minSupportedVersion: "0.0.0",
  latestVersion: "0.0.0",
  updateMode: "none",
  storeUrl: null,
  maintenance: { on: false, message: null },
  contract: { min: 1, max: 1, sunset: null },
  flags,
  message: null,
  locale: "fr",
});

const open = async () => {
  await render(
    <ThemeProvider defaultMode="dark" overrides={brands.orazaka}>
      <AuthProvider>
        <AppNavigator />
      </AuthProvider>
    </ThemeProvider>,
  );
  await screen.findByTestId("login-email");
};

describe("signupOpenFrom", () => {
  it("opens only on an explicit true", () => {
    expect(signupOpenFrom({ flags: { signupOpen: true } })).toBe(true);
    expect(signupOpenFrom({ flags: { signupOpen: false } })).toBe(false);
    expect(signupOpenFrom({ flags: {} })).toBe(false);
    expect(signupOpenFrom({ flags: { signupOpen: "true" } })).toBe(false);
    expect(signupOpenFrom(null)).toBe(false);
  });
});

describe("the sign-in screen and sign-up", () => {
  beforeEach(() => resetAppConfigClient());

  it("shows no sign-up entry while the server says closed", async () => {
    const calls = answerFetch({ "/api/v1/app-config": config({ signupOpen: false }) });
    await open();
    await waitFor(() => expect(calls.some((c) => c.path.startsWith("/api/v1/app-config"))).toBe(true));
    expect(screen.queryByTestId("login-create-account")).toBeNull();
  });

  it("shows no sign-up entry when the flag is absent", async () => {
    const calls = answerFetch({ "/api/v1/app-config": config({}) });
    await open();
    await waitFor(() => expect(calls.some((c) => c.path.startsWith("/api/v1/app-config"))).toBe(true));
    expect(screen.queryByTestId("login-create-account")).toBeNull();
  });

  it("shows no sign-up entry when the config cannot be read", async () => {
    answerFetch({});
    await open();
    expect(screen.queryByTestId("login-create-account")).toBeNull();
  });

  it("shows the entry when the server says open", async () => {
    answerFetch({ "/api/v1/app-config": config({ signupOpen: true }) });
    await open();
    expect(await screen.findByTestId("login-create-account")).toBeTruthy();
  });
});
