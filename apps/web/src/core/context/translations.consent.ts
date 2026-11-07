/**
 * @file translations.consent.ts
 * @description The words of the consent step of a wellbeing (REGULATED) Studio, in English and French.
 *
 * The statement itself is never here: it is the server's text, shown verbatim (ADR-055 section 3). These are the
 * words around it, and they promise nothing the server does not do.
 */

export interface ConsentDictionary {
  /** `{studio}`: the Studio about to start. */
  title: string;
  lead: string;
  doesTitle: string;
  does: string[];
  doesNotTitle: string;
  doesNot: string[];
  /** The AI disclosure (AI Act, art. 50). */
  aiNotice: string;
  /** No number is hard-coded: the Studio shows the verified line of the chosen region when it is needed. */
  crisis: string;
  /** `{version}`: the version of the statement in force. */
  statementTitle: string;
  statementNote: string;
  ageLabel: string;
  regionLabel: string;
  regionPlaceholder: string;
  regionHelp: string;
  regions: Record<"FR" | "BE-FR" | "BE-NL" | "CA" | "CA-QC", string>;
  consentLabel: string;
  agree: string;
  agreeing: string;
  cancel: string;
  close: string;
  /** Said when the server refuses the age or the region (a 400), so the person knows why. */
  refused: string;
  /** Before the first run, on the Studio's page. */
  asked: string;
  givenTitle: string;
  withdraw: string;
  withdrawTitle: string;
  withdrawBody: string;
  withdrawConfirm: string;
  withdrawing: string;
  keep: string;
  /** The verified helpline of the chosen region, as the server sends it. `{label}` `{contact}` `{availability}`. */
  helpline: string;
  helplineSource: string;
  /** « Delete all my wellbeing data »: the erase the statement promises. */
  erase: string;
  eraseTitle: string;
  eraseBody: string;
  eraseConfirm: string;
  erasing: string;
  erased: string;
}

export const consent: Record<"en" | "fr", ConsentDictionary> = {
  en: {
    title: "Before you start {studio}",
    lead: "This Studio handles what you share about how you feel. Read this once, then decide.",
    doesTitle: "What it does",
    does: [
      "Helps you put your week into words and prepare a session with someone you trust.",
      "Keeps what you write, encrypted, for a limited time, as the statement below says.",
    ],
    doesNotTitle: "What it does not do",
    doesNot: [
      "It is not medical advice or care: no diagnosis, no treatment, and it does not replace a professional.",
      "It is not an emergency service.",
    ],
    aiNotice: "You are talking to an AI system, not to a person.",
    crisis:
      "In danger, or thinking about hurting yourself? Call your local emergency number now. If you write something that worries it, the Studio stops and shows a verified helpline for the region you choose below.",
    statementTitle: "Statement, version {version}",
    statementNote: "Shown exactly as published by the service.",
    ageLabel: "I confirm that I am of legal age (an adult) where I live.",
    regionLabel: "Your region",
    regionPlaceholder: "Choose your region",
    regionHelp: "Only used to show the right helpline. The Studio is offered where a helpline has been verified.",
    regions: {
      FR: "France",
      "BE-FR": "Belgium, French-speaking",
      "BE-NL": "Belgium, Dutch-speaking",
      CA: "Canada",
      "CA-QC": "Quebec",
    },
    consentLabel: "I have read the statement and I explicitly consent to this processing of my health data.",
    agree: "Agree and continue",
    agreeing: "Saving your choice…",
    cancel: "Not now",
    close: "Close",
    refused: "This Studio cannot be started with these answers.",
    asked: "Consent asked: before your first run, this Studio asks you to confirm your age and region and to consent.",
    givenTitle: "You agreed to this Studio's statement.",
    withdraw: "Withdraw my consent",
    withdrawTitle: "Withdraw your consent?",
    withdrawBody:
      "This Studio stops for you now. Entries already saved are deleted automatically within the retention announced in the statement you agreed to; to erase them right away, use « Delete all my wellbeing data ». To use it again, you will be asked again.",
    withdrawConfirm: "Withdraw consent",
    withdrawing: "Withdrawing…",
    keep: "Keep it",
    helpline: "Helpline for this region: {label}, {contact} ({availability}).",
    helplineSource: "Official source",
    erase: "Delete all my wellbeing data",
    eraseTitle: "Delete all your wellbeing data?",
    eraseBody:
      "This permanently erases your entries, your written or spoken exchanges, their results, the files tied to them and the consent you gave. It cannot be undone. All that remains is a record with no content: the date and the Studio, proving you asked.",
    eraseConfirm: "Delete everything",
    erasing: "Deleting…",
    erased: "Your wellbeing data has been deleted.",
  },
  fr: {
    title: "Avant de commencer {studio}",
    lead: "Ce Studio traite ce que vous partagez sur votre état d'esprit. Lisez ceci une fois, puis décidez.",
    doesTitle: "Ce qu'il fait",
    does: [
      "Vous aide à mettre votre semaine en mots et à préparer une séance avec une personne de confiance.",
      "Conserve ce que vous écrivez, chiffré, pour une durée limitée, comme l'indique la déclaration ci-dessous.",
    ],
    doesNotTitle: "Ce qu'il ne fait pas",
    doesNot: [
      "Ce n'est ni un avis médical ni un soin : aucun diagnostic, aucun traitement, et il ne remplace pas un professionnel.",
      "Ce n'est pas un service d'urgence.",
    ],
    aiNotice: "Vous parlez à un système d'IA, pas à une personne.",
    crisis:
      "En danger, ou des idées de vous faire du mal ? Appelez dès maintenant le numéro d'urgence de votre pays. Si vous écrivez quelque chose d'inquiétant, le Studio s'arrête et affiche une ligne d'écoute vérifiée pour la région choisie ci-dessous.",
    statementTitle: "Déclaration, version {version}",
    statementNote: "Affichée telle que publiée par le service.",
    ageLabel: "Je confirme être majeur·e là où j'habite.",
    regionLabel: "Votre région",
    regionPlaceholder: "Choisissez votre région",
    regionHelp:
      "Sert uniquement à afficher la bonne ligne d'écoute. Le Studio est proposé là où une ligne a été vérifiée.",
    regions: {
      FR: "France",
      "BE-FR": "Belgique francophone",
      "BE-NL": "Belgique néerlandophone",
      CA: "Canada",
      "CA-QC": "Québec",
    },
    consentLabel: "J'ai lu la déclaration et je consens explicitement à ce traitement de mes données de santé.",
    agree: "J'accepte et je continue",
    agreeing: "Enregistrement de votre choix…",
    cancel: "Pas maintenant",
    close: "Fermer",
    refused: "Ce Studio ne peut pas être lancé avec ces réponses.",
    asked:
      "Consentement demandé : avant votre premier lancement, ce Studio vous demande de confirmer votre âge et votre région, et de consentir.",
    givenTitle: "Vous avez accepté la déclaration de ce Studio.",
    withdraw: "Retirer mon consentement",
    withdrawTitle: "Retirer votre consentement ?",
    withdrawBody:
      "Ce Studio s'arrête pour vous maintenant. Les entrées déjà enregistrées sont supprimées automatiquement dans la durée annoncée dans la déclaration que vous avez acceptée ; pour les effacer tout de suite, utilisez « Supprimer toutes mes données de bien-être ». Pour le réutiliser, on vous le redemandera.",
    withdrawConfirm: "Retirer le consentement",
    withdrawing: "Retrait en cours…",
    keep: "Le garder",
    helpline: "Ligne d'écoute pour cette région : {label}, {contact} ({availability}).",
    helplineSource: "Source officielle",
    erase: "Supprimer toutes mes données de bien-être",
    eraseTitle: "Supprimer toutes vos données de bien-être ?",
    eraseBody:
      "Cela efface définitivement vos entrées, vos échanges écrits ou parlés, leurs résultats, les fichiers associés et le consentement que vous avez donné. C'est irréversible. Il ne reste qu'une trace sans contenu : la date et le Studio, qui prouve que vous l'avez demandé.",
    eraseConfirm: "Tout supprimer",
    erasing: "Suppression…",
    erased: "Vos données de bien-être ont été supprimées.",
  },
};
