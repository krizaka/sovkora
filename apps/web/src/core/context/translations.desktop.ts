/**
 * @file translations.desktop.ts
 * @description The words of /desktop (Sovkora for Mac), en and fr. What the app does is what `apps/desktop/README.md`
 * of krizaka/orazaka says the v0.1 build does, and the services are the ones `orazaka start` brings up — nothing more,
 * no figure that was not measured. The version, the download URL, the size and the SHA-256 are never written here:
 * they are read from the GitHub release at build time.
 */
import type { PhoneWords } from "./translations.landing.types";
import { videosWords, type VideosWords } from "./translations.appVideos";

type Locale = "en" | "fr";

interface Titled {
  title: string;
  body: string;
}

export interface DesktopDictionary {
  meta: { title: string; description: string };
  hero: { eyebrow: string; title: string; lead: string; captureAlt: string };
  /** The phone apps: a store button only when its URL is configured (`APP_STORE_IOS_URL`, `APP_STORE_ANDROID_URL`), else « soon ». */
  /** The phone clip of the Download page (`PhoneSection`). */
  phone: PhoneWords;
  /** The videos the web journeys recorded (`AppVideosSection`, krizaka/.github#315); `items` is keyed by `<platform>-<flow>` of the manifest. */
  videos: VideosWords;
  mobile: {
    title: string;
    soon: string;
    ios: { button: string; soon: string };
    android: { button: string; soon: string };
  };
  download: {
    button: string;
    /** `{size}` is the .dmg's size. */
    facts: string;
    testBuild: string;
    /** Shown instead of the facts while the Mac download is switched off (`APP_DOWNLOADS_MAC_ENABLED`). */
    disabled: string;
    version: string;
    verify: string;
    verifyBody: string;
    shaCopy: string;
    shaCopied: string;
    shaCopyFailed: string;
    unreadable: string;
    releases: string;
  };
  firstLaunch: {
    title: string;
    lead: string;
    steps: Titled[];
    damaged: { summary: string; body: string; terminal: string };
  };
  modes: { title: string; lead: string; items: Titled[] };
  services: {
    title: string;
    lead: string;
    head: { service: string; runs: string; does: string };
    rows: { name: string; runs: string; does: string }[];
  };
  trouble: { title: string; items: (Titled & { code?: string })[] };
  requirements: { title: string; items: string[] };
}

export const desktop: Record<Locale, DesktopDictionary> = {
  en: {
    meta: {
      title: "{brand} for Mac — {brand}",
      description:
        "Run {brand} on your Mac, or connect it to your workspace: a control panel in the menu bar, and access only to the folders you choose. Apple Silicon, macOS 14+.",
    },
    hero: {
      eyebrow: "{brand} for Mac",
      title: "{brand} on your Mac.",
      lead: "Run the whole engine on this Mac, or connect it to your workspace. A control panel in the menu bar — and {brand} sees only the folders you choose.",
      captureAlt: "The {brand} for Mac control panel: the state of the local engine, Start, Stop and Open workspace",
    },
    phone: {
      eyebrow: "{brand} on your phone",
      title: "And the same {brand} in your hand.",
      lead: "The phone app talks to the same platform as the Mac app and the web: a chat, the Studios, your jobs. Recorded on a real device.",
    },
    videos: videosWords("en"),
    mobile: {
      title: "On your phone",
      soon: "Coming to iPhone and Android",
      ios: { button: "Download on the App Store", soon: "iPhone: coming soon" },
      android: { button: "Get it on Google Play", soon: "Android: coming soon" },
    },
    download: {
      button: "Download for Mac",
      facts: "Apple Silicon · macOS 14+ · {size}",
      disabled: "In testing — not distributed yet.",
      testBuild: "A test build: not yet signed by Apple — the first launch takes one extra step.",
      version: "Version {version}",
      verify: "Verify the download",
      verifyBody: "The SHA-256 of the .dmg, published with the release. Compare it with `shasum -a 256 {brand}_*.dmg`:",
      shaCopy: "Copy",
      shaCopied: "Copied",
      shaCopyFailed: "Copy failed — select the hash by hand",
      unreadable: "The latest build could not be read from GitHub right now.",
      releases: "Open the releases page",
    },
    firstLaunch: {
      title: "First launch",
      lead: "The app is not signed by Apple yet, so macOS asks once.",
      steps: [
        { title: "Drag to Applications", body: "Open the .dmg and drag {brand} onto the Applications folder." },
        {
          title: "Right-click, then Open",
          body: "In Applications, right-click {brand} and choose Open — a plain double-click is blocked.",
        },
        { title: "Confirm Open", body: "macOS asks if you are sure: choose Open. It never asks again." },
      ],
      damaged: {
        summary: "If macOS says the app is damaged",
        body: "That is macOS's quarantine on an unsigned download, not a broken file. Remove it once, in a terminal:",
        terminal: "xattr -dr com.apple.quarantine /Applications/{brand}.app",
      },
    },
    modes: {
      title: "Two ways to use it",
      lead: "Switch in the app's settings; your folders stay yours in both.",
      items: [
        {
          title: "Connected to your workspace",
          body: "The app opens your hosted {brand} (www.orazaka.com by default, https only). You sign in on the {brand} page itself — the app never sees or stores your password.",
        },
        {
          title: "Everything on this Mac",
          body: "Start, stop and check the local engine from the panel. Models run on your Mac's own chip (Apple Silicon); nothing leaves the machine unless you connect a cloud model.",
        },
        {
          title: "Only the folders you choose",
          body: "Grant folders with the macOS picker, preview their files, drop files in, revoke any time. Your home folder, system folders and ~/.ssh can never be granted.",
        },
      ],
    },
    services: {
      title: "What runs when you start it",
      lead: "« Start » runs the {brand} command line on your Mac; the panel shows each service's state.",
      head: { service: "Service", runs: "Runs in", does: "What it does" },
      rows: [
        {
          name: "PostgreSQL",
          runs: "Docker",
          does: "Your data: conversations, jobs, settings, and the search index of your documents.",
        },
        { name: "Redis", runs: "Docker", does: "Short-lived cache and sessions." },
        {
          name: "RabbitMQ",
          runs: "Docker",
          does: "The queue that hands long jobs (images, video, voice) to the workers.",
        },
        { name: "Ollama", runs: "Your Mac", does: "The text models, on the Mac's own chip." },
        { name: "Image engine", runs: "Your Mac", does: "Image generation (stable-diffusion.cpp or LocalAI)." },
        { name: "Media worker", runs: "Your Mac", does: "Runs image, video and voice jobs from the queue." },
        {
          name: "Engine API",
          runs: "Your Mac",
          does: "The router every request goes through: security, rules, the right model.",
        },
        { name: "Workspace", runs: "Your Mac", does: "The web app, opened in the app's window (Open workspace)." },
      ],
    },
    trouble: {
      title: "If something goes wrong",
      items: [
        {
          title: "Run doctor",
          body: "In the panel, Run doctor checks your Mac (Node.js, the chip) and whether each service answers, then lists what to fix.",
          code: "orazaka doctor",
        },
        {
          title: "Read the logs",
          body: "The panel shows the live output; for one service, open its logs.",
          code: "orazaka logs --service router",
        },
        { title: "Docker is not running", body: "Start OrbStack, Colima or Docker Desktop, then Start again." },
        {
          title: "Node.js is missing",
          body: "This build needs Node.js 22 or later on the Mac.",
          code: "brew install node",
        },
      ],
    },
    requirements: {
      title: "What you need",
      items: [
        "A Mac with Apple Silicon, macOS 14 or later.",
        "Node.js 22 or later.",
        "For everything on this Mac: Docker (OrbStack, Colima or Docker Desktop).",
        "No Windows or Linux build yet: on those, run {brand} on a server (/docs/self-hosting).",
      ],
    },
  },
  fr: {
    meta: {
      title: "{brand} pour Mac — {brand}",
      description:
        "Faites tourner {brand} sur votre Mac, ou reliez-le à votre espace : un panneau dans la barre des menus, et l'accès aux seuls dossiers que vous choisissez. Apple Silicon, macOS 14+.",
    },
    hero: {
      eyebrow: "{brand} pour Mac",
      title: "{brand} sur votre Mac.",
      lead: "Faites tourner tout le moteur sur ce Mac, ou reliez-le à votre espace. Un panneau dans la barre des menus — et {brand} ne voit que les dossiers que vous choisissez.",
      captureAlt: "Le panneau de {brand} pour Mac : l'état du moteur local, Démarrer, Arrêter et Ouvrir l'espace",
    },
    phone: {
      eyebrow: "{brand} sur votre téléphone",
      title: "Et le même {brand} dans votre main.",
      lead: "L'app mobile parle à la même plateforme que l'app Mac et le web : un chat, les Studios, vos tâches. Enregistrée sur un vrai appareil.",
    },
    videos: videosWords("fr"),
    mobile: {
      title: "Sur votre téléphone",
      soon: "Bientôt sur iPhone et Android",
      ios: { button: "Télécharger sur l'App Store", soon: "iPhone : bientôt" },
      android: { button: "Obtenir sur Google Play", soon: "Android : bientôt" },
    },
    download: {
      button: "Télécharger pour Mac",
      facts: "Apple Silicon · macOS 14+ · {size}",
      disabled: "En test — pas encore distribuée.",
      testBuild: "Une version de test : pas encore signée par Apple — le premier lancement demande une étape de plus.",
      version: "Version {version}",
      verify: "Vérifier le téléchargement",
      verifyBody: "Le SHA-256 du .dmg, publié avec la version. Comparez-le avec `shasum -a 256 {brand}_*.dmg` :",
      shaCopy: "Copier",
      shaCopied: "Copié",
      shaCopyFailed: "Échec de la copie — sélectionnez l'empreinte à la main",
      unreadable: "La dernière version n'a pas pu être lue sur GitHub pour l'instant.",
      releases: "Ouvrir la page des versions",
    },
    firstLaunch: {
      title: "Premier lancement",
      lead: "L'app n'est pas encore signée par Apple : macOS demande une fois.",
      steps: [
        { title: "Glisser dans Applications", body: "Ouvrez le .dmg et glissez {brand} sur le dossier Applications." },
        {
          title: "Clic droit, puis Ouvrir",
          body: "Dans Applications, clic droit sur {brand}, puis Ouvrir — un simple double-clic est bloqué.",
        },
        {
          title: "Confirmer Ouvrir",
          body: "macOS demande si vous êtes sûr : choisissez Ouvrir. Il ne redemandera plus.",
        },
      ],
      damaged: {
        summary: "Si macOS dit que l'app est endommagée",
        body: "C'est la quarantaine de macOS sur un téléchargement non signé, pas un fichier abîmé. Retirez-la une fois, dans un terminal :",
        terminal: "xattr -dr com.apple.quarantine /Applications/{brand}.app",
      },
    },
    modes: {
      title: "Deux façons de l'utiliser",
      lead: "Changez dans les réglages de l'app ; vos dossiers restent à vous dans les deux cas.",
      items: [
        {
          title: "Relié à votre espace",
          body: "L'app ouvre votre {brand} hébergé (www.orazaka.com par défaut, https seulement). Vous vous connectez sur la page de {brand} elle-même — l'app ne voit ni ne garde votre mot de passe.",
        },
        {
          title: "Tout sur ce Mac",
          body: "Démarrez, arrêtez et vérifiez le moteur local depuis le panneau. Les modèles tournent sur la puce du Mac (Apple Silicon) ; rien ne sort de la machine, sauf si vous branchez un modèle cloud.",
        },
        {
          title: "Seulement les dossiers choisis",
          body: "Accordez des dossiers avec le sélecteur de macOS, prévisualisez leurs fichiers, déposez-en, retirez l'accès quand vous voulez. Votre dossier personnel, les dossiers système et ~/.ssh ne peuvent jamais être accordés.",
        },
      ],
    },
    services: {
      title: "Ce qui tourne quand vous démarrez",
      lead: "« Démarrer » lance la ligne de commande {brand} sur votre Mac ; le panneau montre l'état de chaque service.",
      head: { service: "Service", runs: "Tourne dans", does: "Ce qu'il fait" },
      rows: [
        {
          name: "PostgreSQL",
          runs: "Docker",
          does: "Vos données : conversations, tâches, réglages, et l'index de recherche de vos documents.",
        },
        { name: "Redis", runs: "Docker", does: "Cache court et sessions." },
        {
          name: "RabbitMQ",
          runs: "Docker",
          does: "La file qui confie les longues tâches (images, vidéo, voix) aux workers.",
        },
        { name: "Ollama", runs: "Votre Mac", does: "Les modèles de texte, sur la puce du Mac." },
        {
          name: "Moteur d'images",
          runs: "Votre Mac",
          does: "La génération d'images (stable-diffusion.cpp ou LocalAI).",
        },
        {
          name: "Worker média",
          runs: "Votre Mac",
          does: "Exécute les tâches d'image, de vidéo et de voix de la file.",
        },
        {
          name: "API du moteur",
          runs: "Votre Mac",
          does: "Le routeur par lequel passe chaque requête : sécurité, règles, le bon modèle.",
        },
        {
          name: "Espace de travail",
          runs: "Votre Mac",
          does: "L'app web, ouverte dans la fenêtre de l'app (Ouvrir l'espace).",
        },
      ],
    },
    trouble: {
      title: "Si quelque chose ne va pas",
      items: [
        {
          title: "Lancer le diagnostic",
          body: "Dans le panneau, le diagnostic vérifie votre Mac (Node.js, la puce) et si chaque service répond, puis liste quoi corriger.",
          code: "orazaka doctor",
        },
        {
          title: "Lire les journaux",
          body: "Le panneau montre la sortie en direct ; pour un service, ouvrez ses journaux.",
          code: "orazaka logs --service router",
        },
        { title: "Docker ne tourne pas", body: "Lancez OrbStack, Colima ou Docker Desktop, puis Démarrer à nouveau." },
        {
          title: "Node.js manque",
          body: "Cette version demande Node.js 22 ou plus sur le Mac.",
          code: "brew install node",
        },
      ],
    },
    requirements: {
      title: "Ce qu'il vous faut",
      items: [
        "Un Mac Apple Silicon, macOS 14 ou plus.",
        "Node.js 22 ou plus.",
        "Pour tout faire sur ce Mac : Docker (OrbStack, Colima ou Docker Desktop).",
        "Pas encore de version Windows ou Linux : sur ces systèmes, faites tourner {brand} sur un serveur (/docs/self-hosting).",
      ],
    },
  },
};
