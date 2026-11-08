/**
 * @file translations.landing.types.ts
 * @description Shape of the public pages copy — the home page (`t.landing`). No import, so the dictionary type and the copy can both depend on it without a cycle.
 */

/** One titled paragraph (a benefit, a step, a proof). */
export interface LandingItem {
  title: string;
  body: string;
}

/** The words around a phone clip (`PhoneSection`), shared by the home and the Download page. */
export interface PhoneWords {
  eyebrow: string;
  title: string;
  lead: string;
}

/** Copy of the public home page. */
export interface LandingDictionary {
  nav: {
    how: string;
    signIn: string;
    docs: string;
    product: string;
    download: string;
    pricing: string;
    openSource: string;
    /** One short line (≤ 45 characters: the menu shows one line) under each entry of the Product menu. */
    menu: Record<"how" | "download", string>;
    langLabel: string;
    themeLabel: string;
    preferences: string;
    openMenu: string;
    closeMenu: string;
    home: string;
    tryIt: string;
    openApp: string;
    repo: string;
  };
  /** The first screen: one statement, one line, the real product beside it. */
  hero: { title: string; lead: string; secondary: string; visualLabel: string };
  /** The honest status line, the same one Yomizaka and Akinaza say: shown under the hero's actions and on the sign-in. */
  status: { development: string };
  /** The figures under the hero, counted from the published measured runs. */
  figures: { runs: string; models: string; telemetry: string };
  features: {
    eyebrow: string;
    title: string;
    lead: string;
    items: Record<"studios" | "records" | "home", { title: string; text: string }>;
  };
  /** What it does in plain words, each with the industry term as a quiet tag (recognition and search). */
  capabilities: {
    eyebrow: string;
    title: string;
    lead: string;
    /** The label of an item that is not shipped yet. */
    soon: string;
    items: Record<
      "documents" | "models" | "tools" | "agents" | "studios" | "runs",
      { tag: string; title: string; text: string }
    >;
  };
  /** « Made with Sovkora »: the home film, the facts read from its run records, and the outputs shown on the Studios card. */
  film: {
    eyebrow: string;
    title: string;
    lead: string;
    label: string;
    factsTitle: string;
    models: string;
    gpu: string;
    runs: string;
    cost: string;
    costNote: string;
    roles: Record<"keyframes" | "shots" | "music", string>;
    runIds: string;
    outputs: {
      heading: string;
      imageCaption: string;
      videoCaption: string;
      duckKeyframe: string;
      duckShot: string;
      inkKeyframe: string;
      inkShot: string;
    };
  };
  records: {
    eyebrow: string;
    title: string;
    lead: string;
    more: string;
    measuredOn: string;
    runLabel: string;
    prompt: string;
    alt: string;
    where: string;
    onMac: string;
    onHosted: string;
    noCost: string;
    imageCost: string;
    time: string;
    timeValue: string;
    cost: string;
  };
  phone: PhoneWords;
  cta: { title: string; body: string; primary: string; quickstart: string };
  closed: {
    title: string;
    body: string;
    docs: string;
    contact: string;
    home: string;
    heroPrimary: string;
    signInBanner: string;
  };
  stage: {
    title: string;
    message: string;
    /** « code name: {code} »: the dev-deployment line under the commercial name. */
    codeName: string;
  };
  footer: {
    privacy: string;
    terms: string;
    contact: string;
    openSource: string;
    pricing: string;
    license: string;
    by: string;
    product: string;
    docs: string;
    api: string;
    cli: string;
    legal: string;
  };
}
