import type { MetadataRoute } from "next";
import { publicPaths, SITE_URL } from "@/app/_seo/seo";
import { indexable } from "@/core/config/indexing";

export const dynamic = "force-dynamic";

/** The public pages, dated by the build. The language is chosen in the page (one URL), so there is no hreflang to declare. */
export default function sitemap(): MetadataRoute.Sitemap {
  if (!indexable()) return [];
  const lastModified = new Date();
  return publicPaths().map((path) => ({
    url: `${SITE_URL}${path === "/" ? "" : path}`,
    lastModified,
    changeFrequency: path.startsWith("/docs") ? "weekly" : "monthly",
    priority: path === "/" ? 1 : path.startsWith("/features") || path === "/docs" ? 0.8 : 0.5,
  }));
}
