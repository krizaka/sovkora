import type { Metadata } from "next";
import { headers } from "next/headers";
import { pickLocale } from "@/console/core/copy/closed";
import { EngineScreen } from "@/console/features/engine/components/EngineScreen";

export const metadata: Metadata = { title: "Engine · Sovkora Operations" };

/** `/engine` — the engine's runtime settings, grouped, edited in place; a change applies on the next read. */
export default async function EnginePage() {
  const locale = pickLocale((await headers()).get("accept-language"));
  return (
    <main className="mx-auto w-full max-w-7xl px-4 py-8 sm:px-6">
      <EngineScreen locale={locale} />
    </main>
  );
}
