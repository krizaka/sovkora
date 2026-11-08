/** @jest-environment node */
import fs from "node:fs";
import { createRequire } from "node:module";
import path from "node:path";

const read = (p: string) => fs.readFileSync(path.join(process.cwd(), p), "utf8");
const require = createRequire(path.join(process.cwd(), "package.json"));

// One page change, one system: Krizaka UI's PageTransition wraps the page content once, in the provider tree. Its
// cross-fade, and the instant change under prefers-reduced-motion, live in @krizaka/ui's motion.css (tested there).
describe("page transitions", () => {
  const providers = read("src/core/providers/Providers.tsx");

  it("wraps the page content in PageTransition from @krizaka/ui/motion, once", () => {
    expect(providers).toMatch(/import \{ PageTransition \} from "@krizaka\/ui\/motion"/);
    expect(providers.match(/<PageTransition>/g)).toHaveLength(1);
    expect(providers).toMatch(/<PageTransition>\{children\}<\/PageTransition>/);
  });

  it("ships the stylesheet that carries the transition and its reduced-motion rule", () => {
    const motion = fs.readFileSync(
      path.join(path.dirname(require.resolve("@krizaka/ui/motion")), "motion.css"),
      "utf8",
    );
    expect(motion).toContain("::view-transition-old(.kz-page)");
    expect(motion).toMatch(/prefers-reduced-motion[\s\S]*::view-transition-old\(\*\)/);
  });
});
