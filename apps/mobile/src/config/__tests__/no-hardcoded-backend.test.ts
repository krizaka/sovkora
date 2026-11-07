import { readdirSync, readFileSync } from "fs";
import { join } from "path";

// krizaka/.github#120 (ADR-003): the app never hard-codes where its backend is. The addresses live in
// config/api.ts (the dev edge, the baked default) and config/discovery.ts (the baked website address) only.
const src = join(__dirname, "../..");
const files = (dir: string): string[] =>
  readdirSync(dir, { withFileTypes: true }).flatMap((e) =>
    e.isDirectory() ? files(join(dir, e.name)) : [join(dir, e.name)],
  );

describe("no hard-coded backend address", () => {
  it("keeps the hosts in config/api.ts and config/discovery.ts, and in the linking prefixes (native, tied to app.json)", () => {
    const allowed = ["config/api.ts", "config/discovery.ts", "navigation/linking.ts"];
    const offenders = files(src)
      .filter((f) => /\.(ts|tsx)$/.test(f) && !/__tests__|__fixtures__/.test(f))
      .filter((f) => !allowed.some((a) => f.endsWith(a)))
      .filter((f) => /https?:\/\/(?:dev|www)\.orazaka\.com/.test(readFileSync(f, "utf8")));
    expect(offenders.map((f) => f.replace(src, ""))).toEqual([]);
  });
});
