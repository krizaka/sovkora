/**
 * @file translations.webhooks.ts
 * @description The words of Settings → Webhooks (en, fr).
 */

import type { WebhooksDictionary } from "./translations.webhooks.types";

export const webhooks: Record<"en" | "fr", WebhooksDictionary> = {
  en: {
    title: "Webhooks",
    subtitle:
      "Be told when a run is over instead of polling for it. Each delivery is signed with your endpoint's secret.",
    create: "Add endpoint",
    loading: "Loading endpoints…",
    emptyTitle: "No webhook endpoint yet",
    emptyDesc: "Add an https address and {brand} will call it when a run completes, fails or is cancelled.",
    caption: "Webhook endpoints",
    columns: { url: "Address", events: "Events", created: "Created", actions: "Actions" },
    events: { completed: "Completed", failed: "Failed", cancelled: "Cancelled" },
    urlLabel: "Address",
    urlPlaceholder: "https://example.com/hooks/orazaka",
    urlHelp: "Must be https and reachable from the internet.",
    eventsLabel: "Events",
    createCta: "Add endpoint",
    cancel: "Cancel",
    createError: "Couldn't add the endpoint. Please try again.",
    invalidUrl: "Enter an https address.",
    limitReached: "You've reached the maximum number of endpoints. Delete one to add another.",
    revealTitle: "Copy your signing secret now",
    revealDesc:
      "This is the only time you'll see it. Use it to verify the webhook-signature header (Standard Webhooks, HMAC-SHA256).",
    copy: "Copy",
    copied: "Copied",
    done: "Done",
    verifyHint:
      "Deliveries are retried with a growing delay until your endpoint answers 2xx; use webhook-id to ignore duplicates.",
    actions: { log: "Deliveries", rotate: "Rotate secret", delete: "Delete" },
    rotateConfirm: "Issue a new secret? The current one keeps working for a day so you can switch.",
    deleteConfirm: "Delete this endpoint and its delivery log?",
    rotatedTitle: "Your new signing secret",
    logTitle: "Deliveries",
    logEmpty: "Nothing sent yet.",
    logColumns: { event: "Event", status: "Status", attempts: "Attempts", answer: "Answer", when: "When" },
    status: { PENDING: "Pending", DELIVERED: "Delivered", DEAD: "Gave up" },
    close: "Close",
  },
  fr: {
    title: "Webhooks",
    subtitle:
      "Soyez prévenu quand une exécution se termine au lieu de l'interroger. Chaque envoi est signé avec le secret de votre point de terminaison.",
    create: "Ajouter un point de terminaison",
    loading: "Chargement des points de terminaison…",
    emptyTitle: "Aucun point de terminaison",
    emptyDesc: "Ajoutez une adresse https : {brand} l'appellera quand une exécution se termine, échoue ou est annulée.",
    caption: "Points de terminaison webhook",
    columns: { url: "Adresse", events: "Événements", created: "Créé le", actions: "Actions" },
    events: { completed: "Terminée", failed: "Échouée", cancelled: "Annulée" },
    urlLabel: "Adresse",
    urlPlaceholder: "https://exemple.com/hooks/orazaka",
    urlHelp: "Doit être en https et joignable depuis Internet.",
    eventsLabel: "Événements",
    createCta: "Ajouter",
    cancel: "Annuler",
    createError: "Impossible d'ajouter le point de terminaison. Réessayez.",
    invalidUrl: "Saisissez une adresse https.",
    limitReached:
      "Vous avez atteint le nombre maximum de points de terminaison. Supprimez-en un pour en ajouter un autre.",
    revealTitle: "Copiez votre secret de signature maintenant",
    revealDesc:
      "C'est la seule fois où il sera affiché. Servez-vous-en pour vérifier l'en-tête webhook-signature (Standard Webhooks, HMAC-SHA256).",
    copy: "Copier",
    copied: "Copié",
    done: "Terminé",
    verifyHint:
      "Les envois sont répétés avec un délai croissant jusqu'à ce que votre point réponde 2xx ; utilisez webhook-id pour ignorer les doublons.",
    actions: { log: "Envois", rotate: "Renouveler le secret", delete: "Supprimer" },
    rotateConfirm: "Émettre un nouveau secret ? L'actuel continue de fonctionner un jour pour vous laisser basculer.",
    deleteConfirm: "Supprimer ce point de terminaison et son journal d'envois ?",
    rotatedTitle: "Votre nouveau secret de signature",
    logTitle: "Envois",
    logEmpty: "Rien n'a encore été envoyé.",
    logColumns: { event: "Événement", status: "Statut", attempts: "Essais", answer: "Réponse", when: "Quand" },
    status: { PENDING: "En attente", DELIVERED: "Livré", DEAD: "Abandonné" },
    close: "Fermer",
  },
};
