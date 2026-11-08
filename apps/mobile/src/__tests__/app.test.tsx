import React from "react";
import { fireEvent, render, screen } from "@testing-library/react-native";
import { ThemeProvider } from "@krizaka/ui/native";
import { brands } from "@krizaka/tokens/native";
import { AppNavigator } from "../navigation/AppNavigator";
import { AuthProvider } from "../core/AuthContext";
import { answerFetch } from "./test-api";
import login from "../__fixtures__/api/auth-login.local-demo.json";
import studios from "../__fixtures__/api/studios.local-demo.json";
import jobs from "../__fixtures__/api/jobs.local-demo.json";
import profile from "../__fixtures__/api/profile.local-demo.json";

// The keychain, in memory.
const mockKeychain = new Map<string, string>();
jest.mock("expo-secure-store", () => ({
  getItemAsync: async (key: string) => mockKeychain.get(key) ?? null,
  setItemAsync: async (key: string, value: string) => void mockKeychain.set(key, value),
  deleteItemAsync: async (key: string) => void mockKeychain.delete(key),
}));

const app = () =>
  render(
    <ThemeProvider defaultMode="dark" overrides={brands.orazaka}>
      <AuthProvider>
        <AppNavigator />
      </AuthProvider>
    </ThemeProvider>,
  );

const signedIn = { "/api/v1/studios": studios, "/api/v1/jobs": jobs, "/api/v1/profile": profile };

beforeEach(() => mockKeychain.clear());

describe("the app, on recorded edge responses", () => {
  it("signs in with e-mail and password, keeps the session in the keychain and opens the Studios", async () => {
    const calls = answerFetch({ "/api/v1/auth/login": login, ...signedIn });
    await app();
    await fireEvent.changeText(await screen.findByTestId("login-email"), " eric.morel@example.com ");
    await fireEvent.changeText(screen.getByTestId("login-password"), "secret");
    await fireEvent.press(screen.getByText("Sign in"));

    expect(await screen.findByText("Vitrine Artisan")).toBeTruthy();
    expect(screen.getByText("Génération d'image")).toBeTruthy();
    // A locked Studio is listed, not hidden.
    expect(screen.getByText("Locked")).toBeTruthy();

    const signIn = calls.find((c) => c.path === "/api/v1/auth/login");
    expect(signIn?.body).toEqual({ email: "eric.morel@example.com", password: "secret" });
    expect(JSON.parse(mockKeychain.get("orazaka.session") ?? "{}").token).toBe("recorded-token");
    const catalogue = calls.find((c) => c.path.startsWith("/api/v1/studios"));
    expect(catalogue?.headers.Authorization).toBe("Bearer recorded-token");
    // The platform is asked in the app's language — for the names, and in the header.
    expect(catalogue?.path).toBe("/api/v1/studios?locale=en");
    expect(catalogue?.headers["Accept-Language"]).toBe("en");
  });

  it("shows the edge's refusal on a wrong password and stays on the sign-in screen", async () => {
    answerFetch({ "/api/v1/auth/login": { status: 401, body: { error: "Invalid email or password" } } });
    await app();
    await fireEvent.changeText(await screen.findByTestId("login-email"), "eric.morel@example.com");
    await fireEvent.changeText(screen.getByTestId("login-password"), "wrong");
    await fireEvent.press(screen.getByText("Sign in"));
    expect(await screen.findByText("Invalid email or password.")).toBeTruthy();
    expect(mockKeychain.size).toBe(0);
  });

  it("opens on the Studios when a session is stored, then lists the jobs and the settings (the web rail's word)", async () => {
    mockKeychain.set("orazaka.session", JSON.stringify(login));
    answerFetch(signedIn);
    await app();
    expect(await screen.findByText("Vitrine Artisan")).toBeTruthy();

    await fireEvent.press(screen.getByRole("tab", { name: /Jobs/ }));
    expect(await screen.findByText("Jobs per day")).toBeTruthy();
    expect(screen.getAllByText("Done").length).toBeGreaterThan(0);

    await fireEvent.press(screen.getByRole("tab", { name: /Settings/ }));
    expect(await screen.findByText("eric.morel@example.com")).toBeTruthy();
    // Roles are words, never the platform's codes.
    expect(screen.getByText("Member")).toBeTruthy();
    expect(screen.queryByText(/ROLE_/)).toBeNull();
    await fireEvent.press(screen.getByText("Sign out"));
    expect(await screen.findByText("Sign in to your account")).toBeTruthy();
    expect(mockKeychain.size).toBe(0);
  });

  it("says the edge is unreachable instead of showing an empty catalogue", async () => {
    mockKeychain.set("orazaka.session", JSON.stringify(login));
    answerFetch({});
    await app();
    expect(await screen.findByText("The platform cannot be reached.")).toBeTruthy();
  });
});
