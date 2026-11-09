/**
 * @file studios.ts
 * @description The words of the Studios console page and its usage table, en and fr (krizaka-internal#2093).
 */
import { localise, w, type Localised } from "@/core/context/words";

const WORDS = {
  page: {
    title: w("Studios", "Studios"),
    lead: w(
      "A Studio is data: the workflow, its steps and its forms are written here and published without a deploy.",
      "Un Studio est une donnée : le workflow, ses étapes et ses formulaires s’écrivent ici et se publient sans déploiement.",
    ),
    usage: w("Usage", "Utilisation"),
    builder: w("Editor", "Éditeur"),
  },
  analytics: {
    loading: w("Loading…", "Chargement…"),
    failed: w("Could not load", "Chargement impossible"),
    caption: w("Studio usage", "Usage des Studios"),
    reading: w("Reading the usage", "Lecture de l'usage"),
    empty: w("No Studio measured.", "Aucun Studio mesuré."),
    studio: w("Studio", "Studio"),
    installations: w("Installations", "Installations"),
    runs: w("Runs", "Exécutions"),
    failures: w("failed", "échecs"),
    successRate: w("Success", "Réussite"),
    credits: w("Credits / run", "Crédits / exéc."),
    unused: w("installed, never run", "installé, jamais lancé"),
    unusedHint: w(
      "Installed then never run: the clearest churn signal of the product",
      "Installé puis jamais lancé — le signal de churn le plus net du produit",
    ),
  },
  builder: {
    loading: w("Loading the catalogue…", "Chargement du catalogue…"),
    empty: w("No Studio in the catalogue.", "Aucun Studio au catalogue."),
    versionCol: w("Version", "Version"),
    status: w("Status", "Statut"),
    creditsCol: w("Credits", "Crédits"),
    note: w("Note", "Note"),
    versions: w("Versions", "Versions"),
    loadingVersions: w("Reading versions", "Lecture des versions"),
    emptyVersions: w("No version for this Studio.", "Aucune version pour ce Studio."),
    publish: w("Publish", "Publier"),
    deprecate: w("Deprecate", "Déprécier"),
    deprecateTitle: w(
      "The version stays executable for installations that pinned it",
      "La version reste exécutable pour les installations qui l'ont épinglée",
    ),
    version: w("Version (semver)", "Version (semver)"),
    credits: w("Est. credits / run", "Crédits estimés / exéc."),
    changelog: w("Version note", "Note de version"),
    changelogPlaceholder: w("What changes for the user", "Ce qui change pour l'utilisateur"),
    capabilities: w(
      "Available capabilities ({n}) — a CAPABILITY step must name one",
      "Capacités disponibles ({n}) — un step CAPABILITY doit en nommer une",
    ),
    definition: w("Definition (steps + outputs)", "Définition (steps + outputs)"),
    inputSchema: w("Input schema (run form)", "Schéma des entrées (formulaire d'exécution)"),
    configSchema: w("Config schema (install dialog)", "Schéma de configuration (dialogue d'installation)"),
    saveDraft: w("Save draft", "Enregistrer le brouillon"),
    draftSaved: w("Draft {version} saved and validated.", "Brouillon {version} enregistré et validé."),
    versionPublished: w("Version {version} published.", "Version {version} publiée."),
    failed: w("Failed", "Échec"),
  },
} as const;

export type StudiosWords = Localised<typeof WORDS>;
export type StudiosLocale = "en" | "fr";
export const studiosCopy: Record<StudiosLocale, StudiosWords> = { en: localise(WORDS, 0), fr: localise(WORDS, 1) };
