/**
 * @file signupOpen.ts
 * @description Whether the app may offer « Créer un compte ». Sign-up is closed everywhere, so the answer is the server's
 * capability `flags.signupOpen` (`GET /api/v1/app-config`, `@krizaka/app-config`): only an explicit `true` shows the entry.
 * Absent, false, not loaded yet or unreachable all mean hidden. The server still refuses a registration while closed;
 * this only keeps the app from promising what it cannot give.
 */
import { AppState, Platform } from "react-native";
import { createAppConfigClient, type AppConfigClient } from "@krizaka/app-config/client";
import { appStateResume, useAppConfig } from "@krizaka/app-config/react";
import type { AppConfigPayload, Platform as ClientPlatform } from "@krizaka/app-config";
import { APP_VERSION, discovery } from "./discovery";

export const SIGNUP_FLAG = "signupOpen";

/** Only `true` opens it; a missing config, a missing flag, `false` or any other value keeps it hidden. */
export function signupOpenFrom(config: Pick<AppConfigPayload, "flags"> | null): boolean {
  return config?.flags?.[SIGNUP_FLAG] === true;
}

const platform: ClientPlatform = Platform.OS === "ios" ? "ios" : "android";

let client: AppConfigClient | undefined;
/** One client for the app, made on first use, following the edge address discovery found. */
function appConfigClient(): AppConfigClient {
  client ??= createAppConfigClient({
    baseUrl: () => discovery.getService("orazaka").apiBase,
    platform,
    version: APP_VERSION,
  });
  return client;
}

/** Forget the made client, so a test starts from a fresh fetch. */
export function resetAppConfigClient(): void {
  client = undefined;
}

const resume = appStateResume(AppState);

/** True only when the server says sign-up is open. */
export function useSignupOpen(): boolean {
  return signupOpenFrom(useAppConfig(appConfigClient(), resume).config);
}
