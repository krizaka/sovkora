/**
 * @file model-compare.ts
 * @description "Compare models": one prompt per inspiration, the same for every model, each image an Sovkora run of the image-generation
 * Studio on www.orazaka.com. The LIST of images is the media manifest (`sites/orazaka/compare/<inspiration>--<model>.webp`) — nothing
 * here lists a file; `model-compare.results.json` (a table, no logic) holds what each run recorded (run id, time, size), keyed by the file. A published image without its record,
 * or one whose inspiration or model is unknown, fails the build. Words: `t.gallery`.
 */
import { mediaPaths, mediaUrl } from "@/core/media/cdn";
import results from "./model-compare.results.json";

/** Where a model ran: honest labels, never "local" for a hosted model. */
export type CompareWhere = "mac" | "digitalocean";

export interface CompareModel {
  /** The catalogue key (`orazaka models image use <key>` / the Studio's `model` input). */
  id: string;
  /** The model's part of a file name (no dot: `stable-diffusion-3-5-large`). */
  slug: string;
  name: string;
  where: CompareWhere;
  licence: string;
  /** What the provider charges for an image, in US dollars; absent for a model on the visitor's own machine. */
  costUsd?: number;
  /** Whether the same seed makes the same image again (the hosted model's provider ignores it). */
  seedHonoured: boolean;
}

/** In display order: the local ones, then the hosted one. */
export const COMPARE_MODELS: readonly CompareModel[] = [
  {
    id: "flux-2-klein-4b",
    slug: "flux-2-klein-4b",
    name: "FLUX.2 [klein] 4B",
    where: "mac",
    licence: "Apache-2.0",
    seedHonoured: true,
  },
  {
    id: "v1-5-pruned-emaonly",
    slug: "v1-5-pruned-emaonly",
    name: "Stable Diffusion 1.5",
    where: "mac",
    licence: "CreativeML OpenRAIL-M",
    seedHonoured: true,
  },
  {
    id: "stable-diffusion-3.5-large",
    slug: "stable-diffusion-3-5-large",
    name: "Stable Diffusion 3.5 Large",
    where: "digitalocean",
    licence: "Stability AI Community Licence",
    costUsd: 0.08,
    seedHonoured: false,
  },
];

export interface CompareInspiration {
  /** The file prefix and the key of its title in `t.gallery.compareTitles`. */
  id: string;
  /** Exactly what reached every model. */
  prompt: string;
}

export const COMPARE_INSPIRATIONS: readonly CompareInspiration[] = [
  {
    id: "montreal-winter",
    prompt:
      "A quiet street of the Plateau in Montréal on a winter night, outside spiral staircases, warm lit windows, snow falling through the streetlight, steam from a street vent. Cinematic photograph, 35mm lens, shallow depth of field, rich colour grade. No text, no lettering, no logos.",
  },
  {
    id: "moroccan-riad",
    prompt:
      "A Moroccan riad courtyard in the late afternoon: a zellige tile fountain, orange trees, carved plaster arches, shafts of warm light. Editorial architecture photograph, 24mm lens, soft shadows, natural textures. No text, no lettering, no logos.",
  },
  {
    id: "alps-dawn",
    prompt:
      "The Alps at dawn above a valley in mist: snow-capped peaks glowing pink, a cable car gliding over the pine forest, first light. Aerial landscape photograph, telephoto lens, crisp detail, cinematic colour grade. No text, no lettering, no logos.",
  },
  {
    id: "neon-arcade",
    prompt:
      "A 1990s arcade at night, rows of glowing cabinets, a player's hands on a joystick and buttons seen from behind, neon magenta and cyan, haze. Cinematic photograph, 35mm lens, shallow depth of field. No text, no lettering, no logos, no characters on the screens.",
  },
  {
    id: "server-room",
    prompt:
      "A quiet sovereign server room at night in Montréal: a single rack glowing with warm orange light, snow falling beyond a tall window, polished concrete floor reflections, volumetric haze. Cinematic photograph, 35mm anamorphic lens, ultra-detailed. No text, no lettering, no logos.",
  },
  {
    id: "notary-desk",
    prompt:
      "A Québec notary's walnut desk at blue hour: a stack of contracts, a fountain pen, a steaming cup of coffee, snow falling outside the window, a line of warm lamp light across the pages. Editorial still-life photograph, 85mm lens, shallow depth of field. No legible text.",
  },
];

/** What the run of one image recorded. */
export interface CompareRecord {
  /** The Sovkora run (Studio run) that made it. */
  runId: string;
  /** What the run took, in seconds, from the command to the file. */
  seconds: number;
  width: number;
  height: number;
  seed: string;
}

/** What each run recorded, by the media path of its file (no extension). */
const RESULTS: Record<string, CompareRecord> = results;

/** One published image of the comparison. */
export interface CompareImage extends CompareRecord {
  inspiration: CompareInspiration;
  model: CompareModel;
  stem: string;
  src: string;
}

const COMPARE_FILE = /^sites\/orazaka\/compare\/([a-z0-9-]+)--([a-z0-9-]+)\.webp$/;

function discover(): CompareImage[] {
  const found = mediaPaths("sites/orazaka/compare/")
    .map((path) => COMPARE_FILE.exec(path))
    .filter((match): match is RegExpExecArray => match !== null)
    .map((match) => {
      const stem = match[0].slice(0, -".webp".length);
      const inspiration = COMPARE_INSPIRATIONS.find((item) => item.id === match[1]);
      const model = COMPARE_MODELS.find((item) => item.slug === match[2]);
      if (!inspiration || !model) throw new Error(`${match[0]} names an unknown inspiration or model`);
      const record = RESULTS[stem];
      if (!record) throw new Error(`${match[0]} is published but no run recorded it (model-compare RESULTS)`);
      return { ...record, inspiration, model, stem, src: mediaUrl(match[0]) };
    });
  for (const stem of Object.keys(RESULTS))
    if (!found.some((image) => image.stem === stem)) throw new Error(`${stem} has a record but is not published`);
  return found;
}

export const COMPARE_IMAGES: readonly CompareImage[] = discover();

/** The images of one inspiration, in the order of `COMPARE_MODELS`. */
export const imagesOf = (inspirationId: string): CompareImage[] =>
  COMPARE_IMAGES.filter((image) => image.inspiration.id === inspirationId).sort(
    (a, b) => COMPARE_MODELS.indexOf(a.model) - COMPARE_MODELS.indexOf(b.model),
  );
