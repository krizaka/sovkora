/**
 * @file law.ts
 * @description The words of Law → Proposals (ADR-089 §8): the review queue, the golden cases and the promotion of a
 * kind, the corpus and the demand — each message with its English and French side by side, so a translation is read
 * where it is written and no language can miss a message. `law.test.mts` checks both say the same placeholders.
 */

/** One message in both languages. */
interface Pair {
  readonly en: string;
  readonly fr: string;
}

/** The words of one language: every pair of the tree read as its string. */
type Words<T> = T extends Pair ? string : { readonly [K in keyof T]: Words<T[K]> };

const messages = {
  title: { en: "Law", fr: "Droit" },
  lead: {
    en: "The rules the engine checks documents with, derived from the law and reviewed here before they reach a pack. Every decision is audited.",
    fr: "Les règles avec lesquelles le moteur vérifie les documents, tirées de la loi et relues ici avant d’entrer dans un pack. Chaque décision est journalisée.",
  },
  loading: { en: "Reading the law service", fr: "Lecture du service du droit" },
  retry: { en: "Retry", fr: "Réessayer" },
  tabs: {
    label: { en: "Law", fr: "Droit" },
    proposals: { en: "Proposals", fr: "Propositions" },
    corpus: { en: "Corpus", fr: "Corpus" },
    demand: { en: "Demand", fr: "Demande" },
  },
  table: {
    empty: { en: "Nothing to show", fr: "Rien à afficher" },
    loading: { en: "Loading", fr: "Chargement" },
    details: { en: "Details", fr: "Détails" },
    expandRow: { en: "Show details of {name}", fr: "Afficher le détail de {name}" },
    showMore: { en: "Show more about {name}", fr: "En savoir plus sur {name}" },
    results: { en: "{count} rows", fr: "{count} lignes" },
    nav: { en: "Pages", fr: "Pages" },
    previous: { en: "Previous", fr: "Précédente" },
    next: { en: "Next", fr: "Suivante" },
    page: { en: "Page {page}", fr: "Page {page}" },
    compact: { en: "Page {page} of {count}", fr: "Page {page} sur {count}" },
    pageSize: { en: "Rows per page", fr: "Lignes par page" },
  },
  languages: {
    fr: { en: "French", fr: "Français" },
    en: { en: "English", fr: "Anglais" },
  },
  dateParts: {
    day: { en: "Day", fr: "Jour" },
    month: { en: "Month", fr: "Mois" },
    year: { en: "Year", fr: "Année" },
  },
  status: {
    PROPOSED: { en: "Proposed", fr: "Proposée" },
    REJECTED_AUTO: { en: "Refused by the vet", fr: "Refusée par le contrôle" },
    APPROVED: { en: "Approved", fr: "Approuvée" },
    REJECTED: { en: "Rejected", fr: "Rejetée" },
    PROMOTED: { en: "Promoted", fr: "Promue" },
    SUPERSEDED: { en: "Superseded", fr: "Remplacée" },
  },
  severity: {
    BLOCKING: { en: "Blocking", fr: "Bloquante" },
    MAJOR: { en: "Major", fr: "Majeure" },
    ADVISORY: { en: "Advisory", fr: "Conseil" },
  },
  method: {
    DETERMINISTIC: { en: "Deterministic", fr: "Déterministe" },
    JUDGMENT: { en: "Judgment", fr: "Jugement" },
  },
  checkType: {
    NONE: { en: "No pattern", fr: "Aucun motif" },
    REQUIRED_PATTERN: { en: "Required pattern", fr: "Motif obligatoire" },
    FORBIDDEN_PATTERN: { en: "Forbidden pattern", fr: "Motif interdit" },
  },
  queue: {
    statusFilter: { en: "Filter the proposals by status", fr: "Filtrer les propositions par statut" },
    allStatuses: { en: "All", fr: "Toutes" },
    kindFilter: { en: "Filter the proposals by kind", fr: "Filtrer les propositions par type" },
    allKinds: { en: "All kinds", fr: "Tous les types" },
    caption: { en: "Rule proposals", fr: "Propositions de règles" },
    rule: { en: "Rule", fr: "Règle" },
    kind: { en: "Kind", fr: "Type" },
    status: { en: "Status", fr: "Statut" },
    severity: { en: "Severity", fr: "Gravité" },
    revision: { en: "Revision", fr: "Révision" },
    created: { en: "Proposed", fr: "Proposée le" },
  },
  detail: {
    law: { en: "The law", fr: "La loi" },
    article: { en: "{instrument}, art. {ref} ({language})", fr: "{instrument}, art. {ref} ({language})" },
    officialText: { en: "Official text of {citation}", fr: "Texte officiel de {citation}" },
    pinned: { en: "Pinned version {hash}", fr: "Version épinglée {hash}" },
    noSource: { en: "No official URL was given.", fr: "Aucune adresse officielle n’a été donnée." },
    noLaw: {
      en: "No article is cited: this proposal cannot be promoted.",
      fr: "Aucun article n’est cité : cette proposition ne peut pas être promue.",
    },
    rule: { en: "The rule", fr: "La règle" },
    subject: { en: "Applies when", fr: "S’applique quand" },
    check: { en: "Check", fr: "Vérification" },
    question: { en: "Question to the model", fr: "Question au modèle" },
    citation: { en: "Citation", fr: "Citation" },
    onPass: { en: "When it passes", fr: "Quand elle passe" },
    onFail: { en: "When it fails", fr: "Quand elle échoue" },
    method: { en: "Method", fr: "Méthode" },
    fixtures: { en: "Fixtures", fr: "Exemples" },
    mustPass: { en: "Must pass", fr: "Doivent passer" },
    mustFail: { en: "Must fail", fr: "Doivent échouer" },
    noFixtures: { en: "No fixtures.", fr: "Aucun exemple." },
    vet: { en: "Vet report", fr: "Rapport de contrôle" },
    vetPassed: {
      en: "Passed the vet (version {version}, law of {date}).",
      fr: "A passé le contrôle (version {version}, loi du {date}).",
    },
    vetFailed: {
      en: "Refused by the vet (version {version}, law of {date}).",
      fr: "Refusée par le contrôle (version {version}, loi du {date}).",
    },
    noVet: { en: "Not vetted yet.", fr: "Pas encore contrôlée." },
    reviewed: { en: "Reviewed by {actor} on {date}.", fr: "Relue par {actor} le {date}." },
    note: { en: "Note: {note}", fr: "Note : {note}" },
    inPacks: { en: "In a packs pull request", fr: "Dans une demande de fusion des packs" },
    run: { en: "Derivation run {runId}", fr: "Dérivation {runId}" },
  },
  vetReasons: {
    SCHEMA_INVALID: {
      en: "The rule does not follow the ruleset schema.",
      fr: "La règle ne suit pas le schéma des règles.",
    },
    REGEX_INVALID: {
      en: "The pattern is not a valid regular expression.",
      fr: "Le motif n’est pas une expression régulière valide.",
    },
    REGEX_MATCHES_EMPTY: { en: "The pattern matches an empty text.", fr: "Le motif accepte un texte vide." },
    QUOTE_NOT_IN_ARTICLE: { en: "The quote is not in the article.", fr: "La citation n’est pas dans l’article." },
    UNSUPPORTED_NUMBER: {
      en: "A number of the rule is not in the article.",
      fr: "Un nombre de la règle n’est pas dans l’article.",
    },
    FIXTURES_INDISTINCT: {
      en: "The pattern does not tell the passing fixtures from the failing ones.",
      fr: "Le motif ne distingue pas les exemples qui passent de ceux qui échouent.",
    },
    SUBJECT_MISSING: { en: "A judgment rule needs a subject.", fr: "Une règle de jugement a besoin d’un sujet." },
    LAW_VERSION_UNKNOWN: {
      en: "The pinned version of the article is not in the corpus.",
      fr: "La version épinglée de l’article n’est pas dans le corpus.",
    },
    OVER_RULE_LIMIT: {
      en: "The kind already has the most rules a derivation may propose.",
      fr: "Le type a déjà le nombre maximal de règles qu’une dérivation peut proposer.",
    },
    other: { en: "Finding {reason}.", fr: "Constat {reason}." },
  },
  actions: {
    approve: { en: "Approve", fr: "Approuver" },
    approveNote: { en: "Note for the approval (optional)", fr: "Note d’approbation (facultative)" },
    reject: { en: "Reject", fr: "Rejeter" },
    rejectReason: { en: "Reason for the rejection (required)", fr: "Motif du rejet (obligatoire)" },
    reasonRequired: { en: "A rejection needs a reason.", fr: "Un rejet demande un motif." },
    edit: { en: "Edit the rule", fr: "Modifier la règle" },
    editTitle: { en: "Edit {ruleId}", fr: "Modifier {ruleId}" },
    editLead: {
      en: "Saving makes a new revision, vetted again. The cited law stays as it is.",
      fr: "Enregistrer crée une nouvelle révision, contrôlée à nouveau. La loi citée reste telle quelle.",
    },
    save: { en: "Save as a new revision", fr: "Enregistrer une nouvelle révision" },
    cancel: { en: "Cancel", fr: "Annuler" },
    approved: { en: "{ruleId} approved.", fr: "{ruleId} approuvée." },
    rejected: { en: "{ruleId} rejected.", fr: "{ruleId} rejetée." },
    revisedPassed: {
      en: "Revision {revision} of {ruleId} passed the vet: it waits for review.",
      fr: "La révision {revision} de {ruleId} a passé le contrôle : elle attend sa relecture.",
    },
    revisedFailed: {
      en: "Revision {revision} of {ruleId} was refused by the vet; the previous revision is unchanged.",
      fr: "La révision {revision} de {ruleId} a été refusée par le contrôle ; la révision précédente reste inchangée.",
    },
  },
  editor: {
    title: { en: "Title", fr: "Titre" },
    severity: { en: "Severity", fr: "Gravité" },
    method: { en: "Method", fr: "Méthode" },
    subject: { en: "Applies when (regular expression)", fr: "S’applique quand (expression régulière)" },
    checkType: { en: "Check", fr: "Vérification" },
    pattern: { en: "Pattern (regular expression)", fr: "Motif (expression régulière)" },
    question: { en: "Question to the model", fr: "Question au modèle" },
    citation: { en: "Citation", fr: "Citation" },
    sourceUrl: { en: "Official URL", fr: "Adresse officielle" },
    onPass: { en: "Message when it passes", fr: "Message quand elle passe" },
    onFail: { en: "Message when it fails", fr: "Message quand elle échoue" },
    fixturesPass: { en: "Fixtures that must pass (one per line)", fr: "Exemples qui doivent passer (un par ligne)" },
    fixturesFail: { en: "Fixtures that must fail (one per line)", fr: "Exemples qui doivent échouer (un par ligne)" },
    required: { en: "Required.", fr: "Obligatoire." },
    https: { en: "An https address.", fr: "Une adresse https." },
    choose: { en: "Choose a value", fr: "Choisissez une valeur" },
  },
  kind: {
    pick: {
      en: "Choose a kind above to see its golden cases and promote its approved rules.",
      fr: "Choisissez un type ci-dessus pour voir ses cas de référence et promouvoir ses règles approuvées.",
    },
    golden: { en: "Golden cases of {kind}", fr: "Cas de référence de {kind}" },
    goldenSummary: {
      en: "{documents} documents: {compliant} compliant, {defective} with known errors, {blank} blank form — at least {required} required.",
      fr: "{documents} documents : {compliant} conformes, {defective} avec erreurs connues, {blank} formulaire vierge — au moins {required} exigés.",
    },
    met: { en: "Minimum met", fr: "Minimum atteint" },
    notMet: { en: "Minimum not met", fr: "Minimum non atteint" },
    caseName: { en: "Case", fr: "Cas" },
    caseForm: { en: "Form", fr: "Formulaire" },
    forms: {
      FILLED: { en: "Filled", fr: "Rempli" },
      BLANK: { en: "Blank", fr: "Vierge" },
    },
    promote: { en: "Promote {kind}", fr: "Promouvoir {kind}" },
    promoteLead: {
      en: "Pick the approved rules and the date the law is in force from: the engine opens a pull request to orazaka-packs. Its merge is the promotion.",
      fr: "Choisissez les règles approuvées et la date d’entrée en vigueur de la loi : le moteur ouvre une demande de fusion sur orazaka-packs. Sa fusion est la promotion.",
    },
    approvedCaption: { en: "Approved rules of {kind}", fr: "Règles approuvées de {kind}" },
    noneApproved: {
      en: "No approved rule of this kind waits for promotion.",
      fr: "Aucune règle approuvée de ce type n’attend de promotion.",
    },
    selectAll: { en: "Select every approved rule", fr: "Sélectionner toutes les règles approuvées" },
    selectRow: { en: "Select {name}", fr: "Sélectionner {name}" },
    effectiveFrom: { en: "In force from", fr: "En vigueur à partir du" },
    revisionNote: { en: "Revision note (optional)", fr: "Note de révision (facultative)" },
    submit: { en: "Open the packs pull request", fr: "Ouvrir la demande de fusion des packs" },
    nothingSelected: { en: "Select at least one approved rule.", fr: "Sélectionnez au moins une règle approuvée." },
    invalidDate: { en: "A date, YYYY-MM-DD.", fr: "Une date, AAAA-MM-JJ." },
    opened: {
      en: "Pull request opened: revision {revision} of {path}.",
      fr: "Demande de fusion ouverte : révision {revision} de {path}.",
    },
    openPr: { en: "Open the pull request", fr: "Ouvrir la demande de fusion" },
  },
  refusals: {
    "proposal-not-found": { en: "This proposal no longer exists.", fr: "Cette proposition n’existe plus." },
    "proposal-not-editable": {
      en: "This revision can no longer be edited: edit the newest revision.",
      fr: "Cette révision ne peut plus être modifiée : modifiez la révision la plus récente.",
    },
    "proposal-not-reviewable": {
      en: "This proposal has already been decided.",
      fr: "Cette proposition a déjà été tranchée.",
    },
    "proposal-not-approved": {
      en: "Every rule promoted must be approved first.",
      fr: "Chaque règle promue doit d’abord être approuvée.",
    },
    "already-in-review": {
      en: "A rule chosen is already in a packs pull request.",
      fr: "Une règle choisie est déjà dans une demande de fusion des packs.",
    },
    "kind-mismatch": {
      en: "Every rule promoted must belong to this kind.",
      fr: "Chaque règle promue doit appartenir à ce type.",
    },
    "duplicate-rule": {
      en: "Two of the rules chosen have the same rule id.",
      fr: "Deux des règles choisies ont le même identifiant.",
    },
    "golden-minimum-not-met": {
      en: "The kind does not have enough golden cases yet.",
      fr: "Le type n’a pas encore assez de cas de référence.",
    },
    "law-version-mismatch": {
      en: "A pinned article is not the version in force on that date.",
      fr: "Un article épinglé n’est pas la version en vigueur à cette date.",
    },
    "quote-too-long": { en: "A quote is longer than 300 characters.", fr: "Une citation dépasse 300 caractères." },
    "law-promotion-off": {
      en: "Promotion is not configured on this platform.",
      fr: "La promotion n’est pas configurée sur cette plateforme.",
    },
    "law-derivation-off": {
      en: "Derivation is not configured on this platform.",
      fr: "La dérivation n’est pas configurée sur cette plateforme.",
    },
    "law-corpus-disabled": {
      en: "The law corpus is not enabled on this platform.",
      fr: "Le corpus juridique n’est pas activé sur cette plateforme.",
    },
    "kind-not-found": { en: "The pack has no such kind.", fr: "Le pack n’a pas ce type." },
    "invalid-request": {
      en: "The request was refused: check the fields.",
      fr: "La demande a été refusée : vérifiez les champs.",
    },
    forbidden: {
      en: "Only an administrator can use the law screens.",
      fr: "Seul un administrateur peut utiliser les écrans du droit.",
    },
    unavailable: {
      en: "The law service did not answer: it may be starting.",
      fr: "Le service du droit n’a pas répondu : il démarre peut-être.",
    },
  },
  corpus: {
    instruments: { en: "Instruments", fr: "Textes" },
    jurisdiction: { en: "Jurisdiction", fr: "Juridiction" },
    code: { en: "Instrument", fr: "Texte" },
    titleCol: { en: "Title", fr: "Titre" },
    source: { en: "Source", fr: "Source" },
    articles: { en: "Articles", fr: "Articles" },
    versions: { en: "Versions", fr: "Versions" },
    imports: { en: "Imports", fr: "Imports" },
    started: { en: "Started", fr: "Commencé" },
    mode: { en: "Mode", fr: "Mode" },
    modes: {
      AUTOMATED: { en: "Automated", fr: "Automatique" },
      MANUAL: { en: "Manual", fr: "Manuel" },
    },
    importStatus: { en: "Status", fr: "Statut" },
    statuses: {
      RUNNING: { en: "Running", fr: "En cours" },
      SUCCEEDED: { en: "Succeeded", fr: "Réussi" },
      FAILED: { en: "Failed", fr: "Échoué" },
    },
    dataset: { en: "Dataset", fr: "Jeu de données" },
    sha256: { en: "SHA-256", fr: "SHA-256" },
    consolidatedTo: { en: "Consolidated to", fr: "À jour au" },
    importedBy: { en: "By", fr: "Par" },
    counts: { en: "New · changed · unchanged", fr: "Nouveaux · modifiés · inchangés" },
    lookup: { en: "Article in force", fr: "Article en vigueur" },
    lookupLead: {
      en: "Which version of an article was in force on a date — its hash, its length and its official address. The text itself is read only through the metered API.",
      fr: "Quelle version d’un article était en vigueur à une date — son empreinte, sa longueur et son adresse officielle. Le texte lui-même ne se lit que par l’API facturée.",
    },
    ref: { en: "Article", fr: "Article" },
    language: { en: "Language", fr: "Langue" },
    asOf: { en: "On", fr: "Au" },
    lookupSubmit: { en: "Look up", fr: "Chercher" },
    inForce: {
      en: "In force on {date}: from {from}, {length} characters, hash {hash}.",
      fr: "En vigueur le {date} : depuis le {from}, {length} caractères, empreinte {hash}.",
    },
    notInForce: {
      en: "No version of this article was in force on {date}.",
      fr: "Aucune version de cet article n’était en vigueur le {date}.",
    },
    history: { en: "Versions of the article", fr: "Versions de l’article" },
    from: { en: "From", fr: "Du" },
    to: { en: "To", fr: "Au" },
    hash: { en: "Hash", fr: "Empreinte" },
    length: { en: "Characters", fr: "Caractères" },
    official: { en: "Official text", fr: "Texte officiel" },
    current: { en: "In force", fr: "En vigueur" },
    manual: { en: "Manual import", fr: "Import manuel" },
    manualLead: {
      en: "Import a consolidated XML file when the automated import cannot reach the source. Say where the file came from.",
      fr: "Importez un fichier XML consolidé quand l’import automatique ne joint pas la source. Indiquez d’où vient le fichier.",
    },
    file: { en: "Consolidated XML file", fr: "Fichier XML consolidé" },
    fileTitle: { en: "Drop the XML file here", fr: "Déposez le fichier XML ici" },
    browse: { en: "Choose a file", fr: "Choisir un fichier" },
    remove: { en: "Remove {name}", fr: "Retirer {name}" },
    datasetUrl: { en: "Where the file came from (URL)", fr: "Provenance du fichier (URL)" },
    sha256Optional: { en: "Expected SHA-256 (optional)", fr: "SHA-256 attendu (facultatif)" },
    consolidatedToOptional: { en: "Consolidated to (optional)", fr: "À jour au (facultatif)" },
    importSubmit: { en: "Import", fr: "Importer" },
    imported: {
      en: "{stored} versions stored, {unchanged} unchanged.",
      fr: "{stored} versions enregistrées, {unchanged} inchangées.",
    },
    importFailures: { en: "Lines refused", fr: "Lignes refusées" },
    fileRequired: { en: "Choose the XML file.", fr: "Choisissez le fichier XML." },
    sha256Invalid: { en: "64 hexadecimal characters.", fr: "64 caractères hexadécimaux." },
  },
  demand: {
    caption: { en: "Documents of a kind with no promoted ruleset", fr: "Documents d’un type sans règles promues" },
    lead: {
      en: "What people sent that no promoted ruleset checks yet, counted — never any document content.",
      fr: "Ce que les gens ont envoyé et qu’aucune règle promue ne vérifie encore, compté — jamais le contenu d’un document.",
    },
    jurisdiction: { en: "Jurisdiction", fr: "Juridiction" },
    kind: { en: "Kind", fr: "Type" },
    unknown: { en: "Unknown kind", fr: "Type inconnu" },
    count: { en: "Documents", fr: "Documents" },
    firstSeen: { en: "First seen", fr: "Vu la première fois" },
    lastSeen: { en: "Last seen", fr: "Vu la dernière fois" },
    derive: { en: "Start a derivation", fr: "Lancer une dérivation" },
    deriveLead: {
      en: "An administrator starts a derivation: the engine reads the law in force for the kind and proposes rules for review (a Studio run, within its budget).",
      fr: "Un administrateur lance une dérivation : le moteur lit la loi en vigueur pour le type et propose des règles à relire (une exécution Studio, dans son budget).",
    },
    kindId: { en: "Kind", fr: "Type" },
    asOf: { en: "Law in force on", fr: "Loi en vigueur le" },
    language: { en: "Language of the law", fr: "Langue de la loi" },
    deriveSubmit: { en: "Start the derivation", fr: "Lancer la dérivation" },
    derived: {
      en: "Run {runId}: {outcome} — {proposals} proposed, {rejected} refused by the vet, {credits} credits.",
      fr: "Exécution {runId} : {outcome} — {proposals} proposées, {rejected} refusées par le contrôle, {credits} crédits.",
    },
    outcomes: {
      DERIVATION_OFF: { en: "derivation is switched off", fr: "la dérivation est désactivée" },
      NOT_RETRIEVED: { en: "the law could not be retrieved", fr: "la loi n’a pas pu être récupérée" },
      NO_VERIFIED_CLAIMS: {
        en: "no claim could be verified in the law",
        fr: "aucune affirmation n’a pu être vérifiée dans la loi",
      },
      DERIVED: { en: "rules derived", fr: "règles dérivées" },
      BUDGET_EXHAUSTED: { en: "the budget ran out", fr: "le budget est épuisé" },
    },
  },
} as const;

export type LawCopy = Words<typeof messages>;

const isPair = (value: unknown): value is Pair =>
  typeof (value as Pair).en === "string" && typeof (value as Pair).fr === "string";

function wordsOf<T>(tree: T, locale: keyof Pair): Words<T> {
  if (isPair(tree)) return tree[locale] as Words<T>;
  return Object.fromEntries(
    Object.entries(tree as Record<string, unknown>).map(([key, child]) => [key, wordsOf(child, locale)]),
  ) as Words<T>;
}

export const lawCopy: Record<keyof Pair, LawCopy> = { en: wordsOf(messages, "en"), fr: wordsOf(messages, "fr") };
