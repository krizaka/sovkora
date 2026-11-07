import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { LandingNav } from "@/features/landing/components/LandingNav";
import { LandingFooter } from "@/features/landing/components/LandingFooter";
import { DocArticle } from "@/features/docs/components/DocArticle";
import { DocBody } from "@/features/docs/components/DocBody";
import {
  editUrl,
  listDocs,
  navItems,
  neighbours,
  prepareBody,
  readDoc,
  sourcesMap,
  splitSegments,
  tocOf,
} from "@/features/docs/utils/docs";

export const dynamicParams = false;

export function generateStaticParams() {
  // `mobile` is its own route (the four-step tour); every other page is a synced markdown file.
  return listDocs().map((d) => ({ slug: d.slug }));
}

export async function generateMetadata({ params }: Readonly<{ params: Promise<{ slug: string }> }>): Promise<Metadata> {
  const doc = readDoc((await params).slug);
  return doc ? { title: `${doc.entry.title} — Sovkora docs`, description: doc.entry.intro } : {};
}

/** `/docs/<slug>` — one page of the documentation, synced from the Sovkora repository. */
export default async function DocPage({ params }: Readonly<{ params: Promise<{ slug: string }> }>) {
  const doc = readDoc((await params).slug);
  if (!doc) notFound();
  const { entry } = doc;
  const segments = splitSegments(prepareBody(doc.body, entry.source, sourcesMap()));
  // The page opens with its own `# title`: the template's h1 says it, so the first heading is not repeated.
  const [first, ...rest] = segments;
  if (first?.kind === "markdown") first.text = first.text.replace(/^\s*# .*\n+/, "");
  const { previous, next } = neighbours(entry.slug);
  return (
    <>
      <LandingNav onHome={false} />
      <main id="main" className="min-h-screen bg-surface-0 text-fg">
        <DocArticle
          slug={entry.slug}
          journey={entry.journey}
          title={entry.title}
          intro={entry.intro}
          audience={entry.audience}
          generated={entry.generated}
          clip={entry.clip}
          editUrl={editUrl(entry)}
          toc={tocOf([first, ...rest])}
          nav={navItems()}
          previous={previous && { slug: previous.slug, title: previous.title }}
          next={next && { slug: next.slug, title: next.title }}
        >
          <DocBody segments={[first, ...rest].filter(Boolean)} />
        </DocArticle>
      </main>
      <LandingFooter />
    </>
  );
}
