/**
 * @file translations.types.ts
 * @description Shared type definitions for the internationalization dictionary.
 */

import type { ClipsDictionary } from "./translations.clips";
import type { NotFoundDictionary } from "./translations.notFound";
import type { LandingDictionary } from "./translations.landing.types";
import type { InsightsDictionary } from "./translations.insights";
import type { JobsTableDictionary } from "./translations.jobsTable.types";
import type { WebhooksDictionary } from "./translations.webhooks.types";
import type { AutomationsDictionary } from "./translations.automations.types";
import type { RunProgressDictionary } from "./translations.runProgress.types";
import type { ControlDictionary } from "./translations.control";
import type { CatalogueDictionary, PlanDictionary } from "./translations.catalogue";
import type { ConsentDictionary } from "./translations.consent";
import type { MobileDictionary } from "./translations.mobile";
import type { DesktopDictionary } from "./translations.desktop";
import type { PricingDictionary } from "./translations.pricing";
import type { OpenSourceDictionary } from "./translations.openSource";
import type { DocsDictionary } from "./translations.docs";

export type Locale = "en" | "fr";

export interface TranslationDictionary {
  sidebar: {
    dashboard: string;
    chatSessions: string;
    studios: string;
    packs: string;
    logout: string;
    closeMenu: string;
    memoryBlocks: string;
    playground: string;
    jobsHistory: string;
    adminPanel: string;
    settings: string;
    navigation: string;

    // Playground Usecase Categories
    videoCategory: string;
    audioCategory: string;
    textCategory: string;
    imageCategory: string;
    codeCategory: string;
    speechCategory: string;
    visionCategory: string;

    // Playground Usecases
    generateVideo: string;
    analyzeVideo: string;
    analyzeAudio: string;
    textChat: string;
    generateImage: string;
    featureToCode: string;
    speechSynthesis: string;
    visionAnalysis: string;
  };
  playgroundUsecases: {
    emptyTitle: string;
    emptySubtitle: string;
    emptyContent: string;
  };
  /** The signed-in app's sections (core/config/app-sections.ts) and the user menu — the same words on web, mobile and Mac. */
  appNav: Record<
    | "label"
    | "home"
    | "chat"
    | "studios"
    | "jobs"
    | "packs"
    | "usage"
    | "settings"
    | "admin"
    | "account"
    | "language"
    | "signOut",
    string
  >;
  header: {
    profile: string;
    logout: string;
    /** The theme button's name, one per current mode (it cycles dark → light → system). */
    themeDark: string;
    themeLight: string;
    themeSystem: string;
    openSidebar: string;
    changeLanguage: string;
    closeLanguageMenu: string;
  };
  dashboard: {
    welcome: string;
    /** Time-of-day greeting; `{name}` is the signed-in user's first name. */
    greeting: { morning: string; afternoon: string; evening: string };
    overview: string;
    /** Status chip beside the greeting — the shared sovereignty motif. */
    sovereignBadge: string;
    conversations: string;
    conversationsDesc: string;
    credits: string;
    creditsDesc: string;
    studiosInstalled: string;
    studiosInstalledDesc: string;
    activeSessions: string;
    tokensUsed: string;
    memoryNodes: string;
    runningParallel: string;
    estimatedCumulative: string;
    contextSaved: string;
    quickActions: string;
    startNewChat: string;
    startNewChatDesc: string;
    manageProfile: string;
    manageProfileDesc: string;
    configureSettings: string;
    configureSettingsDesc: string;
    recentActivity: string;
    noRecentSessions: string;
    resumeSession: string;
    lastActive: string;
    loading: string;
  };
  chat: {
    sessionTitle: string;
    id: string;
    typeMessage: string;
    send: string;
    sending: string;
    uploadingFile: string;
    typing: string;
    loadingMessages: string;
    noActiveConversation: string;
    /** The caption of a tool's metrics card in the conversation. */
    toolMetrics: string;
    memoryBlocks: string;
    newBlock: string;
    ai: string;
    user: string;
    connectionError: string;
    promptRequired: string;
    addCapability: string;
    modeChat: string;
    clear: string;
    startConversation: string;
    startConversationDesc: string;
    localBadge: string;
    suggestionImage: string;
    suggestionCode: string;
    suggestionAsk: string;
    newMessages: string;
    statusRoutedTo: string;
    statusCredits: string;
    composerBusy: string;
    copyMessage: string;
    copiedMessage: string;
    deleteConfirm: string;
    capGeneration: string;
    capAnalysis: string;
    optModel: string;
    optAuto: string;
    optVoice: string;
    optSize: string;
    optDuration: string;
    attachToAnalyze: string;
    attachReferenceImage: string;
    generating: string;
    viewTask: string;
    awaitingInput: string;
    /** A model's answer drawn as an interface (ADR-082). */
    genui: { surface: string; loading: string; noData: string };
  };
  profile: {
    title: string;
    subtitle: string;
    accountDetails: string;
    detailsDesc: string;
    userId: string;
    subTier: string;
    username: string;
    email: string;
    assignedAuth: string;
    /** A role's name for people, by authority (`ROLE_USER`); an unknown authority shows as is. */
    roles: Record<string, string>;
    noAuth: string;
    prefMetadata: string;
    prefDesc: string;
    enterpriseTier: string;
    premiumTier: string;
    freeTier: string;
    failedLoad: string;
    unauthError: string;
    tabs: {
      account: string;
      appearance: string;
      workspace: string;
      integrations: string;
      apiKeys: string;
      webhooks: string;
    };
    pageTitle: string;
    pageDesc: string;
    navLabel: string;
    accountDesc: string;
    roleHelp: string;
    planLabel: string;
    planHelp: string;
    planLink: string;
    advancedTitle: string;
    advancedDesc: string;
    accountIdHelp: string;
    dangerTitle: string;
    dangerDesc: string;
    signOut: string;
    signOutHelp: string;
    rowTheme: string;
    rowAccent: string;
    rowAccentHelp: string;
    rowDensity: string;
    rowDensityHelp: string;
    rowLanguage: string;
    rowLanguageHelp: string;
    rowAppName: string;
    rowAppNameHelp: string;
    rowTagline: string;
    rowTaglineHelp: string;
    rowTenant: string;
    rowTenantHelp: string;
    rowPersona: string;
    rowPersonaHelp: string;
    showPreferences: string;
    hidePreferences: string;
    appearanceTitle: string;
    appearanceDesc: string;
    workspaceTitle: string;
    workspaceDesc: string;
    integrationsTitle: string;
    integrationsDesc: string;
    unsavedChanges: string;
    saveChanges: string;
    discard: string;
  };
  settings: {
    title: string;
    description: string;
    language: string;
    english: string;
    french: string;
    aiPersona: string;
    standardPersona: string;
    concisePersona: string;
    creativePersona: string;
    tenantBranding: string;
    appName: string;
    appNamePlaceholder: string;
    tagline: string;
    taglinePlaceholder: string;
    tenantId: string;
    tenantIdPlaceholder: string;
    colorAccent: string;
    zincAccent: string;
    roseAccent: string;
    emeraldAccent: string;
    amberAccent: string;
    layoutScale: string;
    standardLayout: string;
    compactLayout: string;
    themeMode: string;
    themeLight: string;
    themeDark: string;
    themeCustom: string;
    themeCyberpunk: string;
    themeSolarized: string;
    themeElectric: string;
    themeSystem: string;
    themeSystemDesc: string;
    themeLightDesc: string;
    themeDarkDesc: string;
    themeCustomDesc: string;
    themeCyberpunkDesc: string;
    themeSolarizedDesc: string;
    themeElectricDesc: string;
    themeApplied: string;
    themeClickToApply: string;
    themeChangesInstant: string;
    saveTheme: string;
    saving: string;
    credentialsTitle: string;
    credentialsDesc: string;
    geminiKey: string;
    anthropicKey: string;
    openaiKey: string;
    credentialsPlaceholder: string;
    saveCredentials: string;
    credentialsSaved: string;
    mcpServersTitle: string;
    mcpServersDesc: string;
    mcpLabel: string;
    mcpLabelPlaceholder: string;
    mcpTransportType: string;
    mcpUrl: string;
    mcpUrlPlaceholder: string;
    mcpAuthToken: string;
    mcpAuthTokenPlaceholder: string;
    mcpCommand: string;
    mcpCommandPlaceholder: string;
    mcpArgs: string;
    mcpArgsPlaceholder: string;
    mcpRegister: string;
    mcpDelete: string;
    mcpNoServers: string;
    mcpLoading: string;
    mcpSuccess: string;
    mcpError: string;
    mcpDeleteSuccess: string;
    mcpDeleteError: string;
    mcpRemoteLabel: string;
    mcpLocalLabel: string;
  };
  jobs: {
    title: string;
    subtitle: string;
    searchPlaceholder: string;
    statusAll: string;
    statusActive: string;
    statusCompleted: string;
    statusFailed: string;
    colId: string;
    colType: string;
    colStatus: string;
    colCreated: string;
    colDuration: string;
    colActions: string;
    payloadModalTitle: string;
    resultModalTitle: string;
    noJobs: string;
    errorDetails: string;
    loading: string;
    previous: string;
    next: string;
    copyError: string;
    refresh: string;
    kpiTotalJobs: string;
    kpiSuccessRate: string;
    kpiModelsUsed: string;
    kpiCapabilities: string;
    kpiTotalInference: string;
    paginationShowing: string;
    paginationOf: string;
    paginationTasks: string;
    perPage: string;
    running: string;
    copied: string;
    viewPayload: string;
    viewResult: string;
    viewErrorLogs: string;
  };
  providers: {
    title: string;
    subtitle: string;
    connected: string;
    notConfigured: string;
    apiKey: string;
    testConnection: string;
    testing: string;
    testSuccess: string;
    testFailed: string;
    enable: string;
    disable: string;
    addCustom: string;
    customUrl: string;
    deleteProvider: string;
    confirmDelete: string;
    keyPlaceholder: string;
    keySaved: string;
    keyReveal: string;
    keyHide: string;
    loading: string;
    addConnection: string;
    editConnection: string;
    modalSubtitle: string;
    selectProvider: string;
    editKeyHint: string;
    saveError: string;
    emptyTitle: string;
    emptyDesc: string;
    emptyCta: string;
    allConfigured: string;
    cancel: string;
    update: string;
  };
  apiKeys: {
    title: string;
    subtitle: string;
    create: string;
    loading: string;
    emptyTitle: string;
    emptyDesc: string;
    emptyCta: string;
    nameLabel: string;
    namePlaceholder: string;
    createCta: string;
    cancel: string;
    createError: string;
    limitReached: string;
    limitCaption: string;
    created: string;
    lastUsed: string;
    neverUsed: string;
    revoke: string;
    confirmDelete: string;
    revealTitle: string;
    revealDesc: string;
    copy: string;
    copied: string;
    done: string;
  };
  playground: {
    videoAnalysis: string;
    videoAnalysisDesc: string;
    selectVideo: string;
    uploadingVideo: string;
    processingVideo: string;
    analysisComplete: string;
    keyframes: string;
    transcript: string;
    analyzeAnother: string;
    tryAgain: string;
    onlyMp4MovSupported: string;
    fileLimitExceeded: string;
    locked: string;
    enterValue: string;
    runningIngestion: string;
    executeIngestion: string;
    executionFailed: string;
    serviceOffline: string;
    serviceOfflineHint: string;
    synthesizeSpeech: string;
    ttsUnsupportedWarning: string;
    outputResult: string;
    gatewayRestriction: string;
    speechHelper: string;
    imageTokensLabel: string;
    videoExamplesLabel: string;
    posterGuidelines: string;
    audioGuidelines: string;
    imageTokenIllustration: string;
    imageTokenCyberpunk: string;
    imageTokenPhotorealistic: string;
    imageTokenAntigravity: string;
    videoExampleVerification: string;
    videoExampleCinematic: string;
    videoExampleDynamic: string;
    posterExamplePrompt: string;
    modelPiperLow: string;
    modelPiperRyan: string;
    modelPiperFr: string;
    modelSdxlTurboGguf: string;
    modelSd15AppleCoreml: string;
    modelStableDiffusionXl: string;
    modelLlavaV16: string;
    modelSvd: string;
    modelAnimateDiffLightningMps: string;
    modelAppleCoreMlVideoPipeline: string;
    removeVideo: string;
    dragAndDropVideoOr: string;
    browse: string;
    supportedFormatsVideo: string;
    mediaPayload: string;
    model: string;
    providerLabel: string;
    modelSelectorLabel: string;
    commercialCloudBridge: string;
    localInfrastructure: string;
    noDialogueDetected: string;
    keyframesExtracted: string;
    noAssetOutput: string;
    localCPlusPlusInference: string;
    localAudioGeneration: string;
    localImageGeneration: string;
    taskQueued: string;
    taskActive: string;
    status: string;
    jobId: string;
    taskFailed: string;
    unknownError: string;
    taskCompletedNoOutput: string;
    uploadingAsset: string;
    removeAsset: string;
    dragAndDropAssetOr: string;
    supportedFormatsAudio: string;
    invalidAudioFile: string;
    invalidImageFile: string;
    failedUploadMedia: string;
    processingProgress: string;
    passiveRagTitle: string;
    passiveRagPlaceholder: string;
    search: string;
    examples: string;
    retrievedContext: string;
    mcpTitle: string;
    mcpLoadError: string;
    mcpFetchError: string;
    mcpExecError: string;
    mcpNoTools: string;
    mcpSelectTool: string;
    mcpEnterValue: string;
    mcpRunTool: string;
    mcpExecuting: string;
    mcpExecutionResult: string;
    tryLabel: string;
    illustrationExamplePrompt: string;
  };
  admin: {
    loadingCredentials: string;
    title: string;
    subtitle: string;
    refresh: string;
    addModel: string;
    modelsSuffix: string;
    noModels: string;
    activeDefault: string;
    optionsPrefix: string;
    editModelTitle: string;
    addModelTitle: string;
    categoryLabel: string;
    modelNameLabel: string;
    modelLabelLabel: string;
    optionsLabel: string;
    setAsDefaultLabel: string;
    cancel: string;
    saveChanges: string;
    createModel: string;
    optSpeech: string;
    optImage: string;
    optVideo: string;
    optVision: string;
    optAudio: string;
    optTheme: string;
    optCode: string;
    helpCategory: string;
    helpModelName: string;
    helpModelLabel: string;
    helpOptions: string;
    helpDefault: string;
    nameAndLabelRequired: string;
    confirmDelete: string;
    loadingModels: string;
    deleteModelTitle: string;
    systemRoleLabel: string;
    errorLoadingModels: string;
    errorSavingModel: string;
    errorDeletingModel: string;
    providerLabel: string;
    helpProvider: string;
    placeholderModelName: string;
    placeholderModelLabel: string;
    placeholderOptions: string;
    featureOverridesTitle: string;
    featureNoOverrides: string;
    featureOverrideEnabled: string;
    pipeline: {
      title: string;
      subtitle: string;
      orderLabel: string;
      enabledLabel: string;
      descriptionLabel: string;
      resetToDefault: string;
      resetConfirm: string;
      saveOrder: string;
      saving: string;
      savedSuccess: string;
      errorSaving: string;
      errorLoading: string;
      dragHint: string;
      disabled: string;
    };
  };
  auth: {
    loginTitle: string;
    loginSubtitle: string;
    emailLabel: string;
    passwordLabel: string;
    signInBtn: string;
    signingInBtn: string;
    orContinueWith: string;
    noAccount: string;
    registerLink: string;
    invalidCredentials: string;
    unexpectedError: string;
    registerTitle: string;
    registerSubtitle: string;
    usernameLabel: string;
    confirmPasswordLabel: string;
    preferredLanguageLabel: string;
    creatingAccountBtn: string;
    createAccountBtn: string;
    termsNotice: string;
    alreadyHaveAccount: string;
    usernameMinLength: string;
    passwordMinLength: string;
    passwordsDoNotMatch: string;
    heroHeadline: string;
    heroTagline: string;
    heroFeature1: string;
    heroFeature2: string;
    heroFeature3: string;
    forgotTitle: string;
    forgotSubtitle: string;
    forgotError: string;
    forgotSuccess: string;
    forgotSuccessDetail: string;
    forgotSubmit: string;
    forgotPasswordLink: string;
    backToLogin: string;
    legalPrivacy: string;
    legalTerms: string;
    legalContact: string;
    langSwitchLabel: string;
  };
  notifications: {
    title: string;
    active: string;
    noTasks: string;
    videoGen: string;
    videoAnalysis: string;
    imageGen: string;
    speechGen: string;
    textGen: string;
    viewAll: string;
    /** The accessible name of the toast region, and of each toast's close button. */
    region: string;
    dismiss: string;
  };
  executionTimeline: {
    title: string;
    step1Title: string;
    step1Desc: string;
    step2Title: string;
    step2Desc: string;
    step3Title: string;
    step3Desc: string;
    step4Title: string;
    step4Desc: string;
  };
  errors: {
    generic: string;
    networkError: string;
    videoGenerationFailed: string;
    fileUploadFailed: string;
    threadParseFailed: string;
    fetchSupportedModelsFailed: string;
    fetchModelCatalogFailed: string;
    featureToggleFailed: string;
  };
  interception: {
    loadingSchema: string;
    loadingError: string;
    loadingErrorDesc: string;
    retry: string;
    fieldRequired: (label: string) => string;
    resolving: string;
    submitPreferences: string;
    validating: string;
  };
  operationGraph: {
    title: string;
    subtitle: string;
    colFeature: string;
    colLabel: string;
    colState: string;
    colEndpoint: string;
    stateActive: string;
    stateLocked: string;
    stateInvisible: string;
    noNodes: string;
    nodeCount: (count: number) => string;
    refresh: string;
  };
  playgroundHeader: {
    title: string;
    subtitle: string;
    mediaCapabilities: string;
    noActiveCapabilities: string;
    noActiveCapabilitiesDesc: string;
    creativeCategory: string;
    creativeDesc: string;
    generateVideoDesc: string;
    generateImageDesc: string;
    speechDesc: string;
    analysisCategory: string;
    analysisDesc: string;
    analyzeVideoDesc: string;
    visionDesc: string;
    audioDesc: string;
    developerCategory: string;
    developerDesc: string;
    codeDesc: string;
    textChatDesc: string;
  };
  verify: {
    loadingMessage: string;
    successTitle: string;
    successDescription: string;
    goToLogin: string;
    errorTitle: string;
    enterTokenManually: string;
    retryVerification: string;
    tokenPlaceholder: string;
    idleTitle: string;
    idleDescription: string;
    tokenLabel: string;
    activateAccount: string;
  };
  /** Credits: the cost confirmation, the top-up dialog, the low-balance warning. `{credits}` is a formatted amount. */
  billing: {
    estimating: string;
    estimate: string;
    available: string;
    insufficient: string;
    noEstimate: string;
    cancel: string;
    confirm: string;
    close: string;
    topUpTitle: string;
    loading: string;
    noPlans: string;
    perMonth: string;
    lowBalanceTitle: string;
    lowBalance: string;
    topUp: string;
    dismiss: string;
  };
  studio: {
    title: string;
    subtitle: string;
    /** The accessible name of the trade filter. */
    /** A profession's name by its catalogue key; a key with no name here shows as is. */
    professions: Record<string, string>;
    loadError: string;
    retry: string;
    back: string;
    pricingFree: string;
    pricingIncluded: string;
    pricingPaid: string;
    lockedPurchase: string;
    lockedPlan: string;
    lockedUnknown: string;
    buyPackage: string;
    upgradePlan: string;
    estimatedCost: string;
    creditsPerRun: string;
    versionHistory: string;
    noChangelog: string;
    install: string;
    /** The one-time settings of a Studio that declares some (agency name, tone, signature). */
    setUp: string;
    setUpHint: string;
    installing: string;
    /** A TOOLKIT Studio (ADR-061): nothing to install, run it directly. */
    included: string;
    uninstall: string;
    configure: string;
    open: string;
    pinnedVersion: string;
    upgradeAvailable: string;
    upgradeNow: string;
    configTitle: string;
    configSubtitle: string;
    save: string;
    cancel: string;
    runNow: string;
    runTitle: string;
    runHistory: string;
    runEmpty: string;
    runSteps: string;
    form: {
      moreOptions: string;
      automatic: string;
      automaticPlain: string;
      automaticHint: string;
      seedRandom: string;
      seedRandomHint: string;
      seedPlaceholder: string;
      seedInvalid: string;
      searchOptions: string;
      noResults: string;
      clear: string;
      showOptions: string;
      local: string;
      cloud: string;
      fast: string;
      balanced: string;
      slow: string;
      about: string;
      perRun: string;
      free: string;
      unavailable: string;
      /** Short reasons a model is greyed out, by the code the job plane sends (`gpu_offline`, `worker_offline`, `provider_down`). */
      unavailableCodes: Record<string, string>;
      /** Why an option of a model-governed field (the aspect) is greyed out: the selected model cannot make it. */
      notForThisModel: string;
      choicesUnreachable: string;
      /** A catalogue-fed list with no model to offer: said out loud, never a blank select. */
      noModels: string;
      /** A closed list that arrived with no choice. */
      noOptions: string;
      /** The blank first line of an optional list that has no default. */
      unset: string;
      /** The disclosure that compares every choice of a model list. */
      compare: string;
      compareHint: string;
      /** The chips of the chosen model: what it makes, where it runs. */
      chosenLabel: string;
    };
    /** Readable names of the values of a closed list the blueprint states itself (`enum`), by field then value. */
    choices: Record<string, Record<string, string>>;
    /** A file input of a Studio (an image to start from, a video, a recording): a picker, never an asset id. */
    asset: {
      imageTitle: string;
      imageBrowse: string;
      imageHint: string;
      videoTitle: string;
      videoBrowse: string;
      videoHint: string;
      audioTitle: string;
      audioBrowse: string;
      audioHint: string;
      tooLarge: string;
      wrongType: string;
      uploadFailed: string;
      list: string;
      remove: string;
      retry: string;
      cancel: string;
      uploading: string;
      done: string;
      error: string;
      required: string;
    };
    /** The title (and help) of the inputs the packs share, read in the caller's language; `<studio>/<key>` wins over `<key>`. */
    inputs: Record<string, { title: string; description?: string }>;
    runOutputs: string;
    /** A rules report (contract 2.0) read as findings: the headline, the facts, the verdicts and severities in words. */
    report: {
      blocking: string;
      toFix: string;
      noneToFix: string;
      summary: string;
      docType: string;
      jurisdiction: string;
      asOf: string;
      notApplicable: string;
      review: string;
      verdict: Record<string, string>;
      severity: Record<string, string>;
    };
    runFailed: string;
    runSucceeded: string;
    resultGone: string;
    resultGoneBody: string;
    runCancelled: string;
    runRunning: string;
    runAwaitingInput: string;
    approve: string;
    cancelRun: string;
    copied: string;
    copy: string;
  };
  packs: {
    loadError: string;
    retry: string;
    holdingsUnavailable: string;
  };
  automation: AutomationDictionary;
  /** The automations of /automation: triggers (webhook, schedule) and run history (ADR-084). */
  automations: AutomationsDictionary;
  /** The Studio list of /studios: search, state, trade, one action per row. */
  catalogue: CatalogueDictionary;
  /** The consent step of a wellbeing Studio (REGULATED). */
  consent: ConsentDictionary;
  /** The plan line and « Plan & packs » (/packs). */
  plan: PlanDictionary;
  /** Accessible names of icon-only controls. */
  a11y: A11yDictionary;
  showcase: ShowcaseDictionary;
  /** Charts, credits, the live job queue and the waits. */
  insights: InsightsDictionary;
  jobsTable: JobsTableDictionary;
  webhooks: WebhooksDictionary;
  runProgress: RunProgressDictionary;
  control: ControlDictionary;
  /** The ⌘K palette: its words (the entries reuse the sidebar's). */
  commandPalette: {
    label: string;
    placeholder: string;
    results: string;
    empty: string;
    navigate: string;
    open: string;
    close: string;
    sectionGo: string;
    sectionAccount: string;
  };
  /** The public home page (visitors without a session). */
  landing: LandingDictionary;
  /** The demo clips: alt, caption and captions. */
  clips: ClipsDictionary;
  /** The 404 page. */
  notFound: NotFoundDictionary;
  /** The Mobile section of the home page and /mobile. */
  mobile: MobileDictionary;
  /** /desktop (Sovkora for Mac) and the home page's "On your Mac" band. */
  desktop: DesktopDictionary;
  /** /pricing (the editions of Sovkora). */
  pricing: PricingDictionary;
  /** /open-source. */
  openSource: OpenSourceDictionary;
  /** The documentation: hub, sidebar, article chrome, the text version of the schemas. */
  docs: DocsDictionary;
}

/** The connector catalogue of the automation page. `{name}` is the connector's name. */
export interface AutomationDictionary {
  connectorsTitle: string;
  connectorsSubtitle: string;
  status: Record<"connected" | "disconnected" | "pending", string>;
  connect: string;
  disconnect: string;
  toggle: string;
  configure: string;
  apiKey: string;
  apiKeyPlaceholder: string;
  saveCredentials: string;
  /** One description per connector id. */
  connectors: Record<"jira" | "whatsapp" | "messenger" | "slack" | "cli-agent", string>;
}

/** Accessible names of icon-only controls (a button showing only an icon still needs a name). */
export interface A11yDictionary {
  closeDialog: string;
  closeDetails: string;
  closeHistory: string;
  help: string;
  copy: string;
  copyJobId: string;
  socialLogin: string;
  socialRegister: string;
  productOverview: string;
  profileSections: string;
  scrollToBottom: string;
  toggleReasoning: string;
  attachFile: string;
  showPassword: string;
  hidePassword: string;
  openMenu: string;
  closeMenu: string;
  deleteConversation: string;
  renameConversation: string;
  openConversations: string;
}

/** The moments a mascot speaks, and the Studios, packs, jobs and notifications surfaces. `{x}` are placeholders. */
export interface ShowcaseDictionary {
  studio: {
    loadErrorTitle: string;
    runRunningBody: string;
    runSucceededBody: string;
    runFailedBody: string;
    /** `{done}`, `{total}` */
    stepsProgress: string;
    download: string;
    /** `{pack}` */
    included: string;
  };
  jobs: {
    emptyTitle: string;
    emptyBody: string;
    emptyCta: string;
    /** `{count}` */
    runningTitle: string;
    runningBody: string;
    /** A result whose file is gone (the placeholder's title). */
    expired: string;
    expiredBody: string;
    /** `{studio}`: the alt of a result picture, never its prompt. */
    resultOf: string;
    result: string;
    copyId: string;
    rawJson: string;
    close: string;
  };
  media: { play: string; pause: string; local: string };
  dashboard: {
    studios: string;
    studiosDesc: string;
    creations: string;
    creationsDesc: string;
    latestCreations: string;
    seeAll: string;
  };
  chat: { approvePlan: string; adjustPlan: string; thinking: string; reasoning: string };
  notifications: { emptyTitle: string; emptyBody: string; running: string; done: string; failed: string };
}
