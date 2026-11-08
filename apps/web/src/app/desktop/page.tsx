import type { Metadata } from "next";
import { DesktopPage } from "@/features/landing/components/DesktopPage";
import { readStoreLinks } from "@/core/config/store-links";
import { readDownloads } from "@/core/config/downloads";
import { loadDesktopRelease } from "@/features/landing/utils/release";
import { translations } from "@/core/context/translations";

const m = translations.en.desktop.meta;

export const metadata: Metadata = {
  title: m.title,
  description: m.description,
  alternates: { canonical: "/desktop" },
};

/** `/desktop` — Sovkora for Mac. The download is read from the latest `desktop-*` GitHub release (refreshed hourly); the phone apps' store buttons appear when `APP_STORE_IOS_URL` / `APP_STORE_ANDROID_URL` are set at build. */
export const dynamic = "force-dynamic";

export default async function Page() {
  const { macEnabled } = await readDownloads();
  const release = macEnabled ? await loadDesktopRelease() : null;
  return <DesktopPage release={release} stores={readStoreLinks()} downloadEnabled={macEnabled} />;
}
