import React from "react";
import { fireEvent, render, screen } from "@testing-library/react-native";
import { ThemeProvider } from "@krizaka/ui/native";
import { brands } from "@krizaka/tokens/native";
import { AppNavigator } from "../navigation/AppNavigator";
import { AuthProvider } from "../core/AuthContext";
import { LocaleProvider } from "../i18n";
import { answerFetch } from "./test-api";
import login from "../__fixtures__/api/auth-login.local-demo.json";
import studios from "../__fixtures__/api/studios.local-demo.json";
import jobs from "../__fixtures__/api/jobs.local-demo.json";
import profile from "../__fixtures__/api/profile.local-demo.json";

const mockKeychain = new Map<string, string>();
jest.mock("expo-secure-store", () => ({
  getItemAsync: async (key: string) => mockKeychain.get(key) ?? null,
  setItemAsync: async (key: string, value: string) => void mockKeychain.set(key, value),
  deleteItemAsync: async (key: string) => void mockKeychain.delete(key),
}));
// A phone whose language is French (Canada).
jest.mock("../i18n/deviceTags", () => ({ deviceLanguageTags: () => ["fr-CA", "en-CA"] }));

const app = () =>
  render(
    <ThemeProvider defaultMode="light" overrides={brands.orazaka}>
      <LocaleProvider>
        <AuthProvider>
          <AppNavigator />
        </AuthProvider>
      </LocaleProvider>
    </ThemeProvider>,
  );

beforeEach(() => mockKeychain.clear());

describe("on a French phone, in light mode", () => {
  it("speaks French everywhere — tabs, sign-in, settings — and asks the platform for French", async () => {
    mockKeychain.set("orazaka.session", JSON.stringify(login));
    const calls = answerFetch({ "/api/v1/studios": studios, "/api/v1/jobs": jobs, "/api/v1/profile": profile });
    await app();
    expect(await screen.findByText("Vitrine Artisan")).toBeTruthy();
    expect(calls.find((c) => c.path.startsWith("/api/v1/studios"))?.path).toBe("/api/v1/studios?locale=fr");

    // Four tabs, in the phone's language, never truncated to a code.
    for (const name of ["Studios", "Chat", "Tâches", "Paramètres"])
      expect(screen.getByRole("tab", { name })).toBeTruthy();
    expect(screen.getAllByRole("tab")).toHaveLength(4);
    expect(screen.getByText("Verrouillé")).toBeTruthy();

    await fireEvent.press(screen.getByRole("tab", { name: "Paramètres" }));
    expect(await screen.findByText("eric.morel@example.com")).toBeTruthy();
    expect(screen.getByText("Membre")).toBeTruthy();
    expect(screen.queryByText(/ROLE_/)).toBeNull();
    expect(screen.getByText("Connecteurs")).toBeTruthy();
    expect(screen.getByText("Se déconnecter")).toBeTruthy();

    await fireEvent.press(screen.getByText("Se déconnecter"));
    expect(await screen.findByText("Connectez-vous à votre compte")).toBeTruthy();
  });
});
