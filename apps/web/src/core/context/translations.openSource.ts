/**
 * @file translations.openSource.ts
 * @description The words of /open-source, en and fr (krizaka/orazaka-web-client#290). The repositories are read from
 * GitHub at build, never typed; the licence layers restate docs/LICENSING.md of the Sovkora repository.
 */
import { localise, w } from "./words";

type Locale = "en" | "fr";

export interface OpenSourceDictionary {
  meta: { title: string; description: string };
  eyebrow: string;
  title: string;
  lead: string;
  source: string;
  docs: string;
  repos: { title: string; lead: string; all: string; updated: string; unavailable: string; label: string };
  licence: {
    title: string;
    lead: string;
    label: string;
    layer: string;
    where: string;
    licence: string;
    rows: { layer: string; where: string; licence: string }[];
    note: string;
  };
  selfHost: { title: string; lead: string; steps: string[]; cta: string };
  contribute: { title: string; lead: string; items: { title: string; text: string }[]; cta: string };
}

const WORDS = {
  meta: {
    title: w("Open source — {brand}", "Open source — {brand}"),
    description: w(
      "{brand}'s engine, packs and CLI are Apache-2.0: the repositories, the licence line, self-hosting and how to contribute.",
      "Le moteur, les packs et la CLI de {brand} sont en Apache-2.0 : les dépôts, la ligne de licence, l'auto-hébergement et comment contribuer.",
    ),
  },
  eyebrow: w("Open source", "Open source"),
  title: w(
    "The engine is open. Read it, run it, change it.",
    "Le moteur est ouvert. Lisez-le, lancez-le, modifiez-le.",
  ),
  lead: w(
    "{brand}'s engine, its pack interface, its command line and the reference packs are Apache-2.0 and public. Commercial packs and the hosted services are a separate, private line.",
    "Le moteur de {brand}, son interface de packs, sa ligne de commande et les packs de référence sont publics, en Apache-2.0. Les packs commerciaux et les services hébergés forment une ligne distincte et privée.",
  ),
  source: w("See the code", "Voir le code"),
  docs: w("Read the documentation", "Lire la documentation"),
  repos: {
    title: w("Repositories", "Dépôts"),
    lead: w(
      "Read from GitHub when the site is built: the public repositories of the {brand} family.",
      "Lus sur GitHub à la construction du site : les dépôts publics de la famille {brand}.",
    ),
    all: w("All repositories", "Tous les dépôts"),
    updated: w("Last push", "Dernier push"),
    unavailable: w(
      "The list could not be read from GitHub just now. The organisation page has it.",
      "La liste n'a pas pu être lue sur GitHub à l'instant. La page de l'organisation la contient.",
    ),
    label: w("{brand} repositories", "Dépôts de {brand}"),
  },
  licence: {
    title: w("Licence", "Licence"),
    lead: w(
      "Everything in the main repository is Apache-2.0. The line falls at the repository, not at a flag.",
      "Tout le dépôt principal est en Apache-2.0. La ligne passe au dépôt, pas à un drapeau.",
    ),
    label: w("Where the licence line falls", "Où passe la ligne de licence"),
    layer: w("Layer", "Couche"),
    where: w("Where", "Où"),
    licence: w("Licence", "Licence"),
    rows: [
      {
        layer: w(
          "Engine: studio, jobs, billing, identity, interceptors",
          "Moteur : studio, jobs, facturation, identité, intercepteurs",
        ),
        where: w("The main repository", "Le dépôt principal"),
        licence: w("Apache-2.0", "Apache-2.0"),
      },
      {
        layer: w(
          "Pack interface, the command line and the reference packs",
          "Interface de packs, ligne de commande et packs de référence",
        ),
        where: w("The main repository", "Le dépôt principal"),
        licence: w("Apache-2.0", "Apache-2.0"),
      },
      {
        layer: w(
          "Commercial packs, hosted registry, managed workers",
          "Packs commerciaux, registre hébergé, workers gérés",
        ),
        where: w("Private repositories", "Dépôts privés"),
        licence: w("Proprietary", "Propriétaire"),
      },
    ],
    note: w(
      "The full text is the LICENSING page of the repository.",
      "Le texte complet est la page LICENSING du dépôt.",
    ),
  },
  selfHost: {
    title: w("Self-host in three lines", "Auto-héberger en trois lignes"),
    lead: w(
      "{brand} installs on a Mac, on Linux or from source. The guide lists what each path needs.",
      "{brand} s'installe sur Mac, sur Linux ou depuis les sources. Le guide liste ce que chaque voie demande.",
    ),
    steps: [
      w("Clone the repository.", "Clonez le dépôt."),
      w("Follow the install path for your machine.", "Suivez la voie d'installation de votre machine."),
      w("Open a studio and run your first job.", "Ouvrez un studio et lancez votre premier job."),
    ],
    cta: w("Open the self-hosting guide", "Ouvrir le guide d'auto-hébergement"),
  },
  contribute: {
    title: w("Contribute", "Contribuer"),
    lead: w(
      "Issues and pull requests are read by a person. Start small.",
      "Les tickets et les pull requests sont lus par une personne. Commencez petit.",
    ),
    items: [
      {
        title: w("Report", "Signaler"),
        text: w(
          "Open an issue with the steps to reproduce it. A security flaw goes through the security policy, not a public issue.",
          "Ouvrez un ticket avec les étapes pour le reproduire. Une faille de sécurité passe par la politique de sécurité, pas par un ticket public.",
        ),
      },
      {
        title: w("Propose", "Proposer"),
        text: w(
          "Open a pull request with its tests; every change ships with the test that proves it.",
          "Ouvrez une pull request avec ses tests ; chaque changement arrive avec le test qui le prouve.",
        ),
      },
      {
        title: w("Write a pack", "Écrire un pack"),
        text: w(
          "A pack is a manifest and a worker: the documentation describes the interface.",
          "Un pack est un manifeste et un worker : la documentation décrit l'interface.",
        ),
      },
    ],
    cta: w("Security policy", "Politique de sécurité"),
  },
} as const;

export const openSource: Record<Locale, OpenSourceDictionary> = {
  en: localise(WORDS, 0),
  fr: localise(WORDS, 1),
};
