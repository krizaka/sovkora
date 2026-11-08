/**
 * @file settings.constants.test.ts
 * @description Tests for settings constants — exhaustive label maps and type-safe arrays.
 */

import {
  AI_PERSONAS,
  THEME_ACCENTS,
  THEME_LAYOUTS,
  AI_PERSONA_LABELS,
  THEME_ACCENT_LABELS,
  THEME_LAYOUT_LABELS,
  type AiPersona,
  type ThemeAccent,
  type ThemeLayout,
} from "@/constants/settings.constants";

describe("Settings Constants", () => {
  describe("AI_PERSONAS", () => {
    test("has exactly 3 values", () => {
      expect(AI_PERSONAS).toHaveLength(3);
    });

    test("contains expected values", () => {
      expect(AI_PERSONAS).toContain("standard");
      expect(AI_PERSONAS).toContain("concise");
      expect(AI_PERSONAS).toContain("creative");
    });

    test("AI_PERSONA_LABELS covers all personas", () => {
      for (const persona of AI_PERSONAS) {
        expect(AI_PERSONA_LABELS[persona as AiPersona]).toBeDefined();
        expect(AI_PERSONA_LABELS[persona as AiPersona]).toMatch(/^settings\./);
      }
    });
  });

  describe("THEME_ACCENTS", () => {
    test("has exactly 6 values", () => {
      expect(THEME_ACCENTS).toHaveLength(6);
    });

    test("contains expected values", () => {
      expect(THEME_ACCENTS).toContain("rose");
      expect(THEME_ACCENTS).toContain("emerald");
      expect(THEME_ACCENTS).toContain("amber");
      expect(THEME_ACCENTS).toContain("zinc");
      expect(THEME_ACCENTS).toContain("indigo");
      expect(THEME_ACCENTS).toContain("violet");
    });

    test("THEME_ACCENT_LABELS covers all accents", () => {
      for (const accent of THEME_ACCENTS) {
        expect(THEME_ACCENT_LABELS[accent as ThemeAccent]).toBeDefined();
        expect(THEME_ACCENT_LABELS[accent as ThemeAccent]).toMatch(/^settings\./);
      }
    });
  });

  describe("THEME_LAYOUTS", () => {
    test("has exactly 2 values", () => {
      expect(THEME_LAYOUTS).toHaveLength(2);
    });

    test("contains standard and compact", () => {
      expect(THEME_LAYOUTS).toContain("standard");
      expect(THEME_LAYOUTS).toContain("compact");
    });

    test("THEME_LAYOUT_LABELS covers all layouts", () => {
      for (const layout of THEME_LAYOUTS) {
        expect(THEME_LAYOUT_LABELS[layout as ThemeLayout]).toBeDefined();
        expect(THEME_LAYOUT_LABELS[layout as ThemeLayout]).toMatch(/^settings\./);
      }
    });
  });
});
