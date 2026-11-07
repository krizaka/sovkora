/**
 * @file translations.notFound.ts
 * @description The words of the 404 page (en, fr): an address that does not exist, and the ways back.
 */
export interface NotFoundDictionary {
  title: string;
  text: string;
  label: string;
  home: string;
  docs: string;
  signIn: string;
}

export const notFound = {
  en: {
    title: "This page took another road",
    text: "There is nothing at this address. Go back to the home page, read the documentation, or sign in to your workspace.",
    label: "Where to go",
    home: "Home",
    docs: "Documentation",
    signIn: "Sign in",
  },
  fr: {
    title: "Cette page a pris un autre chemin",
    text: "Il n'y a rien à cette adresse. Revenez à l'accueil, lisez la documentation ou connectez-vous à votre espace.",
    label: "Où aller",
    home: "Accueil",
    docs: "Documentation",
    signIn: "Se connecter",
  },
} as const satisfies Record<"en" | "fr", NotFoundDictionary>;
