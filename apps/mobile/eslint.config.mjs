import { defineConfig, globalIgnores } from "eslint/config";
import tseslint from "typescript-eslint";

/**
 * React Native / Expo lint profile. Mirrors the Orazaka invariants that apply to a
 * non-web TS app: strict typing, banned enums, banned redundant prefixes, and the
 * 250-line component cap. The web-only rules (Tailwind color classes, lucide imports,
 * inline-style objects, <div> soup, next/image) are intentionally omitted — RN uses
 * StyleSheet/style props and <View>/<Text>, not the DOM.
 */
const eslintConfig = defineConfig([
  ...tseslint.configs.recommended,
  {
    files: ["**/*.{ts,tsx}"],
    rules: {
      // --- Strict typing ---
      "@typescript-eslint/no-explicit-any": "error",
      "@typescript-eslint/no-unused-vars": ["error", { argsIgnorePattern: "^_" }],

      "no-restricted-syntax": [
        "error",
        // Orazaka Invariant: banning traditional TS enums (tree-shakable const unions instead)
        {
          selector: "TSEnumDeclaration",
          message:
            "Traditional TypeScript enums are strictly banned in Orazaka. Use 'export const MyValues = [...] as const' paired with 'export type MyType = typeof MyValues[number]'.",
        },
        // Naming Invariant [ERR-104]: banning redundant Orazaka prefixes on internal types
        {
          selector:
            "ExportNamedDeclaration > :matches(TSInterfaceDeclaration, TSTypeAliasDeclaration, ClassDeclaration, FunctionDeclaration)[id.name=/^Orazaka/]",
          message:
            "[ERR-104] The 'Orazaka' prefix is banned on internal types — the module path already establishes ownership. Use clean domain names.",
        },
      ],
    },
  },
  {
    // Components stay scannable — max 250 lines per .tsx (AGENTS §8).
    files: ["**/*.tsx"],
    rules: {
      "max-lines": ["error", { max: 250, skipBlankLines: false, skipComments: false }],
    },
  },
  {
    // Tooling read by Node as CommonJS (Metro, Expo config plugins, Jest): `require` is how they load.
    files: ["metro.config.js", "jest.config.js", "plugins/**/*.js"],
    rules: { "@typescript-eslint/no-require-imports": "off" },
  },
  globalIgnores([".expo/**", "dist/**", "node_modules/**", "babel.config.js", "ios/**", "android/**"]),
]);

export default eslintConfig;
