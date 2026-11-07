import fs from "node:fs";
import path from "node:path";
import { APP_SECTIONS } from "@/core/config/app-sections";

/** The signed-in app's addresses say the section's name (docs/ux/information-architecture.md); the old ones answer 308. */
const RENAMED = [
  { from: "/dashboard/jobs", to: "/jobs", section: "jobs" },
  { from: "/billing", to: "/usage", section: "usage" },
  { from: "/profile", to: "/settings", section: "settings" },
] as const;

describe("the app's addresses", () => {
  it.each(RENAMED)("$from answers 308 towards $to, and keeps its query", async ({ from, to }) => {
    const { default: nextConfig } = (await import(path.join(process.cwd(), "next.config.ts"))) as {
      default: { redirects: () => Promise<{ source: string; destination: string; permanent: boolean }[]> };
    };
    const redirects = await nextConfig.redirects();
    const rule = redirects.find((r) => r.source === from);
    expect(rule).toMatchObject({ destination: to, permanent: true });
  });

  it("the old /billing address is redirected on the product host only: /billing is a section of the console (admin host)", async () => {
    const { default: nextConfig } = (await import(path.join(process.cwd(), "next.config.ts"))) as {
      default: { redirects: () => Promise<{ source: string; has?: { type: string; value?: string }[] }[]> };
    };
    const rule = (await nextConfig.redirects()).find((r) => r.source === "/billing");
    const host = rule?.has?.find((h) => h.type === "host");
    expect(host?.value).toBeDefined();
    expect(new RegExp(`^${host?.value}$`).test("admin.orazaka.com")).toBe(false);
    expect(new RegExp(`^${host?.value}$`).test("dev.orazaka.com")).toBe(true);
  });

  it.each(RENAMED)("the rail's « $section » is $to, a page that exists", ({ to, section }) => {
    expect(APP_SECTIONS.find((s) => s.id === section)?.href).toBe(to);
    const dir = to === "/usage" ? "src/app/(protected)/usage" : `src/app${to}`;
    expect(fs.existsSync(path.join(process.cwd(), dir, "page.tsx"))).toBe(true);
  });

  it("no source file still links an old address", () => {
    const hits: string[] = [];
    const walk = (dir: string) => {
      for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
        const p = path.join(dir, e.name);
        if (e.isDirectory()) {
          // The operator console (src/console, served on admin.orazaka.com) has its own addresses: /billing is its section.
          if (e.name !== "__tests__" && e.name !== "node_modules" && p !== path.join(process.cwd(), "src", "console"))
            walk(p);
        } else if (
          e.name !== "redirects.ts" &&
          /\.(ts|tsx)$/.test(e.name) &&
          /["'`]\/(dashboard\/jobs|billing|profile)\b/.test(fs.readFileSync(p, "utf8"))
        )
          hits.push(p);
      }
    };
    walk(path.join(process.cwd(), "src"));
    expect(hits).toEqual([]);
  });
});
