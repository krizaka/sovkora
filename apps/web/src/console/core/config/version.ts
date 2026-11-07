/** The version of the app shown in the footer: `NEXT_PUBLIC_APP_VERSION`, set at build by next.config.ts (app-version.ts). */
export const APP_VERSION: string = process.env.NEXT_PUBLIC_APP_VERSION ?? "dev";

/** Where the console runs (`dev`, `staging`, `production`): `NEXT_PUBLIC_APP_ENV`, set at build by next.config.ts. */
export const APP_ENV: string = process.env.NEXT_PUBLIC_APP_ENV ?? "dev";
