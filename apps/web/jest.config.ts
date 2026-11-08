import type { Config } from "jest";
import nextJest from "next/jest.js";
import { createRequire } from "node:module";
import fs from "node:fs";
import path from "node:path";

// @krizaka/ui as this app resolves it (it may be nested under the app while the workspace root holds the version
// the design system asked for).
// Jest runs from the app directory (npm scripts, the workspace reactor).
const resolveFromApp = createRequire(path.join(process.cwd(), "package.json")).resolve;
const ui = path.dirname(resolveFromApp("@krizaka/ui/package.json"));
const icons = path.dirname(resolveFromApp("@krizaka/icons/package.json"));
const charts = path.dirname(resolveFromApp("@krizaka/charts/package.json"));
const intl = path.dirname(resolveFromApp("@krizaka/intl/package.json"));
const tokens = path.dirname(resolveFromApp("@krizaka/tokens/package.json"));
/** A package whose `exports` carry the `import` condition only: its folder, looked up from the app and from @krizaka/ui. */
function findPackage(name: string): string {
  for (let dir = ui; dir !== path.dirname(dir); dir = path.dirname(dir)) {
    const candidate = path.join(dir, "node_modules", name);
    if (fs.existsSync(path.join(candidate, "package.json"))) return candidate;
  }
  return path.join(process.cwd(), "node_modules", name);
}
const markdown = findPackage("@tanstack/markdown");
const highlight = findPackage("@tanstack/highlight");
const query = path.dirname(resolveFromApp("@krizaka/query/package.json"));
const pacer = path.dirname(resolveFromApp("@krizaka/pacer/package.json"));
// @krizaka/settings (findTypedPrices, orazaka#417) and its @krizaka/db are ES modules only: mapped to their files here,
// compiled to CommonJS by the transform (both are in `transpilePackages` of next.config.ts, which next/jest reads).
const settings = path.dirname(resolveFromApp("@krizaka/settings/package.json"));
const db = path.dirname(createRequire(path.join(settings, "package.json")).resolve("@krizaka/db/package.json"));

// @krizaka/org (the commercial names and the code-name guard, krizaka-internal#2103): the same `import`-only exports.
const org = path.dirname(resolveFromApp("@krizaka/org/package.json"));

const createJestConfig = nextJest({
  dir: "./",
});

const config = {
  testEnvironment: "jsdom",
  setupFiles: ["<rootDir>/tests/setup.ts"],
  moduleNameMapper: {
    "^@/(.*)$": "<rootDir>/src/$1",
    "^@krizaka/auth$": "<rootDir>/tests/auth-stub.ts",
    // @krizaka/ui (re-exported by @krizaka/orazaka-design-system 2.x) exposes its entries under the `import`
    // condition only, which Jest's CommonJS resolver does not read: map them to their files.
    "^@krizaka/org$": `${org}/dist/index.js`,
    "^@krizaka/ui$": `${ui}/dist/index.js`,
    "^@krizaka/ui/(.*)$": `${ui}/dist/$1.js`,
    // @krizaka/charts: the same `import`-only exports.
    "^@krizaka/charts/(.*)$": `${charts}/dist/$1.js`,
    // @krizaka/icons: the same `import`-only exports.
    "^@krizaka/icons$": `${icons}/dist/index.js`,
    // @krizaka/intl: the same `import`-only exports.
    "^@krizaka/intl$": `${intl}/dist/index.js`,
    "^@krizaka/intl/(.*)$": `${intl}/dist/$1.js`,
    // @krizaka/tokens (a dependency of @krizaka/ui 3.x's chunks): the same `import`-only exports.
    "^@krizaka/tokens/(.*)$": `${tokens}/dist/$1.js`,
    // @krizaka/query (the nav bar of @krizaka/ui 3.55 reads it): the same `import`-only exports.
    "^@krizaka/query$": `${query}/dist/index.js`,
    "^@krizaka/query/(.*)$": `${query}/dist/$1.js`,
    // @krizaka/pacer (a dependency of @krizaka/ui's DataTable): the same `import`-only exports.
    "^@krizaka/pacer/(.*)$": `${pacer}/dist/$1.js`,
    "^@krizaka/settings$": `${settings}/dist/index.js`,
    "^@krizaka/db$": `${db}/dist/index.js`,
    // @tanstack/markdown and @tanstack/highlight (under @krizaka/ui's Markdown and CodeBlock): the same `import`-only exports.
    "^@tanstack/markdown/(.*)$": `${markdown}/dist/$1.js`,
    "^@tanstack/highlight/(.*)$": `${highlight}/dist/$1.js`,
  },
  testPathIgnorePatterns: [
    "<rootDir>/node_modules/",
    "<rootDir>/.next/",
    "<rootDir>/src/__tests__/helpers/",
    "<rootDir>/e2e/",
    "<rootDir>/scripts/",
    "<rootDir>/src/console/test/",
  ],
  collectCoverageFrom: ["src/**/*.{ts,tsx}", "!src/**/*.d.ts", "!src/app/layout.tsx", "!src/app/page.tsx"],
} satisfies Config;

export default createJestConfig(config);
