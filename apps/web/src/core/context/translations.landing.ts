/**
 * @file translations.landing.ts
 * @description Copy of the public home page (visitors without a session). Kept beside
 * `translations.ts` so the sales copy can be reviewed in one place; merged into the dictionary under
 * `landing`. Every claim here is true of an install today — nothing is promised that the product does not do.
 * Each message is written once for both languages, `w(en, fr)`, so the two locales cannot drift apart.
 */

import { LICENCE_ID } from "./translations.licence";
import type { Locale } from "./translations.types";
import type { LandingDictionary } from "./translations.landing.types";
import { localise, w } from "./words";

const WORDS = {
  nav: {
    how: w("How it works", "Fonctionnement"),
    signIn: w("Sign in", "Se connecter"),
    docs: w("Docs", "Docs"),
    product: w("Product", "Produit"),
    download: w("Download", "Télécharger"),
    pricing: w("Pricing", "Tarifs"),
    openSource: w("Source code", "Code source"),
    menu: {
      how: w("What it does, on real screens.", "Ce qu'il fait, sur de vrais écrans."),
      download: w("{brand} Desktop for Apple Silicon.", "{brand} Desktop pour Apple Silicon."),
    },
    langLabel: w("Language", "Langue"),
    themeLabel: w("Change the theme", "Changer le thème"),
    preferences: w("Preferences", "Préférences"),
    openMenu: w("Open the menu", "Ouvrir le menu"),
    closeMenu: w("Close the menu", "Fermer le menu"),
    home: w("{brand} home", "Accueil {brand}"),
    tryIt: w("Try {brand}", "Essayer {brand}"),
    openApp: w("Open the app", "Ouvrir l'app"),
    repo: w("{brand} web client on GitHub", "Client web {brand} sur GitHub"),
  },
  hero: {
    title: w("An AI platform that runs on your machines.", "Une plateforme d'IA qui tourne sur vos machines."),
    lead: w(
      "Chat, documents, images and video, with the models you choose. With a local model, the work is done on your machine; a hosted model is used only if you connect it, and your requests then go to its provider.",
      "Clavardage, documents, images et vidéo, avec les modèles que vous choisissez. Avec un modèle local, le travail se fait sur votre machine ; un modèle hébergé ne sert que si vous le branchez, et vos requêtes partent alors chez son fournisseur.",
    ),
    secondary: w("See it run", "Le voir tourner"),
    visualLabel: w("{brand} · chat", "{brand} · chat"),
  },
  status: {
    development: w(
      "In development: accounts are not open and nothing is for sale.",
      "En développement : les comptes ne sont pas ouverts et rien n'est en vente.",
    ),
  },
  figures: {
    runs: w(
      "runs measured on real hardware, each with its record",
      "runs mesurés sur du vrai matériel, chacun avec sa fiche",
    ),
    models: w("models measured, on a Mac or hosted", "modèles mesurés, sur un Mac ou hébergés"),
    telemetry: w("telemetry calls: the app makes none", "appel de télémétrie : l'app n'en fait aucun"),
  },
  features: {
    eyebrow: w("What it does", "Ce qu'il fait"),
    title: w("Three things, done on your side.", "Trois choses, faites de votre côté."),
    lead: w(
      "The recordings come from our tests, on the phone app, with demo data.",
      "Les enregistrements viennent de nos tests, sur l'app mobile, avec des données de démonstration.",
    ),
    items: {
      studios: {
        title: w("A sentence in, a result out", "Une phrase, un résultat"),
        text: w(
          "A Studio turns a sentence or a file into an image, a video or a checked document. You follow the run, then open the result.",
          "Un Studio change une phrase ou un fichier en image, en vidéo ou en document vérifié. Vous suivez l'exécution, puis vous ouvrez le résultat.",
        ),
      },
      records: {
        title: w("Every result keeps its run", "Chaque résultat garde son exécution"),
        text: w(
          "The output, its steps, its input and its run id stay together, so a result can be checked again. The jobs list shows the state of each one.",
          "La sortie, ses étapes, son entrée et son identifiant d'exécution restent ensemble : un résultat peut être revérifié. La liste des tâches montre l'état de chacune.",
        ),
      },
      home: {
        title: w("Chat and agents that ask first", "Un chat et des agents qui demandent d'abord"),
        text: w(
          "Models run on your machines; a hosted one only if you connect it. An agent shows its plan and acts once you approve it.",
          "Les modèles tournent sur vos machines ; un modèle hébergé seulement si vous en branchez un. Un agent montre son plan et n'agit qu'une fois approuvé.",
        ),
      },
    },
  },
  capabilities: {
    eyebrow: w("What {brand} does", "Ce que {brand} sait faire"),
    title: w("What {brand} does, in plain words", "Ce que {brand} sait faire, en mots simples"),
    lead: w(
      "The industry term sits beside each one, so you can recognise it.",
      "Le terme du métier figure à côté de chaque point, pour le reconnaître.",
    ),
    soon: w("In development", "En développement"),
    items: {
      documents: {
        tag: w("RAG", "RAG"),
        title: w("Answers grounded in your own documents", "Des réponses fondées sur vos propres documents"),
        text: w(
          "The model looks in your documents before it answers. The search engine exists; sending your files from the app is in development.",
          "Le modèle cherche dans vos documents avant de répondre. Le moteur de recherche existe ; l'envoi de vos fichiers depuis l'app est en développement.",
        ),
      },
      models: {
        tag: w("LLM", "LLM"),
        title: w("The language models you choose", "Les modèles de langage que vous choisissez"),
        text: w(
          "On your Mac or hosted, your pick. A hosted model is used only if you connect it, and then your requests go to its provider.",
          "Sur votre Mac ou hébergés, à vous de choisir. Un modèle hébergé n'est utilisé que si vous le branchez ; vos requêtes partent alors chez son fournisseur.",
        ),
      },
      tools: {
        tag: w("MCP", "MCP"),
        title: w("Plugs into your tools through an open standard", "Se branche à vos outils par un standard ouvert"),
        text: w(
          "Register your own MCP servers from the command line or the API; what they know joins the conversation.",
          "Enregistrez vos propres serveurs MCP en ligne de commande ou par l'API ; ce qu'ils savent rejoint la conversation.",
        ),
      },
      agents: {
        tag: w("Agents · tools", "Agents · outils"),
        title: w("Agents that use tools and ask first", "Des agents qui utilisent des outils et demandent d'abord"),
        text: w(
          "An agent shows its plan and acts only once you approve it.",
          "Un agent montre son plan et n'agit qu'une fois approuvé.",
        ),
      },
      studios: {
        tag: w("Studios", "Studios"),
        title: w("Studios for images, video and documents", "Des Studios pour l'image, la vidéo et les documents"),
        text: w(
          "A sentence or a file becomes an image, a video or a checked document, with its cost shown before it runs.",
          "Une phrase ou un fichier devient une image, une vidéo ou un document vérifié, avec son coût affiché avant l'exécution.",
        ),
      },
      runs: {
        tag: w("Run id", "Run id"),
        title: w("Every result traced to its run", "Chaque résultat relié à son exécution"),
        text: w(
          "The output, its steps and its input stay together under one run id, so it can be checked again.",
          "La sortie, ses étapes et son entrée restent ensemble sous un même identifiant de run, pour pouvoir tout revérifier.",
        ),
      },
    },
  },
  film: {
    eyebrow: w("Made with {brand}", "Fait avec {brand}"),
    title: w("This film was made with {brand}.", "Ce film a été fait avec {brand}."),
    lead: w(
      "Key frames from the image Studio, shots from the video Studio, music from an audio model. Here is the film, and the records behind it.",
      "Les images clés viennent du Studio d'images, les plans du Studio vidéo, la musique d'un modèle audio. Voici le film, et les relevés qui le soutiennent.",
    ),
    label: w(
      "A short film made with {brand}: music, no speech.",
      "Un court film fait avec {brand} : de la musique, pas de parole.",
    ),
    factsTitle: w("What it took", "Ce qu'il a fallu"),
    models: w("Models", "Modèles"),
    gpu: w("GPU", "GPU"),
    runs: w("Video runs kept", "Runs vidéo retenus"),
    cost: w("GPU cost of those runs", "Coût GPU de ces runs"),
    costNote: w(
      "The sum of the records of the shots kept in the film. Takes that were dropped, key frames and music are not counted here.",
      "La somme des relevés des plans gardés dans le film. Les prises écartées, les images clés et la musique ne sont pas comptées ici.",
    ),
    roles: {
      keyframes: w("key frames", "images clés"),
      shots: w("shots", "plans"),
      music: w("music", "musique"),
    },
    runIds: w("Run ids of the shots, in cut order", "Identifiants de run des plans, dans l'ordre du montage"),
    outputs: {
      heading: w(
        "Outputs of the film, as the Studios made them",
        "Sorties du film, telles que les Studios les ont faites",
      ),
      imageCaption: w("Image Studio · {model}", "Studio d'images · {model}"),
      videoCaption: w("Video Studio · {model} · run {run}", "Studio vidéo · {model} · run {run}"),
      duckKeyframe: w(
        "Key frame: a mallard on a workbench in a basement by a canal at dusk, beside an open laptop.",
        "Image clé : un colvert sur un établi, dans un sous-sol près d'un canal au crépuscule, à côté d'un portable ouvert.",
      ),
      duckShot: w("Shot: the mallard turns and takes off.", "Plan : le colvert se retourne et s'envole."),
      inkKeyframe: w(
        "Key frame: a puddle on a wet cobbled street, mirroring the grey sky.",
        "Image clé : une flaque sur une rue pavée mouillée, qui reflète le ciel gris.",
      ),
      inkShot: w(
        "Shot: a drop of blue ink falls into the puddle and ripples spread.",
        "Plan : une goutte d'encre bleue tombe dans la flaque et des ondes s'étendent.",
      ),
    },
  },
  records: {
    eyebrow: w("Measured", "Mesuré"),
    title: w("One prompt, three models, three run records.", "Un prompt, trois modèles, trois fiches de run."),
    lead: w(
      "Times, costs and run ids come from the platform's own records — they are not typed.",
      "Temps, coûts et identifiants de run viennent des relevés de la plateforme — ils ne sont pas saisis à la main.",
    ),
    more: w("See every measured run", "Voir tous les runs mesurés"),
    measuredOn: w(
      "Measured on {day}: {ok} runs finished, {models} models.",
      "Mesuré le {day} : {ok} runs terminés, {models} modèles.",
    ),
    runLabel: w("Run", "Run"),
    prompt: w("A winter night in Montréal", "Une nuit d'hiver à Montréal"),
    alt: w("{title}, by {model}", "{title}, par {model}"),
    where: w("Ran on", "Exécuté sur"),
    onMac: w("One Mac (Metal) — nothing left the machine", "Un seul Mac (Metal) — rien n'a quitté la machine"),
    onHosted: w("DigitalOcean serverless inference", "Inférence serverless de DigitalOcean"),
    noCost: w("None — your own machine", "Aucun — votre propre machine"),
    imageCost: w("${cost} an image", "{cost} $ l'image"),
    time: w("Time", "Durée"),
    timeValue: w("{seconds} s, from the command to the file", "{seconds} s, de la commande au fichier"),
    cost: w("Provider price", "Prix du fournisseur"),
  },
  phone: {
    eyebrow: w("On your phone", "Sur votre téléphone"),
    title: w("The same work, from your phone.", "Le même travail, depuis votre téléphone."),
    lead: w(
      "Sign in, describe what you need, follow the run, open the result. The phone app is the same {brand}, recorded here on a real device.",
      "Connectez-vous, décrivez ce qu'il vous faut, suivez le run, ouvrez le résultat. L'app mobile est le même {brand}, enregistrée ici sur un vrai appareil.",
    ),
  },
  cta: {
    title: w("See what runs today.", "Voyez ce qui tourne aujourd'hui."),
    body: w(
      "{brand} is in development: the documentation describes what runs today, and nothing more. For a custom project, write to us; a person answers.",
      "{brand} est en développement : la documentation décrit ce qui tourne aujourd'hui, rien de plus. Pour un projet sur mesure, écrivez-nous ; une personne vous répond.",
    ),
    primary: w("Create your workspace", "Créer mon espace"),
    quickstart: w("Run it in five minutes", "L'essayer en cinq minutes"),
  },
  closed: {
    title: w(
      "{brand} is in development — sign-in is closed on this site",
      "{brand} est en développement — la connexion est fermée sur ce site",
    ),
    body: w(
      "This is the public development site: you can read how {brand} works, watch it run and browse the documentation. Accounts are not open yet.",
      "Ceci est le site public de développement : vous pouvez lire comment {brand} fonctionne, le voir tourner et parcourir la documentation. Les comptes ne sont pas encore ouverts.",
    ),
    docs: w("Read the documentation", "Lire la documentation"),
    contact: w("Talk to us", "Nous parler"),
    home: w("Back to the home page", "Retour à l'accueil"),
    heroPrimary: w("Read the documentation", "Lire la documentation"),
    signInBanner: w(
      "Sign-in opens when {brand}'s dev services run.",
      "La connexion s'ouvre quand les services de développement de {brand} tournent.",
    ),
  },
  stage: {
    title: w("Development version", "Version de développement"),
    message: w(
      "Data can be reset at any time. This is not a commercial offer yet.",
      "Les données peuvent être réinitialisées à tout moment. Ce n'est pas encore une offre commerciale.",
    ),
  },
  footer: {
    privacy: w("Privacy", "Confidentialité"),
    terms: w("Terms", "Conditions"),
    contact: w("Contact", "Contact"),
    openSource: w("Source code", "Code source"),
    pricing: w("Pricing", "Tarifs"),
    license: w(`Licence: ${LICENCE_ID}`, `Licence ${LICENCE_ID}`),
    by: w("A Krizaka product", "Un produit Krizaka"),
    product: w("Product", "Produit"),
    docs: w("Documentation", "Documentation"),
    api: w("API reference", "Référence de l'API"),
    cli: w("CLI", "CLI"),
    legal: w("Legal", "Légal"),
    label: w("Footer", "Pied de page"),
    home: w("Home", "Accueil"),
    code: w("Code", "Code"),
    licences: w("Licences, folder by folder", "Licences, dossier par dossier"),
    publisher: w("Publisher", "Éditeur"),
    publisherSite: w("Krizaka", "Krizaka"),
  },
} as const;

export const landing: Record<Locale, LandingDictionary> = {
  en: localise(WORDS, 0),
  fr: localise(WORDS, 1),
};
