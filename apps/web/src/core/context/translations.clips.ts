/**
 * @file translations.clips.ts
 * @description The words of the demo clips (en, fr): what each shows for assistive technology, its one-line caption under
 * the clip, and its captions (`<track>`, served by /captions/<clip>/<lang>). Each line says what the recording shows.
 */
import type { ClipId } from "@/core/clips/clips";

export interface ClipCopy {
  alt: string;
  caption: string;
  cues: readonly string[];
}
export type ClipsDictionary = Record<ClipId, ClipCopy>;

export const clips = {
  en: {
    take: {
      alt: "The {brand} chat: a question typed, the request routed, the answer written.",
      caption: "Eric asks a question; the answer is written on this machine.",
      cues: ["Eric types a question.", "{brand} routes the request.", "The answer is written on this machine."],
    },
    workspace: {
      alt: "The workspace home: jobs, success rate, time to a result.",
      caption: "Eric's workspace: what ran, and how fast.",
      cues: ["Eric's workspace.", "Jobs, success rate and time to a result, over 14 days."],
    },
    studio: {
      alt: "The Studios catalogue, then the Image generation Studio with its estimated cost.",
      caption: "A Studio shows its price before it runs.",
      cues: ["The Studios catalogue.", "A Studio opens with its estimated cost, before any run."],
    },
    results: {
      alt: "The jobs gallery, then one result opened with its run id.",
      caption: "Every result keeps its run.",
      cues: ["Every result is kept under Jobs.", "One result opened: its steps and its run id."],
    },
    "phone-studio": {
      alt: "The {brand} phone app: a sentence typed, the run in progress, the generated image with its seed and model.",
      caption: "From your phone: a sentence in, an image out.",
      cues: [
        "Mira describes the picture she needs.",
        "The run is followed live.",
        "The result, with its seed and model.",
      ],
    },
    "phone-ask": {
      alt: "The {brand} phone app chat: a question typed, the answer streamed in.",
      caption: "The same chat as on the web, in your hand.",
      cues: ["Mira asks a question.", "The answer streams in."],
    },
  },
  fr: {
    take: {
      alt: "Le chat de {brand} : une question tapée, la requête routée, la réponse écrite.",
      caption: "Eric pose une question ; la réponse s'écrit sur cette machine.",
      cues: ["Eric tape une question.", "{brand} route la requête.", "La réponse s'écrit sur cette machine."],
    },
    workspace: {
      alt: "L'accueil de l'espace de travail : tâches, taux de réussite, temps jusqu'au résultat.",
      caption: "L'espace d'Eric : ce qui a tourné, et à quelle vitesse.",
      cues: ["L'espace de travail d'Eric.", "Tâches, taux de réussite et temps jusqu'au résultat, sur 14 jours."],
    },
    studio: {
      alt: "Le catalogue des Studios, puis le Studio de génération d'image avec son coût estimé.",
      caption: "Un Studio annonce son prix avant de tourner.",
      cues: ["Le catalogue des Studios.", "Un Studio s'ouvre avec son coût estimé, avant tout run."],
    },
    results: {
      alt: "La galerie des tâches, puis un résultat ouvert avec son identifiant de run.",
      caption: "Chaque résultat garde son run.",
      cues: ["Chaque résultat est conservé dans Tâches.", "Un résultat ouvert : ses étapes et son identifiant de run."],
    },
    "phone-studio": {
      alt: "L'app mobile de {brand} : une phrase tapée, le run en cours, l'image générée avec sa graine et son modèle.",
      caption: "Depuis votre téléphone : une phrase, une image.",
      cues: [
        "Mira décrit l'image dont elle a besoin.",
        "Le run se suit en direct.",
        "Le résultat, avec sa graine et son modèle.",
      ],
    },
    "phone-ask": {
      alt: "Le chat de l'app mobile de {brand} : une question tapée, la réponse qui arrive en continu.",
      caption: "Le même chat que sur le web, dans votre main.",
      cues: ["Mira pose une question.", "La réponse arrive en continu."],
    },
  },
} as const satisfies Record<"en" | "fr", ClipsDictionary>;
