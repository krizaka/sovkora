/**
 * @file translations.mobile.ts
 * @description Copy of the Mobile section of the home page and of /docs/mobile — the steps of the Sovkora app, en and fr.
 * Each step says the person's goal, what the screen does for them, and three things to notice; each of those is
 * readable in the app's code (orazaka-mobile-client) and visible in the recording — nothing invented. Recorded on the
 * iOS 27 simulator, in dark and light, against www.orazaka.com, signed in as the demo persona, Mira.
 */
type Locale = "en" | "fr";

export interface MobileStepCopy {
  /** The person's goal, as the step's title. */
  goal: string;
  /** One line, for the home page. */
  short: string;
  /** What the screen does and why it matters. */
  purpose: string;
  /** The text alternative of the recording. */
  alt: string;
  /** Three things to notice. */
  notice: [string, string, string];
}

export interface MobileDictionary {
  eyebrow: string;
  title: string;
  lead: string;
  /** The link from the home page to /mobile. */
  all: string;
  recordedOn: string;
  stepsTitle: string;
  step: string;
  notice: string;
  frameLabel: string;
  home: string;
  meta: string;
  metaDescription: string;
  steps: Record<"sign-in" | "studio-run" | "ask" | "jobs", MobileStepCopy>;
}

export const mobile: Record<Locale, MobileDictionary> = {
  en: {
    eyebrow: "On your phone",
    title: "{brand} in your pocket",
    lead: "The mobile app runs your work from anywhere — make an image from a sentence, ask the model, and see where your jobs stand — against the same edge, services and interceptor pipeline as the web client. This is the real app, not a mock-up. Press play; nothing starts by itself.",
    all: "Follow every step",
    recordedOn:
      "Recorded on the iOS 27 simulator (iPhone 17), in dark and light, against www.orazaka.com, signed in as the demo persona, Mira. Typing is sped up and waits are cut — each cut says how long it was.",
    stepsTitle: "The steps",
    step: "Step",
    notice: "What to notice",
    frameLabel: "{brand} mobile app",
    home: "Back to the home page",
    meta: "{brand} — Mobile",
    metaDescription:
      "The real {brand} mobile app, step by step: sign in, make an image, ask and get a streamed answer, follow your jobs.",
    steps: {
      "sign-in": {
        goal: "Sign in to your workspace",
        short: "Sign in and land on the Studios.",
        purpose:
          "One screen, one account: you sign in with the same credentials as on the web and go straight to your Studios, with nothing to configure on the phone.",
        alt: "Mira types an e-mail and a password, taps Sign in and lands on the list of Studios.",
        notice: [
          "Sign in stays disabled until both the e-mail and the password are filled.",
          "No extra step after signing in: as soon as the session exists, the app swaps the sign-in screens for your Studios.",
          "The four tabs — Studios, Chat, Jobs, Profile — speak the language of the phone, in light or dark.",
        ],
      },
      "studio-run": {
        goal: "Make an image from a sentence",
        short: "Open the image Studio, describe, launch: the real result.",
        purpose:
          "A Studio is a ready-made workflow. The catalogue shows each one, you describe what you want, press Launch, and follow it to its result without opening a laptop.",
        alt: "A sentence is typed into the image Studio, Launch is pressed, the run goes from running to done and the generated image appears with its seed, model and generation time.",
        notice: [
          "The result is the run's own output from the platform: the image, and beside it the seed, the model and the time it took — nothing drawn by the app.",
          "Tuning (seed, model, things to avoid) stays under More options; the sentence and the format are all most runs need.",
          "The wait is cut from the recording and the label says how long it was; the app itself shows a progress bar while it runs.",
        ],
      },
      ask: {
        goal: "Ask the model, and read the answer as it is written",
        short: "Type a request; the answer streams in.",
        purpose:
          "When you only need an answer, not a workflow, you just ask. The reply arrives word by word instead of after a long wait, and the request goes through {brand}'s own pipeline.",
        alt: "A request for a reply to a customer is typed; the model's answer appears in the conversation as it is written, with the interceptors that ran listed under it.",
        notice: [
          "The chips under the answer are the interceptors that really ran for this request, as the router reported them — not a canned list.",
          'The model chip says which model answers. The line "0 bytes left your network" appears only when that model runs on your own server — never otherwise.',
          "Leaving the screen mid-answer closes the request.",
        ],
      },
      jobs: {
        goal: "Follow your jobs without opening the console",
        short: "Jobs per day, by kind, then each one.",
        purpose:
          "Images, videos, voice and text are produced in the background. This screen tells you what is done, what is running and what failed, so you know where things stand from your pocket.",
        alt: "A chart of the last seven days, a chart by kind of work, then the list of jobs with their state.",
        notice: [
          "Two charts above the list: the last seven days split into succeeded and failed, and the share of each kind of work.",
          "Each job shows its state, a progress bar while it runs, and a sentence when it failed — never a stack trace.",
          "It is read-only on purpose: approving or purging a job stays in the console, never one tap away in a pocket.",
        ],
      },
    },
  },
  fr: {
    eyebrow: "Sur votre téléphone",
    title: "{brand} dans la poche",
    lead: "L'app mobile mène votre travail de n'importe où — faire une image à partir d'une phrase, interroger le modèle, voir où en sont vos jobs — face au même edge, aux mêmes services et à la même chaîne d'intercepteurs que le client web. C'est la vraie app, pas une maquette. Appuyez sur lecture ; rien ne démarre seul.",
    all: "Suivre chaque étape",
    recordedOn:
      "Enregistré sur le simulateur iOS 27 (iPhone 17), en sombre et en clair, face à www.orazaka.com, connecté en tant que la persona de démonstration, Mira. La saisie est accélérée et les attentes coupées — chaque coupe dit sa durée.",
    stepsTitle: "Les étapes",
    step: "Étape",
    notice: "À remarquer",
    frameLabel: "App mobile {brand}",
    home: "Retour à l'accueil",
    meta: "{brand} — Mobile",
    metaDescription:
      "La vraie app mobile {brand}, pas à pas : se connecter, faire une image, demander et lire une réponse en flux, suivre ses jobs.",
    steps: {
      "sign-in": {
        goal: "Se connecter à son espace",
        short: "Se connecter et arriver sur les Studios.",
        purpose:
          "Un écran, un compte : vous vous connectez avec les mêmes identifiants que sur le web et vous arrivez directement sur vos Studios, sans rien à configurer sur le téléphone.",
        alt: "Mira saisit un e-mail et un mot de passe, appuie sur Se connecter et arrive sur la liste des Studios.",
        notice: [
          "Le bouton de connexion reste désactivé tant que l'e-mail et le mot de passe ne sont pas remplis.",
          "Pas d'étape en plus : dès que la session existe, l'app remplace les écrans de connexion par vos Studios.",
          "Les quatre onglets — Studios, Chat, Tâches, Profil — parlent la langue du téléphone, en clair ou en sombre.",
        ],
      },
      "studio-run": {
        goal: "Faire une image à partir d'une phrase",
        short: "Ouvrir le Studio d'image, décrire, lancer : le vrai résultat.",
        purpose:
          "Un Studio est un flux de travail prêt à l'emploi. Le catalogue montre chacun, vous décrivez ce que vous voulez, vous lancez, et vous le suivez jusqu'au résultat sans ouvrir un ordinateur.",
        alt: "Une phrase est saisie dans le Studio d'image, on appuie sur Lancer, l'exécution passe de en cours à terminée et l'image générée apparaît avec sa graine, son modèle et son temps de génération.",
        notice: [
          "Le résultat est la sortie réelle de l'exécution sur la plateforme : l'image, et à côté la graine, le modèle et le temps pris — rien n'est dessiné par l'app.",
          "Les réglages (graine, modèle, à éviter) restent sous Plus d'options ; la phrase et le format suffisent à la plupart des exécutions.",
          "L'attente est coupée de l'enregistrement et l'étiquette dit sa durée ; dans l'app, une barre de progression s'affiche pendant l'exécution.",
        ],
      },
      ask: {
        goal: "Poser une question au modèle et lire la réponse au fil de l'écriture",
        short: "Saisir une demande ; la réponse arrive en flux.",
        purpose:
          "Quand il vous faut une réponse et non un flux de travail, vous demandez. La réponse arrive mot à mot plutôt qu'après une longue attente, et la requête passe par la chaîne propre à {brand}.",
        alt: "Une demande de réponse à un client est saisie ; la réponse du modèle apparaît dans la conversation au fil de l'écriture, avec les intercepteurs exécutés listés dessous.",
        notice: [
          "Les pastilles sous la réponse sont les intercepteurs réellement exécutés pour cette requête, tels que le routeur les a rapportés — pas une liste figée.",
          "La pastille du modèle dit lequel répond. La ligne « 0 octet n'a quitté votre réseau » n'apparaît que si ce modèle tourne sur votre propre serveur — jamais autrement.",
          "Quitter l'écran en pleine réponse ferme la requête.",
        ],
      },
      jobs: {
        goal: "Suivre ses jobs sans ouvrir la console",
        short: "Les jobs par jour, par type, puis chacun.",
        purpose:
          "Images, vidéos, voix et texte sont produits en arrière-plan. Cet écran dit ce qui est terminé, ce qui tourne et ce qui a échoué : vous savez où ça en est depuis votre poche.",
        alt: "Un graphique des sept derniers jours, un graphique par type de travail, puis la liste des jobs avec leur état.",
        notice: [
          "Deux graphiques au-dessus de la liste : les sept derniers jours entre réussis et échecs, et la part de chaque type de travail.",
          "Chaque job affiche son état, une barre de progression tant qu'il tourne, et une phrase s'il a échoué — jamais une trace d'erreur.",
          "C'est en lecture seule, volontairement : approuver ou purger un job reste dans la console, jamais à un appui dans une poche.",
        ],
      },
    },
  },
};
