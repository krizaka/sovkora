import "@testing-library/jest-dom";
import { render, screen } from "@testing-library/react";
import { readFileSync } from "node:fs";
import { join } from "node:path";

import { SiteFooter } from "@/console/components/SiteFooter";

/* The operator console ends with @krizaka/ui's SiteFooter (compact app mode), one line: the publisher, the version. */
describe("the console's footer", () => {
  it("is one contentinfo: the family footer's slim app line, with the publisher and the version", () => {
    render(<SiteFooter />);
    expect(screen.getAllByRole("contentinfo")).toHaveLength(1);
    expect(screen.getByRole("link", { name: /A Krizaka product/ })).toHaveAttribute("href", "https://www.krizaka.com");
    expect(screen.getByTestId("app-version")).toHaveTextContent(/dev|v\d/);
  });

  it("is not hand-made any more (one footer everywhere, krizaka-com AGENTS.md)", () => {
    const src = readFileSync(join(__dirname, "..", "SiteFooter.tsx"), "utf8");
    expect(src).toContain("@krizaka/ui/site-footer");
    expect(src).not.toMatch(/<footer\b/);
  });
});
