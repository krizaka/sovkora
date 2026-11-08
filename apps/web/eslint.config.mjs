import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";
import { krizakaUiRestrictedSyntax } from "@krizaka/config/eslint/krizaka-ui";
import { krizakaStack } from "@krizaka/config/eslint/stack";

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  // The stack rules, the React-effects rules and cognitive complexity (15): warnings while krizaka-ratchet freezes the debt.
  ...krizakaStack({ effects: "warn", complexity: "warn", sonar: "warn", codeRules: "off" }),
  {
    files: ["**/*.{js,jsx,mjs,ts,tsx,mts,cts}"],
    rules: {
      // --- SECURITY AND STRICT TYPING RULES ---
      "@typescript-eslint/no-explicit-any": "error",
      "@typescript-eslint/no-unused-vars": ["error", { argsIgnorePattern: "^_" }],
      "react-hooks/exhaustive-deps": "error",
      "@next/next/no-img-element": "error",

      // --- PRODUCTION HYPER-GOVERNANCE ---
      "no-console": ["error", { allow: ["warn", "error"] }], // Disallow raw debug logs in production pipelines
      "no-debugger": "error", // Block left-over development debugging breakpoints

      "no-restricted-syntax": [
        "error",
        // 0. The four Krizaka UI rules (@krizaka/config), strict: no raw palette colour, no `light:`, no arbitrary
        //    `[var(--…)]` utility, no template string in className. krizaka-ratchet holds the same four at zero.
        ...krizakaUiRestrictedSyntax(),
        // 1. Orazaka Invariant: Banning traditional TS Enums
        {
          selector: "TSEnumDeclaration",
          message:
            "Traditional TypeScript enums are strictly banned in Orazaka. Use 'export const MyValues = [...] as const' paired with 'export type MyType = typeof MyValues[number]' to maintain pure, tree-shakable, structural Type-Safe compliance.",
        },

        // 2. Krizaka UI Invariant: Banning generic deep HTML structures (Div Soup)
        {
          selector:
            "JSXElement[openingElement.name.name='div'] > JSXElement[openingElement.name.name='div'] > JSXElement[openingElement.name.name='div']",
          message:
            "Deeply nested generic <div> structures are banned in Orazaka. Use semantic HTML5 elements (<main>, <section>, <article>, <header>, <footer>) to ensure structural accessibility.",
        },

        // 3. Temporal Invariant: Forbidding mutation of native Date instances
        {
          selector:
            "CallExpression[callee.type='MemberExpression'][callee.property.name=/^set(Date|Hours|Minutes|Seconds|Milliseconds|Month|FullYear|UTCDate|UTCHours|UTCMinutes|UTCSeconds|UTCMilliseconds|UTCMonth|UTCFullYear|Time)$/]",
          message:
            "Mutating native JavaScript Date instances via set* methods is strictly forbidden in Orazaka. Use date-fns instead to preserve immutability.",
        },

        // 4. Naming Invariant [ERR-104]: Forbidding redundant prefixes
        {
          selector:
            "ExportNamedDeclaration > :matches(TSInterfaceDeclaration, TSTypeAliasDeclaration, ClassDeclaration, FunctionDeclaration)[id.name=/^Orazaka/]",
          message:
            "[ERR-104] Orazaka prefix is banned on internal types. The module path already establishes ownership. Use clean domain names (e.g., 'Settings' not 'OrazakaSettings').",
        },

        // 5. Tailwind v4 / Style Invariant: Forbidding inline style objects
        {
          selector: "JSXAttribute[name.name='style']",
          message:
            "Inline style objects are strictly forbidden in Orazaka. You must leverage Tailwind v4 CSS utility tokens or custom rules within layout classes inside globals.css to ensure Design System consistency.",
        },

        // 6. Next.js Async / Hydration Invariant: Forbidding raw fetch calls inside useEffect
        {
          selector: "CallExpression[callee.name='useEffect'] CallExpression[callee.property.name='fetch']",
          message:
            "Data fetching inside useEffect hooks is forbidden in Orazaka. Use Next.js Server Components, Server Actions, or dedicated cache-aware SWR/TanStack Query pipelines to manage state hydration cleanly.",
        },

        // 7. Design-Token Invariant: Forbidding hardcoded Tailwind palette color classes
        {
          selector:
            "Literal[value=/(bg|text|border|ring|from|to|via|fill|stroke|divide|outline|decoration|caret|placeholder|accent|shadow)-(zinc|gray|slate|neutral|stone|red|rose|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink)-[0-9]{2,3}/]",
          message:
            "Hardcoded Tailwind palette color classes are banned in Orazaka — they break theme switching and dark mode. Use the role utilities of @krizaka/tailwind: text-fg / text-fg-secondary / text-fg-muted, bg-surface-{0..3}, border-border-{subtle,default,strong}, {text,bg,border}-{success,warning,danger,info}, *-accent, text-on-accent (opacity modifiers like /10 are supported).",
        },
        {
          selector:
            "TemplateElement[value.cooked=/(bg|text|border|ring|from|to|via|fill|stroke|divide|outline|decoration|caret|placeholder|accent|shadow)-(zinc|gray|slate|neutral|stone|red|rose|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink)-[0-9]{2,3}/]",
          message:
            "Hardcoded Tailwind palette color classes are banned in Orazaka — they break theme switching and dark mode. Use the role utilities of @krizaka/tailwind: text-fg / text-fg-secondary / text-fg-muted, bg-surface-{0..3}, border-border-{subtle,default,strong}, {text,bg,border}-{success,warning,danger,info}, *-accent, text-on-accent (opacity modifiers like /10 are supported).",
        },
      ],

      // --- IMPORTS AND ARCHITECTURAL BOUNDARIES ---
      "no-restricted-imports": [
        "error",
        {
          patterns: [
            {
              group: ["../*", "../**"],
              message:
                "Relative imports from parent directories are strictly banned in Orazaka. You must leverage the absolute path alias '@/*' (pointing to './src/*') to maintain clean module boundaries and refactoring flexibility.",
            },
            {
              group: ["next/font/google", "next/font/google/*"],
              message:
                "Google Fonts are banned in Orazaka: next/font/google fetches the faces from the network at build. The typefaces are the files of @krizaka/fonts, loaded with next/font/local (see the root layout).",
            },
            {
              group: ["lucide-react", "lucide-react/*"],
              message:
                "Direct icon-library imports are banned in Orazaka. Use the centralized polymorphic <Icon name=\"...\" /> registry exported from '@krizaka/orazaka-design-system'; add a new entry to the registry (orazaka-design-system/src/icon.tsx) if an icon is missing.",
            },
          ],
        },
      ],
    },
  },
  {
    // Krizaka UI Invariant: components stay scannable — max 250 lines per .tsx
    // (AGENTS §8). Extract sub-components / hooks / types / constants past the cap.
    files: ["**/*.tsx"],
    rules: {
      "max-lines": ["error", { max: 250, skipBlankLines: false, skipComments: false }],
    },
  },
  {
    // The console's node:test suites (npm run test:console) import the modules they test by relative path, with their
    // `.ts` extension, the way node resolves them.
    files: ["src/console/test/**/*.mts"],
    rules: { "no-restricted-imports": "off" },
  },
  {
    // The UI crawl (a node script run by hand and by the e2e workflow, outside the app) shares the QA-name reader with
    // the recording scripts, which live in scripts/: it is the only file that reaches over from e2e/ to scripts/.
    files: ["e2e/ui-crawl.mjs"],
    rules: { "no-restricted-imports": "off" },
  },
  // Align globally ignored build directories
  globalIgnores([
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
    "coverage/**",
    "node_modules/**",
    // Identical copy of krizaka/.github scripts/check-app-links.mjs (a CLI that prints its findings): not edited here.
    "scripts/check-app-links.mjs",
  ]),
]);

export default eslintConfig;
