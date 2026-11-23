"use client";

import Link from "next/link";
import { ProductPage, PublicFooter } from "@krizaka/ui/product-page";
import { familyLabels } from "@krizaka/ui/sites";
import { DevelopmentStatus } from "@/components/DevelopmentStatus";
import { useLook } from "@/core/clips/useLook";
import { useTranslation } from "@/core/context/LocaleContext";
import { LandingNav } from "@/features/landing/components/LandingNav";
import { homePageData } from "@/features/landing/utils/product-page";
import { publicFooter } from "@/features/landing/utils/public-footer";
import "@krizaka/ui/product-page.css";

/**
 * The public home on the product-page template of @krizaka/ui (krizaka-internal#4635, #4692): the hero, the recordings of the app
 * as scenes, what it does not do, one way in. The page holds no words and no product name: `homePageData` builds the template's data
 * from the words, the media registry (by role) and the page's structure. The development status, the company's word, closes the
 * page above the footer, which is the export's own data (`content/public-footer.json`).
 */
export function ProductHome() {
  const { t, locale } = useTranslation();
  const look = useLook();
  const data = homePageData(locale, look, { docs: "/docs", features: "#what" });
  return (
    <>
      <LandingNav />
      <main id="main" className="bg-surface-0 text-fg">
        <ProductPage data={data} />
        <DevelopmentStatus className="mx-auto px-4 py-8 text-center" />
      </main>
      <PublicFooter
        current="sovkora"
        labels={familyLabels[locale]}
        config={publicFooter(locale, t.landing.footer)}
        linkAs={Link}
      />
    </>
  );
}
