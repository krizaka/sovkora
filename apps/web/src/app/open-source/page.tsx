import type { Metadata } from "next";
import { OpenSourcePage } from "@/features/landing/components/OpenSourcePage";
import { loadListedRepos } from "@/features/landing/utils/repositories";
import { translations } from "@/core/context/translations";

const m = translations.en.openSource.meta;

export const metadata: Metadata = {
  title: m.title,
  description: m.description,
  alternates: { canonical: "/open-source" },
};

/** `/open-source` — the repositories (read from GitHub at build, refreshed hourly), the licence line, self-hosting and how to contribute. */
export default async function Page() {
  return <OpenSourcePage repos={await loadListedRepos()} />;
}
