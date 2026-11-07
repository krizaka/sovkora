/**
 * @file translations.appVideos.ts
 * @description The words of the app videos section on /desktop (`AppVideosSection`, krizaka/.github#315), each line with
 * its English and French side by side. `items` is keyed by `<platform>-<flow>` of the manifest.
 */
export type VideosLocale = "en" | "fr";

export interface VideosWords {
  title: string;
  lead: string;
  play: string;
  recorded: string;
  seconds: string;
  items: Record<string, { name: string; body: string; cues?: readonly string[] }>;
}

const TITLE = { en: "See the app at work", fr: "Voir l'app à l'œuvre" };
const LEAD = {
  en: "Each video is recorded by an automated journey on the dev environment, with a demo persona and demo data only.",
  fr: "Chaque vidéo est enregistrée par un parcours automatisé sur l'environnement de développement, avec un personnage de démonstration et des données de démonstration seulement.",
};
const PLAY = { en: "Play", fr: "Lire" };
const RECORDED = { en: "Recorded {date}", fr: "Enregistrée le {date}" };
const CHAT = {
  en: { name: "The chat", body: "A question asked with a pasted policy, answered from the text on screen." },
  fr: { name: "Le chat", body: "Une question posée avec une politique collée, répondue à partir du texte à l'écran." },
};
const STUDIO = {
  en: { name: "The Studios", body: "The catalogue by trade, then a Studio opened. Nothing is run." },
  fr: { name: "Les Studios", body: "Le catalogue par métier, puis un Studio ouvert. Rien n'est lancé." },
};

const MAC = {
  en: {
    name: "The Mac app",
    body: "The control panel opens, a folder of your Mac is shared, one automation reads a PDF and summarises it. Demo data only.",
    cues: [
      "{brand} for Mac opens on your control panel.",
      "You share one folder of your Mac, and only that one.",
      "An automation is set up on that folder.",
      "One run reads the PDF and the model's summary comes back with its steps.",
    ],
  },
  fr: {
    name: "L'app Mac",
    body: "Le panneau de contrôle s'ouvre, un dossier de votre Mac est partagé, une automatisation lit un PDF et le résume. Données de démonstration seulement.",
    cues: [
      "{brand} pour Mac s'ouvre sur votre panneau de contrôle.",
      "Vous partagez un dossier de votre Mac, et lui seul.",
      "Une automatisation est créée sur ce dossier.",
      "Un passage lit le PDF et le résumé du modèle revient avec ses étapes.",
    ],
  },
};

export function videosWords(locale: VideosLocale): VideosWords {
  return {
    title: TITLE[locale],
    lead: LEAD[locale],
    play: PLAY[locale],
    recorded: RECORDED[locale],
    seconds: "{seconds} s",
    items: { "web-chat": CHAT[locale], "web-studio": STUDIO[locale], "mac-control-panel": MAC[locale] },
  };
}
