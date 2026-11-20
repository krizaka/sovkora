/**
 * @file translations.routeError.ts
 * @description The words of the error boundary of the signed-in console (en, fr): a page that failed to render, and the way to retry.
 */
export interface RouteErrorDictionary {
  title: string;
  text: string;
  retry: string;
}

export const routeError = {
  en: {
    title: "This page could not be shown",
    text: "Something went wrong while displaying this page. Your work is safe. Try again, or use the menu to go elsewhere.",
    retry: "Try again",
  },
  fr: {
    title: "Cette page n'a pas pu s'afficher",
    text: "Un problème est survenu à l'affichage de cette page. Votre travail est conservé. Réessayez, ou utilisez le menu pour aller ailleurs.",
    retry: "Réessayer",
  },
} as const satisfies Record<"en" | "fr", RouteErrorDictionary>;
