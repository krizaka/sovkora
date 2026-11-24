import type { MascotBird } from "@krizaka/ui/mascots";
import type { ProductPageData, ProductScene } from "@krizaka/ui/product-page";
import { z } from "zod";
import data from "@/../content/product-page.json";
import { CLIPS, clipFiles, isClipId, type Look } from "@/core/clips/clips";
import { sectionMedia } from "@/core/clips/media-registry";
import { productPageWords, type ProductPageWords } from "@/core/context/translations.productPage";
import type { Locale } from "@/core/context/translations.types";

const BUDGET_KEYS = ["hero.title", "hero.lead", "scene.title", "scene.line", "limit", "closing.title"] as const;
type BudgetKey = (typeof BUDGET_KEYS)[number];

/** The birds of the family that can stand beside the limits (a client list cannot be read on the server). */
const BIRDS = ["owl", "falcon", "duck", "flamingo", "pigeon"] as const satisfies readonly MascotBird[];

const template = z.object({
  /** The registry page whose scenes the template shows. */
  page: z.string(),
  /** The registry role of a scene. */
  sceneRole: z.string(),
  /** The limits band's lines, by id (their words are in `translations.productPage.ts`). */
  limits: z.array(z.string()).min(1).max(3),
  /** The most words each section may say. */
  budget: z.record(z.enum(BUDGET_KEYS), z.number().int().positive()),
  /** The product's bird, drawn beside the limits. */
  bird: z.enum(BIRDS),
});
export type ProductPageTemplate = z.infer<typeof template>;

export const parseTemplate = (raw: unknown): ProductPageTemplate => template.parse(raw);

/** The page's structure, parsed once: `content/product-page.json`. */
export const PRODUCT_PAGE: ProductPageTemplate = parseTemplate(data);

const wordCount = (text: string) => text.split(/\s+/).filter(Boolean).length;

/** The sections whose words go over their budget, named `hero.title`, `scene.phone-studio.line`, `limit.hosted`. */
export function overBudget(words: ProductPageWords, budget: ProductPageTemplate["budget"]): string[] {
  const over = (key: BudgetKey, text: string) => (budget[key] ?? Infinity) < wordCount(text);
  return [
    ...(over("hero.title", words.hero.title) ? ["hero.title"] : []),
    ...(over("hero.lead", words.hero.lead) ? ["hero.lead"] : []),
    ...Object.entries(words.scenes).flatMap(([id, s]) => [
      ...(over("scene.title", s.title) ? [`scene.${id}.title`] : []),
      ...(over("scene.line", s.line) ? [`scene.${id}.line`] : []),
    ]),
    ...Object.entries(words.limits.items).flatMap(([id, text]) => (over("limit", text) ? [`limit.${id}`] : [])),
    ...(over("closing.title", words.closing.title) ? ["closing.title"] : []),
  ];
}

/**
 * The scenes of the page in a language and a look: the registry's recordings of the scene role (only those that may be shown, in
 * its order), each with its words; a recording with no words is left out. Every scene is a phone recording today.
 */
export function productScenes(
  locale: Locale,
  look: Look,
  words: Readonly<Record<string, { title: string; line?: string } | undefined>>,
  page: ProductPageTemplate = PRODUCT_PAGE,
): ProductScene[] {
  return sectionMedia(page.page, page.sceneRole).flatMap((id) => {
    const said = isClipId(id) ? words[id] : undefined;
    if (!said) {
      return [];
    }
    const files = clipFiles(id, locale, look);
    return [
      {
        ...said,
        id,
        device: "phone" as const,
        poster: files.poster,
        sources: { webm: files.webm, mp4: files.mp4 },
        width: CLIPS[id].width,
        height: CLIPS[id].height,
      },
    ];
  });
}

/** The addresses of the page's two ways in. */
export interface ProductPageLinks {
  docs: string;
  features: string;
}

/**
 * Everything the public home shows, as the template's data (krizaka-internal#4635, #4692): the words come from
 * `translations.productPage.ts`, the media from the registry by role — nothing is typed in a component, and no product name.
 */
export function homePageData(locale: Locale, look: Look, links: ProductPageLinks): ProductPageData {
  const w = productPageWords(locale);
  const scenes = productScenes(locale, look, w.scenes);
  return {
    brand: "sovkora",
    hero: {
      title: w.hero.title,
      lead: w.hero.lead,
      primary: { href: links.docs, label: w.hero.primary },
      secondary: { href: links.features, label: w.hero.secondary },
      ...(scenes[0] && { backdrop: scenes[0].poster }),
    },
    scenes: { items: scenes, labels: { play: w.media.play, pause: w.media.pause }, id: "what" },
    limits: {
      title: w.limits.title,
      items: PRODUCT_PAGE.limits.flatMap((id) => w.limits.items[id as keyof typeof w.limits.items] ?? []),
      bird: PRODUCT_PAGE.bird,
    },
    closing: { title: w.closing.title, action: { href: links.docs, label: w.hero.primary } },
  };
}
