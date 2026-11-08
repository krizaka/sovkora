/**
 * @file discovery.ts
 * @description Where the Sovkora edge lives, asked of the signed manifest on krizaka.com instead of being baked in the
 * build (krizaka/.github ADR-003, `@krizaka/discovery`): a URL change is an edit of the manifest and a krizaka.com
 * deploy, never a store release. The order is verified live document → cache (Keychain) → the baked default below →
 * the address hard-coded before discovery (`DEV_EDGE_URL`).
 *
 * Build-time inputs, for local work only (an EAS profile sets neither):
 *   EXPO_PUBLIC_EDGE_URL  an edge the device can reach (`http://192.168.1.5:8088`, `http://10.0.2.2:8088`): wins over discovery
 *   EXPO_PUBLIC_APP_ENV   `dev` (default) | `prod`: which entry of the manifest this build follows
 */
import Constants from "expo-constants";
import * as SecureStore from "expo-secure-store";
import {
  chunkedStorage,
  createDiscovery,
  type DiscoveryOptions,
  KRIZAKA_HOST_SUFFIXES,
  KRIZAKA_MANIFEST_URLS,
  KRIZAKA_PUBLIC_KEYS,
} from "@krizaka/discovery";
import { DEV_EDGE_URL, setEdgeBase, trimSlashes } from "./api";

export const APP_VERSION = Constants.expoConfig?.version ?? "0.0.0";

/** The website (a cookie session): where the app's universal links come from. Not the API. */
export const DEV_WEB_URL = "https://www.orazaka.com";

/** Baked in the build: used until a verified manifest says otherwise (the manifest's own values at build time). */
export const BAKED_ADDRESSES = { apiBase: DEV_EDGE_URL, webBase: DEV_WEB_URL } as const;
/** What the app hard-coded before discovery existed: the last resort. */
export const LEGACY_ADDRESSES = { apiBase: DEV_EDGE_URL, webBase: DEV_WEB_URL } as const;

/** Where an update is while the app is distributed through TestFlight and EAS (the stores' pages replace these at launch). */
export const UPDATE_URL = {
  ios: "itms-beta://",
  android: "https://expo.dev/accounts/oussamaabid/projects/orazaka-mobile/builds",
} as const;

export function edgeOverrideFrom(raw: string | undefined): string | undefined {
  const value = raw?.trim();
  if (!value) return undefined;
  let url: URL;
  try {
    url = new URL(value);
  } catch {
    throw new Error("EXPO_PUBLIC_EDGE_URL must be an absolute http(s) URL");
  }
  if (url.protocol !== "https:" && url.protocol !== "http:")
    throw new Error("EXPO_PUBLIC_EDGE_URL must be an absolute http(s) URL");
  return trimSlashes(value);
}

export function appEnvironmentFrom(raw: string | undefined): "dev" | "prod" {
  const value = raw?.trim() || "dev";
  if (value !== "dev" && value !== "prod") throw new Error("EXPO_PUBLIC_APP_ENV must be dev or prod");
  return value;
}

/** Builds the app's discovery client; tests pass their own fetch, storage and keys. */
export function createAppDiscovery(options: Partial<DiscoveryOptions> = {}) {
  const override = edgeOverrideFrom(process.env.EXPO_PUBLIC_EDGE_URL);
  return createDiscovery({
    environment: appEnvironmentFrom(process.env.EXPO_PUBLIC_APP_ENV),
    appVersion: APP_VERSION,
    manifestUrls: KRIZAKA_MANIFEST_URLS,
    publicKeys: KRIZAKA_PUBLIC_KEYS,
    allowedHostSuffixes: [...KRIZAKA_HOST_SUFFIXES, "orazaka.com"],
    defaults: { orazaka: BAKED_ADDRESSES },
    legacy: { orazaka: LEGACY_ADDRESSES },
    ...(override ? { overrides: { orazaka: { apiBase: override, webBase: DEV_WEB_URL } } } : {}),
    // The Keychain / Keystore, in pieces under their per-value limit. The document is signed: what is read back is verified again.
    storage: chunkedStorage({
      getItem: (key) => SecureStore.getItemAsync(key),
      setItem: (key, value) => SecureStore.setItemAsync(key, value),
      removeItem: (key) => SecureStore.deleteItemAsync(key),
    }),
    ...options,
  });
}

export const discovery = createAppDiscovery();

// Every request reads the edge from discovery at the moment it is made.
setEdgeBase(() => discovery.getService("orazaka").apiBase);

/** The Sovkora website, as of now (the manifest's `webBase`). */
export const orazakaWebUrl = (): string => discovery.getService("orazaka").webBase;
