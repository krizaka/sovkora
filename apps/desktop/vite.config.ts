import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  // @krizaka/orazaka-design-system@3.4.0 (its latest) still imports the retired brand file; the Sovkora brand of @krizaka/tokens replaces it.
  resolve: { alias: { "@krizaka/tokens/brands/orazaka.css": "@krizaka/tokens/brands/sovkora.css" } },
  clearScreen: false,
  server: { port: 1420, strictPort: true },
  build: { target: "safari16", outDir: "dist", emptyOutDir: true },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/__tests__/setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
  },
});
