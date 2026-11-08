import fs from "node:fs";
import path from "node:path";
import { cookies } from "next/headers";
import { LandingPage } from "@/features/landing/components/LandingPage";
import { DashboardHome } from "@/app/_home/DashboardHome";
import filmRecords from "@/features/landing/utils/film.records.json";
import { filmFacts } from "@/features/landing/utils/film-records";
import { measuredRuns } from "@/features/landing/utils/measured-runs";

/** The next-auth session cookies (development and `__Secure-` production names) — the same pair the proxy reads. */
const SESSION_COOKIES = ["next-auth.session-token", "__Secure-next-auth.session-token"];

/**
 * `/` — the public home page for a visitor, the workspace dashboard for a signed-in user. Decided on
 * the server from the session cookie, so a visitor never sees a dashboard skeleton flash before the
 * landing page, and a user never sees the landing page flash before their dashboard. A stale cookie
 * lands on the dashboard, whose session check sends it to /login.
 */
export default async function HomePage() {
  const jar = await cookies();
  const signedIn = SESSION_COOKIES.some((name) => jar.has(name));
  if (signedIn) {
    return <DashboardHome />;
  }
  // The proof band is counted from the published « Measured runs » page, at build — never typed.
  const measured = measuredRuns(
    fs.readFileSync(path.join(process.cwd(), "content", "docs", "measured-runs.md"), "utf8"),
  );
  // The film's run facts are summed from its records at build; an unreadable file shows no figure.
  const film = filmFacts(filmRecords);
  return <LandingPage measured={measured} film={film} />;
}
