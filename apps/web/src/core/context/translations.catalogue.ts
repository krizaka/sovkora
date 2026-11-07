/**
 * @file translations.catalogue.ts
 * @description The words of the Studio list (/studios) and of « Plan & packs » (/packs), in English and French.
 */

export interface CatalogueDictionary {
  title: string;
  subtitle: string;
  searchLabel: string;
  /** `{n}`: how many Studios the catalogue holds. */
  searchPlaceholder: string;
  clearSearch: string;
  viewLabel: string;
  views: Record<"all" | "ready" | "more", string>;
  tradeLabel: string;
  allTrades: string;
  /** `{trade}`: the accessible name of one trade's list. */
  listLabel: string;
  states: Record<"ready" | "needs-pack" | "needs-plan" | "unknown", string>;
  /** `{pack}` for `pack`. */
  sources: Record<"plan" | "pack" | "free" | "granted", string>;
  /** `{pack}`, `{price}`. */
  needsPack: string;
  needsPackNoPrice: string;
  needsPlan: string;
  unknown: string;
  update: string;
  actions: Record<"run" | "getPack" | "seePlans", string>;
  /** `{studio}`: the accessible name of a row's action. */
  actionLabels: Record<"run" | "getPack" | "seePlans", string>;
  emptyTitle: string;
  emptyBody: string;
  emptyReset: string;
  emptyMore: string;
  loadError: string;
  retry: string;
}

export interface PlanDictionary {
  /** `{plan}`: « Free plan ». */
  name: string;
  /** `{n}`. */
  creditsMonthly: string;
  ready: string;
  more: string;
  link: string;
  loading: string;
  unknown: string;
  title: string;
  subtitle: string;
  yourPlan: string;
  /** `{price}`. */
  pricePerMonth: string;
  free: string;
  seeUsage: string;
  yourPacks: string;
  morePacks: string;
  noMorePacks: string;
  noPacks: string;
  /** One Studio, and `{n}` Studios. */
  studiosCount: { one: string; other: string };
  details: string;
  included: string;
  owned: string;
  /** `{n}`. */
  credits: string;
  priceUnavailable: string;
  drawerStudios: string;
  add: string;
  adding: string;
  remove: string;
  close: string;
  /** `{pack}`: the accessible name of a row's « Details ». */
  detailsLabel: string;
}

export const catalogue: Record<"en" | "fr", CatalogueDictionary> = {
  en: {
    title: "Studios",
    subtitle: "Workflows for your trade. Find one and run it.",
    searchLabel: "Search Studios",
    searchPlaceholder: "Search {n} Studios",
    clearSearch: "Clear the search",
    viewLabel: "Show",
    views: { all: "All", ready: "Ready", more: "Get more" },
    tradeLabel: "Trade",
    allTrades: "All trades",
    listLabel: "Studios in {trade}",
    states: { ready: "Ready", "needs-pack": "Needs a pack", "needs-plan": "Not on your plan", unknown: "Not checked" },
    sources: { plan: "Included in your plan", pack: "From your {pack} pack", free: "Free", granted: "Open to you" },
    needsPack: "{pack} pack, {price}",
    needsPackNoPrice: "{pack} pack",
    needsPlan: "Comes with another plan",
    unknown: "We could not check it right now. Try again in a moment.",
    update: "New version",
    actions: { run: "Run", getPack: "Get pack", seePlans: "See plans" },
    actionLabels: {
      run: "Run {studio}",
      getPack: "Get the pack for {studio}",
      seePlans: "See the plans that include {studio}",
    },
    emptyTitle: "No Studio matches",
    emptyBody: "Try another word, or look in every trade.",
    emptyReset: "Show every Studio",
    emptyMore: "Every Studio in the catalogue is ready for you.",
    loadError: "The Studios could not be loaded.",
    retry: "Try again",
  },
  fr: {
    title: "Studios",
    subtitle: "Des workflows pour votre métier. Trouvez-en un et lancez-le.",
    searchLabel: "Rechercher un Studio",
    searchPlaceholder: "Rechercher parmi {n} Studios",
    clearSearch: "Effacer la recherche",
    viewLabel: "Afficher",
    views: { all: "Tous", ready: "Prêts", more: "À obtenir" },
    tradeLabel: "Métier",
    allTrades: "Tous les métiers",
    listLabel: "Studios : {trade}",
    states: { ready: "Prêt", "needs-pack": "Pack requis", "needs-plan": "Hors de votre offre", unknown: "Non vérifié" },
    sources: {
      plan: "Inclus dans votre offre",
      pack: "Avec votre pack {pack}",
      free: "Gratuit",
      granted: "Ouvert pour vous",
    },
    needsPack: "Pack {pack}, {price}",
    needsPackNoPrice: "Pack {pack}",
    needsPlan: "Fait partie d'une autre offre",
    unknown: "Vérification impossible pour l'instant. Réessayez dans un moment.",
    update: "Nouvelle version",
    actions: { run: "Lancer", getPack: "Obtenir le pack", seePlans: "Voir les offres" },
    actionLabels: {
      run: "Lancer {studio}",
      getPack: "Obtenir le pack de {studio}",
      seePlans: "Voir les offres qui incluent {studio}",
    },
    emptyTitle: "Aucun Studio ne correspond",
    emptyBody: "Essayez un autre mot, ou cherchez dans tous les métiers.",
    emptyReset: "Afficher tous les Studios",
    emptyMore: "Tous les Studios du catalogue sont prêts pour vous.",
    loadError: "Les Studios n'ont pas pu être chargés.",
    retry: "Réessayer",
  },
};

export const plan: Record<"en" | "fr", PlanDictionary> = {
  en: {
    name: "{plan} plan",
    creditsMonthly: "{n} credits a month",
    ready: "{n} Studios ready",
    more: "{n} more with a pack or another plan",
    link: "Plan & packs",
    loading: "Reading your plan…",
    unknown: "Your plan could not be read",
    title: "Plan & packs",
    subtitle:
      "What your plan gives you, the packs you hold, and what another pack would add. A pack adds to your plan, it never replaces it.",
    yourPlan: "Your plan",
    pricePerMonth: "{price} a month",
    free: "Free",
    seeUsage: "Credits and usage",
    yourPacks: "Your packs",
    morePacks: "More packs",
    noMorePacks: "You hold every pack on offer.",
    noPacks: "No pack yet. Your plan's Studios are ready all the same.",
    studiosCount: { one: "1 Studio", other: "{n} Studios" },
    details: "Details",
    included: "Included in your plan",
    owned: "In your account",
    credits: "{n} credits included",
    priceUnavailable: "Price not available right now",
    drawerStudios: "Studios in this pack",
    add: "Add to my account",
    adding: "Adding…",
    remove: "Remove from my account",
    close: "Close",
    detailsLabel: "Details of the {pack} pack",
  },
  fr: {
    name: "Offre {plan}",
    creditsMonthly: "{n} crédits par mois",
    ready: "{n} Studios prêts",
    more: "{n} de plus avec un pack ou une autre offre",
    link: "Offre et packs",
    loading: "Lecture de votre offre…",
    unknown: "Votre offre n'a pas pu être lue",
    title: "Offre et packs",
    subtitle:
      "Ce que votre offre vous donne, les packs que vous avez, et ce qu'un autre pack ajouterait. Un pack s'ajoute à votre offre, il ne la remplace jamais.",
    yourPlan: "Votre offre",
    pricePerMonth: "{price} par mois",
    free: "Gratuit",
    seeUsage: "Crédits et utilisation",
    yourPacks: "Vos packs",
    morePacks: "Autres packs",
    noMorePacks: "Vous avez tous les packs proposés.",
    noPacks: "Aucun pack pour l'instant. Les Studios de votre offre sont prêts quand même.",
    studiosCount: { one: "1 Studio", other: "{n} Studios" },
    details: "Détails",
    included: "Inclus dans votre offre",
    owned: "Dans votre compte",
    credits: "{n} crédits inclus",
    priceUnavailable: "Prix indisponible pour l'instant",
    drawerStudios: "Studios de ce pack",
    add: "Ajouter à mon compte",
    adding: "Ajout…",
    remove: "Retirer de mon compte",
    close: "Fermer",
    detailsLabel: "Détails du pack {pack}",
  },
};
