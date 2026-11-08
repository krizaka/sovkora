/**
 * @file translations.runProgress.ts
 * @description The words of the run experience (orazaka-web-client#85, #86): the phase of a running step, the time left
 * (only when it rests on measured runs), leaving the page, cancelling, and deleting a result — en, fr.
 */

import type { Locale } from "./translations.types";
import type { RunProgressDictionary } from "./translations.runProgress.types";

export const runProgress: Record<Locale, RunProgressDictionary> = {
  en: {
    phases: {
      queued: "Waiting for a machine",
      loading: "Loading the model",
      generating: "Generating",
      encoding: "Encoding the file",
      uploading: "Saving the result",
    },
    stepOf: "Step {step} of {steps}",
    elapsed: "{time} so far",
    overdue: "Taking longer than usual — it typically takes {time}",
    basis: "Median of your last {n} similar runs",
    noEstimate: "No estimate yet: too few similar runs have finished",
    cancelRun: "Cancel the run",
    cancelConfirm: "Stop it for real?",
    cancelling: "Stopping…",
    cancelFailed: "The run could not be stopped. Try again in a moment.",
    cancelled: "{name} was stopped. Nothing was billed.",
    inProgress: {
      title: "Your runs in progress",
      empty: "Nothing is running.",
      open: "Follow",
      since: "Started {time}",
    },
    deleteDialog: {
      title: "Delete {name}?",
      description: "The file is removed from storage for good. You have a few seconds to undo.",
      confirm: "Delete",
      cancel: "Keep it",
      done: "{name} deleted",
      undo: "Undo",
      failed: "{name} could not be deleted: {reason}",
      running: "Stop the task before deleting it.",
    },
  },
  fr: {
    phases: {
      queued: "En attente d'une machine",
      loading: "Chargement du modèle",
      generating: "Génération",
      encoding: "Encodage du fichier",
      uploading: "Enregistrement du résultat",
    },
    stepOf: "Étape {step} sur {steps}",
    elapsed: "{time} écoulées",
    overdue: "Plus long que d'habitude — cela prend en général {time}",
    basis: "Médiane de vos {n} derniers essais semblables",
    noEstimate: "Pas encore d'estimation : trop peu d'essais semblables terminés",
    cancelRun: "Annuler l'exécution",
    cancelConfirm: "L'arrêter pour de bon ?",
    cancelling: "Arrêt en cours…",
    cancelFailed: "L'exécution n'a pas pu être arrêtée. Réessayez dans un instant.",
    cancelled: "{name} a été arrêtée. Rien n'a été facturé.",
    inProgress: {
      title: "Vos exécutions en cours",
      empty: "Rien ne tourne.",
      open: "Suivre",
      since: "Démarrée {time}",
    },
    deleteDialog: {
      title: "Supprimer {name} ?",
      description: "Le fichier est retiré du stockage pour de bon. Vous avez quelques secondes pour annuler.",
      confirm: "Supprimer",
      cancel: "Le garder",
      done: "{name} supprimé",
      undo: "Annuler",
      failed: "{name} n'a pas pu être supprimé : {reason}",
      running: "Arrêtez la tâche avant de la supprimer.",
    },
  },
};
