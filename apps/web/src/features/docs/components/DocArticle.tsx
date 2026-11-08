"use client";

import type { ReactNode } from "react";
import Link from "next/link";
import { DocsArticlePage } from "@krizaka/ui/docs-article-page";
import { Button } from "@krizaka/ui/button";
import { Clip } from "@/core/clips/Clip";
import type { ClipId } from "@/core/clips/clips";
import { useTranslation } from "@/core/context/LocaleContext";
import "@/features/docs/docs.css";
import { DocsSidebar } from "@/features/docs/components/DocsSidebar";
import { pageHref, type JourneyId, type NavItem } from "@/features/docs/utils/docs-shared";

export interface DocArticleProps {
  slug: string;
  journey: JourneyId;
  title: string;
  intro: string;
  audience: string;
  generated: boolean;
  editUrl: string | null;
  toc: { id: string; label: string }[];
  nav: NavItem[];
  previous: { slug: string; title: string } | null;
  next: { slug: string; title: string } | null;
  /** The page's own words, translated (the mobile tour): they replace the title and the lead. */
  words?: { title: string; lead: string };
  /** A recording shown under the lead, in place of a paragraph. */
  clip?: ClipId | null;
  children: ReactNode;
}

/** A documentation article on @krizaka/ui's template: sidebar by journey · breadcrumb, title, lead, facts · the reading column · contents; previous and next. */
export function DocArticle({
  slug,
  journey,
  title,
  intro,
  audience,
  generated,
  editUrl,
  toc,
  nav,
  previous,
  next,
  words,
  clip,
  children,
}: Readonly<DocArticleProps>) {
  const { t, locale } = useTranslation();
  const d = t.docs;
  const first = nav.find((n) => n.journey === journey);
  const facts = [
    { label: d.audience[audience] ?? audience },
    ...(generated ? [{ label: d.generated, tone: "ok" as const }] : []),
    ...(locale === "fr" && d.englishOnly && !words ? [{ label: d.englishOnly }] : []),
  ];
  return (
    <DocsArticlePage
      linkAs={Link}
      sidebar={<DocsSidebar items={nav} current={slug} />}
      sidebarLabel={d.sidebarLabel}
      breadcrumbLabel={d.breadcrumbLabel}
      breadcrumb={[
        { href: "/docs", label: d.home },
        { href: first ? pageHref(first.slug) : "/docs", label: d.journeys[journey].title },
      ]}
      title={words?.title ?? title}
      lead={words?.lead ?? intro}
      facts={facts}
      toc={toc.length > 1 ? { label: d.onThisPage, items: toc } : undefined}
      pager={{
        previous: previous ? { href: pageHref(previous.slug), title: previous.title } : undefined,
        next: next ? { href: pageHref(next.slug), title: next.title } : undefined,
        labels: { previous: d.previous, next: d.next },
      }}
    >
      {clip && <Clip id={clip} className="mb-8 max-w-xl" />}
      {children}
      {editUrl && (
        <p className="mt-10 border-t border-border-subtle pt-4 text-sm">
          <Button asChild variant="ghost" size="sm">
            <a href={editUrl} target="_blank" rel="noopener noreferrer">
              {generated ? d.viewSource : d.editOnGitHub}
            </a>
          </Button>
        </p>
      )}
    </DocsArticlePage>
  );
}
