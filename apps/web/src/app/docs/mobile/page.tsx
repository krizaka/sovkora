import type { Metadata } from "next";
import { LandingNav } from "@/features/landing/components/LandingNav";
import { LandingFooter } from "@/features/landing/components/LandingFooter";
import { MobileTour } from "@/features/docs/components/MobileTour";
import { mobile } from "@/core/context/translations.mobile";
import { findPage, navItems, neighbours } from "@/features/docs/utils/docs";

export const metadata: Metadata = { title: mobile.en.meta, description: mobile.en.metaDescription };

/** `/docs/mobile` — the four steps of the Sovkora app, the first page of the Mobile section of the documentation. */
export default function MobileDocPage() {
  const entry = findPage("mobile")!;
  const { previous, next } = neighbours("mobile");
  return (
    <>
      <LandingNav onHome={false} />
      <main id="main" className="min-h-screen bg-surface-0 text-fg">
        <MobileTour
          entry={entry}
          nav={navItems()}
          previous={previous && { slug: previous.slug, title: previous.title }}
          next={next && { slug: next.slug, title: next.title }}
        />
      </main>
      <LandingFooter />
    </>
  );
}
