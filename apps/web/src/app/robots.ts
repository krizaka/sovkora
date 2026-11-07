import type { MetadataRoute } from "next";
import { CRAWLERS, PRIVATE_PATHS, SITE_URL } from "@/app/_seo/seo";
import { indexable } from "@/core/config/indexing";

// Read on request: the environment decides, not the build.
export const dynamic = "force-dynamic";

export default function robots(): MetadataRoute.Robots {
  // Not indexed (dev, preview): closed, and no sitemap to advertise.
  if (!indexable()) return { rules: [{ userAgent: "*", disallow: "/" }] };
  return {
    rules: [
      { userAgent: "*", allow: "/", disallow: PRIVATE_PATHS },
      ...CRAWLERS.map((userAgent) => ({ userAgent, allow: "/", disallow: PRIVATE_PATHS })),
    ],
    sitemap: `${SITE_URL}/sitemap.xml`,
    host: SITE_URL,
  };
}
