/**
 * @file translations.showcase.ts
 * @description The words of the moments a mascot speaks (empty shelves, long runs, results, errors) and of the
 * Studios, packs, jobs and notifications surfaces added with them (en, fr).
 */

import type { ShowcaseDictionary } from "./translations.types";

export const showcase: Record<"en" | "fr", ShowcaseDictionary> = {
  en: {
    studio: {
      loadErrorTitle: "This page did not load",
      runRunningBody:
        "Every step runs on your machine. You can leave this page: the run carries on and the bell tells you when it is done.",
      runSucceededBody: "Everything it produced is below — copy it, download it, use it.",
      runFailedBody: "The steps below show where it stopped.",
      stepsProgress: "{done} of {total} steps",
      download: "Download",
      included: "Included in {pack}",
    },
    jobs: {
      emptyTitle: "Nothing has run yet",
      emptyBody:
        "Generate an image, a video or a voice-over from the chat, or run a Studio: every job lands here, live.",
      emptyCta: "Open a Studio",
      runningTitle: "{count} running on your machine",
      runningBody: "You can keep working: the bell tells you when each one is done.",
      expired: "File expired",
      expiredBody: "The file was not kept by this environment. The prompt is in the details.",
      resultOf: "Result of {studio}",
      result: "Result",
      copyId: "Copy the ID",
      rawJson: "Raw JSON",
      close: "Close",
    },
    media: {
      play: "Play",
      pause: "Pause",
      local: "Generated on this machine — nothing left it",
    },
    dashboard: {
      studios: "Open a Studio",
      studiosDesc: "Document checks, product visuals, voice-overs, prospecting — ready-made workflows.",
      creations: "Your creations",
      creationsDesc: "Every image, video and voice-over generated on your machine.",
      latestCreations: "Latest creations",
      seeAll: "See all",
    },
    chat: {
      approvePlan: "Approve and run the plan",
      adjustPlan: "Ask for changes",
      thinking: "Thinking…",
      reasoning: "Reasoning",
    },
    notifications: {
      emptyTitle: "All quiet",
      emptyBody: "When an image, a video or a Studio run finishes, it shows up here.",
      running: "Running",
      done: "Ready",
      failed: "Failed",
    },
  },
  fr: {
    studio: {
      loadErrorTitle: "Cette page n'a pas pu se charger",
      runRunningBody:
        "Chaque étape tourne sur votre machine. Vous pouvez quitter cette page : le run continue et la cloche vous prévient quand il est terminé.",
      runSucceededBody: "Tout ce qu'il a produit est ci-dessous — copiez-le, téléchargez-le, utilisez-le.",
      runFailedBody: "Les étapes ci-dessous montrent où il s'est arrêté.",
      stepsProgress: "{done} étape(s) sur {total}",
      download: "Télécharger",
      included: "Inclus dans {pack}",
    },
    jobs: {
      emptyTitle: "Rien n'a encore tourné",
      emptyBody:
        "Générez une image, une vidéo ou une voix off depuis le chat, ou lancez un Studio : chaque tâche arrive ici, en direct.",
      emptyCta: "Ouvrir un Studio",
      runningTitle: "{count} en cours sur votre machine",
      runningBody: "Vous pouvez continuer à travailler : la cloche vous prévient quand chacune est terminée.",
      expired: "Fichier expiré",
      expiredBody: "Le fichier n'a pas été conservé par cet environnement. Le prompt est dans les détails.",
      resultOf: "Résultat de {studio}",
      result: "Résultat",
      copyId: "Copier l'identifiant",
      rawJson: "JSON brut",
      close: "Fermer",
    },
    media: {
      play: "Lire",
      pause: "Pause",
      local: "Généré sur cette machine — rien n'en est sorti",
    },
    dashboard: {
      studios: "Ouvrir un Studio",
      studiosDesc:
        "Vérification de documents, visuels produit, voix off, prospection — des workflows prêts à l'emploi.",
      creations: "Vos créations",
      creationsDesc: "Chaque image, vidéo et voix off générée sur votre machine.",
      latestCreations: "Dernières créations",
      seeAll: "Tout voir",
    },
    chat: {
      approvePlan: "Approuver et lancer le plan",
      adjustPlan: "Demander des modifications",
      thinking: "Réflexion…",
      reasoning: "Raisonnement",
    },
    notifications: {
      emptyTitle: "Tout est calme",
      emptyBody: "Quand une image, une vidéo ou un run de Studio se termine, il apparaît ici.",
      running: "En cours",
      done: "Prêt",
      failed: "Échec",
    },
  },
};
