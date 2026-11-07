import { registerRootComponent } from "expo";
import App from "./App";

/**
 * The entry Expo starts (`main` in package.json). `registerRootComponent` registers the root under the name the native
 * app asks for ("main") and sets up the environment; without it the app launches to "main has not been registered".
 */
registerRootComponent(App);
