/**
 * @file translations.docs.ts
 * @description The words around the documentation (hub, sidebar, article chrome, the text version of the schemas), en and
 * fr. The pages' own text is the source docs of the Sovkora repository (English): it is synced, never translated here.
 */
type Locale = "en" | "fr";

import type { GroupId, JourneyId } from "@/core/constants/docs-journeys";

export interface DocsJourneyCopy {
  title: string;
  blurb: string;
  /** What the card's first link says. */
  start: string;
}

export interface DocsDiagramLabels {
  sequence: string;
  pipeline: string;
  states: string;
  architecture: string;
  coreChain: string;
  configuredChain: string;
  participants: string;
  reply: string;
  step: string;
  usedBy: string;
  uses: string;
  calls: string;
  calledBy: string;
  none: string;
  notBuilt: string;
  initial: string;
  final: string;
  terminal: string;
  repository: string;
  role: string;
  owner: string;
  moduleMap: string;
}

/** The chrome of the pack catalogue drawn on /docs/packs (the packs' own words are data, in English like the page). */
export interface DocsPackLabels {
  findLabel: string;
  tier: string;
  category: string;
  all: string;
  found: string;
  none: string;
  studios: string;
  studiosOne: string;
  open: string;
  facts: string;
  tierFact: string;
  regulatoryFact: string;
  workerFact: string;
  rulesets: string;
  jurisdiction: string;
  ruleset: string;
  rules: string;
  versions: string;
  asks: string;
  steps: string;
  credits: string;
  more: string;
  less: string;
  guard: string;
  consent: string;
  safety: string;
  noInputs: string;
}

export interface DocsDictionary {
  meta: string;
  metaDescription: string;
  eyebrow: string;
  title: string;
  lead: string;
  searchLabel: string;
  searchPlaceholder: string;
  searchEmpty: string;
  searchHint: string;
  journeysTitle: string;
  journeys: Record<JourneyId, DocsJourneyCopy>;
  groups: Record<GroupId, string>;
  quickstartTitle: string;
  /** The hub's entry block, right after the quickstart: what a pack is, and the way to the catalogue. */
  packsEntry: { title: string; line: string; cta: string };
  popularTitle: string;
  modelsTitle: string;
  modelsLine: string;
  promiseLabel: string;
  promise: string;
  home: string;
  sidebarLabel: string;
  breadcrumbLabel: string;
  onThisPage: string;
  previous: string;
  next: string;
  editOnGitHub: string;
  viewSource: string;
  generated: string;
  englishOnly: string;
  audience: Record<string, string>;
  copy: string;
  copied: string;
  diagrams: DocsDiagramLabels;
  packs: DocsPackLabels;
}

export const docs: Record<Locale, DocsDictionary> = {
  en: {
    meta: "Documentation — {brand}",
    metaDescription:
      "{brand}'s documentation on a short path: a five-minute quickstart, one page of concepts, short guides, and a generated reference.",
    eyebrow: "Documentation",
    title: "Quickstart, concepts, guides.",
    lead: "Read the first two sections and you can run {brand}. The reference is generated from the code, so it cannot drift from what runs.",
    searchLabel: "Search the documentation",
    searchPlaceholder: "Search: install, packs, API, backup…",
    searchEmpty: "Nothing matches. Try a shorter word, or pick a journey below.",
    searchHint: "Pages and their headings",
    journeysTitle: "Where do you want to go?",
    journeys: {
      start: {
        title: "Start",
        blurb: "Five minutes to a running install, then the whole model on one page.",
        start: "Open the quickstart",
      },
      guides: {
        title: "Guides",
        blurb: "One task each, short: install, run a Studio, decide what is refused, use the phone app.",
        start: "Run a Studio",
      },
      packs: {
        title: "Packs",
        blurb: "The catalogue: what each pack installs, every manifest field, how to write and install one.",
        start: "Open the catalogue",
      },
      reference: {
        title: "Reference",
        blurb: "Generated from the code: the API, the CLI, packs, models, architecture, policies.",
        start: "Open the API reference",
      },
    },
    groups: { integrate: "Integrate", platform: "The platform", policies: "Policies and apps" },
    quickstartTitle: "From zero to a running stack",
    packsEntry: {
      title: "Packs",
      line: "A pack is an installable offer, one per trade: the Studios, models and rules a job needs, in one install.",
      cta: "Browse the packs",
    },
    popularTitle: "Most read",
    modelsTitle: "Supported models",
    modelsLine: "Every model {brand} supports: what it does, where it runs, its licence and context window.",
    promiseLabel: "Our promise",
    promise:
      "Every reference page is generated from the code. If a page is wrong, the code is, or the generator is — and we fix it at the source, never in the page.",
    home: "Docs",
    sidebarLabel: "Pages of the documentation",
    breadcrumbLabel: "Breadcrumb",
    onThisPage: "On this page",
    previous: "Previous",
    next: "Next",
    editOnGitHub: "Edit this page on GitHub",
    viewSource: "View the source on GitHub",
    generated: "Generated from the code",
    englishOnly: "Page text is in English, as in the {brand} repositories.",
    audience: {
      everyone: "For everyone",
      operator: "For operators",
      developer: "For developers",
      "decision-maker": "For decision-makers",
    },
    copy: "Copy",
    copied: "Copied",
    packs: {
      findLabel: "Find a pack",
      tier: "Tier",
      category: "Category",
      all: "All",
      found: "{count} of {total} packs",
      none: "No pack matches these filters.",
      studios: "{count} Studios",
      studiosOne: "1 Studio",
      open: "Open the pack",
      facts: "About this pack",
      tierFact: "Tier",
      regulatoryFact: "Regulatory class",
      workerFact: "Own worker",
      rulesets: "Rulesets",
      jurisdiction: "Jurisdiction",
      ruleset: "Ruleset",
      rules: "Rules",
      versions: "Versions",
      asks: "Asks for",
      steps: "Steps",
      credits: "Credits held",
      more: "More",
      less: "Less",
      guard: "A scope guard refuses anything outside this pack's domain.",
      consent: "Versioned consent is asked before install.",
      safety: "A fixed, reviewed crisis response is built in.",
      noInputs: "No input — it starts from the run.",
    },
    diagrams: {
      sequence: "Sequence — text version",
      pipeline: "Interceptor pipeline — text version",
      states: "States — text version",
      architecture: "Architecture",
      coreChain: "Core chain: always, in the order of the code",
      configuredChain: "Configured chain: in the database order",
      participants: "Participants",
      reply: "replies to",
      step: "Step",
      usedBy: "Used by",
      uses: "Uses",
      calls: "Calls",
      calledBy: "Called by",
      none: "None",
      notBuilt: "not built yet",
      initial: "start",
      final: "end",
      terminal: "Terminal",
      repository: "Repository",
      role: "Role",
      owner: "Owner",
      moduleMap: "The module map is listed in the tables of this page.",
    },
  },
  fr: {
    meta: "Documentation — {brand}",
    metaDescription:
      "La documentation de {brand} sur un chemin court : un démarrage en cinq minutes, une page de concepts, des guides courts et une référence générée.",
    eyebrow: "Documentation",
    title: "Démarrage, concepts, guides.",
    lead: "Lisez les deux premières sections et vous pouvez faire tourner {brand}. La référence est générée depuis le code : elle ne peut pas s'éloigner de ce qui tourne.",
    searchLabel: "Rechercher dans la documentation",
    searchPlaceholder: "Rechercher : installer, packs, API, sauvegarde…",
    searchEmpty: "Aucun résultat. Essayez un mot plus court, ou choisissez un parcours ci-dessous.",
    searchHint: "Pages et leurs titres",
    journeysTitle: "Où voulez-vous aller ?",
    journeys: {
      start: {
        title: "Démarrer",
        blurb: "Cinq minutes pour une installation qui tourne, puis tout le modèle sur une page.",
        start: "Ouvrir le démarrage",
      },
      guides: {
        title: "Guides",
        blurb: "Une tâche chacun, court : installer, lancer un Studio, décider ce qui est refusé, l'app mobile.",
        start: "Lancer un Studio",
      },
      packs: {
        title: "Packs",
        blurb:
          "Le catalogue : ce que chaque pack installe, chaque champ du manifeste, comment en écrire et en installer un.",
        start: "Ouvrir le catalogue",
      },
      reference: {
        title: "Référence",
        blurb: "Générée depuis le code : l'API, la CLI, les packs, les modèles, l'architecture, les politiques.",
        start: "Ouvrir la référence API",
      },
    },
    groups: { integrate: "Intégrer", platform: "La plateforme", policies: "Politiques et apps" },
    quickstartTitle: "De zéro à une pile qui tourne",
    packsEntry: {
      title: "Packs",
      line: "Un pack est une offre installable, une par métier : les Studios, modèles et règles d'un travail, en une installation.",
      cta: "Parcourir les packs",
    },
    popularTitle: "Les plus lues",
    modelsTitle: "Modèles pris en charge",
    modelsLine:
      "Chaque modèle que {brand} prend en charge : ce qu'il fait, où il tourne, sa licence et sa fenêtre de contexte.",
    promiseLabel: "Notre promesse",
    promise:
      "Chaque page de référence est générée depuis le code. Si une page est fausse, le code l'est, ou le générateur — et on corrige à la source, jamais dans la page.",
    home: "Docs",
    sidebarLabel: "Pages de la documentation",
    breadcrumbLabel: "Fil d'Ariane",
    onThisPage: "Sur cette page",
    previous: "Précédent",
    next: "Suivant",
    editOnGitHub: "Modifier cette page sur GitHub",
    viewSource: "Voir la source sur GitHub",
    generated: "Générée depuis le code",
    englishOnly: "Texte des pages en anglais, comme dans les dépôts {brand}.",
    audience: {
      everyone: "Pour tous",
      operator: "Pour les exploitants",
      developer: "Pour les développeurs",
      "decision-maker": "Pour les décideurs",
    },
    copy: "Copier",
    copied: "Copié",
    packs: {
      findLabel: "Trouver un pack",
      tier: "Niveau",
      category: "Catégorie",
      all: "Tous",
      found: "{count} packs sur {total}",
      none: "Aucun pack ne correspond à ces filtres.",
      studios: "{count} Studios",
      studiosOne: "1 Studio",
      open: "Ouvrir le pack",
      facts: "À propos de ce pack",
      tierFact: "Niveau",
      regulatoryFact: "Classe réglementaire",
      workerFact: "Worker dédié",
      rulesets: "Jeux de règles",
      jurisdiction: "Juridiction",
      ruleset: "Jeu de règles",
      rules: "Règles",
      versions: "Versions",
      asks: "Demande",
      steps: "Étapes",
      credits: "Crédits bloqués",
      more: "Plus",
      less: "Moins",
      guard: "Un garde-fou de périmètre refuse tout ce qui sort du domaine de ce pack.",
      consent: "Un consentement versionné est demandé avant l'installation.",
      safety: "Une réponse de crise fixe et relue est intégrée.",
      noInputs: "Aucune entrée — il démarre depuis le lancement.",
    },
    diagrams: {
      sequence: "Séquence — version texte",
      pipeline: "Pipeline d'intercepteurs — version texte",
      states: "États — version texte",
      architecture: "Architecture",
      coreChain: "Chaîne du cœur : toujours, dans l'ordre du code",
      configuredChain: "Chaîne configurée : dans l'ordre de la base",
      participants: "Participants",
      reply: "répond à",
      step: "Étape",
      usedBy: "Utilisé par",
      uses: "Utilise",
      calls: "Appelle",
      calledBy: "Appelé par",
      none: "Aucun",
      notBuilt: "pas encore construit",
      initial: "début",
      final: "fin",
      terminal: "Terminal",
      repository: "Dépôt",
      role: "Rôle",
      owner: "Propriétaire",
      moduleMap: "La carte des modules est listée dans les tableaux de cette page.",
    },
  },
};
