/**
 * @file translations.automation.ts
 * @description The words of the automation page and the accessible names of icon-only controls (en, fr).
 */

import type { A11yDictionary, AutomationDictionary } from "./translations.types";

export const automation: Record<"en" | "fr", AutomationDictionary> = {
  en: {
    connectorsTitle: "Integration connectors",
    connectorsSubtitle: "Connect your tools to run approved automations.",
    status: { connected: "Connected", disconnected: "Inactive", pending: "Pending" },
    connect: "Connect",
    disconnect: "Disconnect",
    toggle: "Connect or disconnect {name}",
    configure: "Configure {name}",
    apiKey: "API key",
    apiKeyPlaceholder: "Paste your API key",
    saveCredentials: "Save credentials",
    connectors: {
      jira: "Create tickets, change their status and index their comments.",
      whatsapp: "Send notifications through the WhatsApp Cloud API.",
      messenger: "Deliver chat summaries to Messenger conversations.",
      slack: "Post summaries and alerts to Slack channels and threads.",
      "cli-agent": "Run automations on your own machine through a reverse tunnel.",
    },
  },
  fr: {
    connectorsTitle: "Connecteurs d'intégration",
    connectorsSubtitle: "Connectez vos outils pour exécuter des automatisations approuvées.",
    status: { connected: "Connecté", disconnected: "Inactif", pending: "En attente" },
    connect: "Connecter",
    disconnect: "Déconnecter",
    toggle: "Connecter ou déconnecter {name}",
    configure: "Configurer {name}",
    apiKey: "Clé d'API",
    apiKeyPlaceholder: "Collez votre clé d'API",
    saveCredentials: "Enregistrer les identifiants",
    connectors: {
      jira: "Créer des tickets, changer leur statut et indexer leurs commentaires.",
      whatsapp: "Envoyer des notifications via l'API WhatsApp Cloud.",
      messenger: "Livrer les résumés de conversation dans Messenger.",
      slack: "Publier résumés et alertes dans les canaux et fils Slack.",
      "cli-agent": "Exécuter des automatisations sur votre propre machine via un tunnel inversé.",
    },
  },
};

export const a11y: Record<"en" | "fr", A11yDictionary> = {
  en: {
    closeDialog: "Close the dialog",
    closeDetails: "Close the details",
    closeHistory: "Close the conversations",
    help: "Help",
    copy: "Copy",
    copyJobId: "Copy the full job ID",
    socialLogin: "Sign in with another account",
    socialRegister: "Sign up with another account",
    productOverview: "Product overview",
    profileSections: "Profile sections",
    scrollToBottom: "Scroll to the latest message",
    toggleReasoning: "Show or hide the reasoning",
    attachFile: "Attach a file",
    showPassword: "Show password",
    hidePassword: "Hide password",
    openMenu: "Open the menu",
    closeMenu: "Close the menu",
    deleteConversation: "Delete this conversation",
    renameConversation: "Rename this conversation",
    openConversations: "Open your conversations",
  },
  fr: {
    closeDialog: "Fermer la fenêtre",
    closeDetails: "Fermer le détail",
    closeHistory: "Fermer les conversations",
    help: "Aide",
    copy: "Copier",
    copyJobId: "Copier l'identifiant complet de la tâche",
    socialLogin: "Se connecter avec un autre compte",
    socialRegister: "S'inscrire avec un autre compte",
    productOverview: "Aperçu du produit",
    profileSections: "Sections du profil",
    scrollToBottom: "Aller au dernier message",
    toggleReasoning: "Afficher ou masquer le raisonnement",
    attachFile: "Joindre un fichier",
    showPassword: "Afficher le mot de passe",
    hidePassword: "Masquer le mot de passe",
    openMenu: "Ouvrir le menu",
    closeMenu: "Fermer le menu",
    deleteConversation: "Supprimer cette conversation",
    renameConversation: "Renommer cette conversation",
    openConversations: "Ouvrir vos conversations",
  },
};
