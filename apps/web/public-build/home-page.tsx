import fs from "node:fs";
import path from "node:path";
import { ProductLanding } from "@/features/landing/components/ProductLanding";
import filmRecords from "@/features/landing/utils/film.records.json";
import { filmFacts } from "@/features/landing/utils/film-records";
import { measuredRuns } from "@/features/landing/utils/measured-runs";

/**
 * `/fr` and `/en` of the public export: the landing page of the dev app's `/`, without the dashboard branch (that one reads the
 * session cookie, which a static page cannot).
 */
export default function HomePage() {
  const measured = measuredRuns(
    fs.readFileSync(path.join(process.cwd(), "content", "docs", "measured-runs.md"), "utf8"),
  );
  return <ProductLanding measured={measured} film={filmFacts(filmRecords)} quickstartHref={null} />;
}
