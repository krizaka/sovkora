/**
 * @file store-links.ts
 * @description The store pages of the mobile apps (bounded context APP_), declared once, the zod way, read at build.
 *
 * `APP_STORE_IOS_URL` (App Store) and `APP_STORE_ANDROID_URL` (Google Play). Unset, empty or not a store page: the
 * Download page says « coming soon » with no button and no link. Set: the button appears by itself. Only the stores'
 * own https pages are accepted — never a TestFlight invitation, an APK or an Expo build, which are not for the public.
 */
import { z } from "zod";

const storeUrl = (hosts: readonly string[]) =>
  z
    .url({ protocol: /^https$/ })
    .refine((u) => URL.canParse(u) && hosts.includes(new URL(u).hostname), "not a page of this store");

const IOS = storeUrl(["apps.apple.com", "itunes.apple.com"]);
const ANDROID = storeUrl(["play.google.com"]);

export interface StoreLinks {
  ios: string | null;
  android: string | null;
}

const valid = (schema: z.ZodType<string>, raw: string | undefined): string | null => {
  const value = (raw ?? "").trim();
  if (!value) return null;
  const parsed = schema.safeParse(value);
  return parsed.success ? parsed.data : null;
};

export function readStoreLinks(vars: Record<string, string | undefined> = process.env): StoreLinks {
  return { ios: valid(IOS, vars.APP_STORE_IOS_URL), android: valid(ANDROID, vars.APP_STORE_ANDROID_URL) };
}
