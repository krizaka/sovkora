/**
 * @file translations.insights.ts
 * @description The words of what Eric's own work says about itself: the charts of the dashboard and of the credits
 * page, the credits meter, the live job queue (steps, ETA, progress toasts) and the waits (en, fr).
 */

import type { ChartLabelsInput } from "@krizaka/charts/provider";

export interface InsightsDictionary {
  activityTitle: string;
  activityDesc: string;
  jobsPerDay: string;
  /** `{days}` */
  jobsPerDayDesc: string;
  day: string;
  completed: string;
  failed: string;
  running: string;
  byKind: string;
  byKindDesc: string;
  kindHeader: string;
  jobsHeader: string;
  jobsCenter: string;
  kinds: { image: string; video: string; audio: string; text: string };
  timeToResult: string;
  timeToResultDesc: string;
  secondsHeader: string;
  studiosUsed: string;
  studiosUsedDesc: string;
  studioHeader: string;
  runsHeader: string;
  noStudioRun: string;
  usage: {
    period: string;
    /** `{days}` */
    periodOption: string;
    kpiJobs: string;
    kpiSuccess: string;
    kpiTime: string;
    kpiRuns: string;
    kpiSuccessHint: string;
    kpiTimeHint: string;
    /** `{days}` */
    kpiPeriodHint: string;
    /** `{jobs}` `{change}` */
    jobsInsight: string;
    /** `{jobs}` */
    jobsInsightFlat: string;
    jobsInsightNone: string;
    /** `{kind}` `{count}` */
    kindInsight: string;
    kindInsightNone: string;
    /** `{studio}` `{runs}` */
    studioInsight: string;
    /** `{kind}` `{seconds}` */
    timeInsight: string;
    timeInsightNone: string;
    source: string;
    export: string;
    exportCsv: string;
    exportJson: string;
    exportFile: string;
    noCredits: string;
    noCreditsLeft: string;
    noCreditsAction: string;
    recent: string;
    seeAll: string;
  };
  credits: {
    nav: string;
    title: string;
    subtitle: string;
    available: string;
    meterLabel: string;
    granted: string;
    purchased: string;
    held: string;
    /** `{plan}` */
    plan: string;
    /** `{grant}` */
    perPeriod: string;
    getCredits: string;
    seeUsage: string;
    noTokens: string;
  };
  queue: {
    /** `{s}` */
    etaSeconds: string;
    /** `{m}` */
    etaMinutes: string;
    steps: { done: string; current: string; upcoming: string; error: string };
    /** `{name}` */
    started: string;
    /** `{name}` */
    ready: string;
    /** `{name}` `{reason}` */
    failed: string;
    /** `{count}` */
    readyMany: string;
    unknownError: string;
  };
  loading: { workspace: string; jobs: string; credits: string; run: string; slow: string };
  /** The pagination of the jobs table: `{page}`, `{count}`. */
  pagination: { nav: string; page: string; compact: string; pageSize: string };
  /** The words of every chart (@krizaka/charts), in the reader's language. */
  charts: ChartLabelsInput;
}

export const insights: Record<"en" | "fr", InsightsDictionary> = {
  en: {
    activityTitle: "Your work on this machine",
    activityDesc: "Read from your own jobs and Studio runs — nothing estimated, nothing sent anywhere.",
    jobsPerDay: "Jobs per day",
    jobsPerDayDesc: "The last {days} days, by outcome",
    day: "Day",
    completed: "Completed",
    failed: "Failed",
    running: "In progress",
    byKind: "What you made",
    byKindDesc: "Your recent jobs, by kind",
    kindHeader: "Kind",
    jobsHeader: "Jobs",
    jobsCenter: "jobs",
    kinds: { image: "Images", video: "Videos", audio: "Voice-overs", text: "Text" },
    timeToResult: "Time to a result",
    timeToResultDesc: "Median seconds from request to result, measured on your machine",
    secondsHeader: "Seconds",
    studiosUsed: "Studios you used",
    studiosUsedDesc: "Runs per Studio",
    studioHeader: "Studio",
    runsHeader: "Runs",
    noStudioRun: "No Studio has run yet.",
    usage: {
      period: "Period",
      periodOption: "{days} days",
      kpiJobs: "Jobs",
      kpiSuccess: "Success rate",
      kpiTime: "Median time to a result",
      kpiRuns: "Studio runs",
      kpiSuccessHint: "completed of finished",
      kpiTimeHint: "request to result",
      kpiPeriodHint: "last {days} days",
      jobsInsight: "{jobs} jobs · {change} vs the period before",
      jobsInsightFlat: "{jobs} jobs · same as the period before",
      jobsInsightNone: "No job in this period yet",
      kindInsight: "Mostly {kind} — {count} jobs",
      kindInsightNone: "Nothing made in this period",
      studioInsight: "{studio} ran {runs} times",
      timeInsight: "{kind} takes {seconds} s at the median",
      timeInsightNone: "No finished job to time yet",
      source: "Read from your own jobs and Studio runs — nothing estimated.",
      export: "Export",
      exportCsv: "Download CSV",
      exportJson: "Download JSON",
      exportFile: "sovkora-usage-{days}d",
      noCredits: "No credits yet",
      noCreditsLeft: "No credits left this period",
      noCreditsAction: "Get credits",
      recent: "Latest creations",
      seeAll: "See all",
    },
    credits: {
      nav: "Credits",
      title: "Credits & usage",
      subtitle: "What you can spend, where it comes from, and what your work used.",
      available: "Available now",
      meterLabel: "Your credits",
      granted: "From your plan",
      purchased: "Purchased",
      held: "Held by work in progress",
      plan: "Plan {plan}",
      perPeriod: "{grant} credits granted each period",
      getCredits: "Get credits",
      seeUsage: "Credits & usage",
      noTokens: "Token counts are not reported per job yet: the charts show jobs, runs and time, which are.",
    },
    queue: {
      etaSeconds: "About {s} s left",
      etaMinutes: "About {m} min left",
      steps: { done: "Done", current: "In progress", upcoming: "Upcoming", error: "Failed" },
      started: "{name} started on your machine",
      ready: "{name} is ready",
      failed: "{name} failed: {reason}",
      readyMany: "{count} results ready",
      unknownError: "unknown error",
    },
    loading: {
      workspace: "Loading your workspace",
      jobs: "Loading your jobs",
      credits: "Loading your credits",
      run: "Loading the run",
      slow: "Still loading — the engine is busy",
    },
    pagination: {
      nav: "Pages of your jobs",
      page: "Page {page}",
      compact: "Page {page} of {count}",
      pageSize: "Rows per page",
    },
    charts: {
      showData: "Show data",
      hideData: "Hide data",
      actions: "Chart actions",
      viewTable: "View as table",
      downloadCsv: "Download CSV",
      downloadPng: "Download PNG",
      downloadSvg: "Download SVG",
      copyData: "Copy data",
      copied: "Data copied",
      close: "Close",
      tableRows: "{count} rows",
      toggleSeries: "Show or hide {label}",
      sparse: "{count} of {total} days have data.",
      trendVsPrevious: "{change} vs the period before",
      tableCaption: "{title} — data",
      loading: "Loading chart…",
      empty: "No data for this period yet.",
      error: "The chart could not be loaded.",
      keyboardHint: "Use the arrow keys to move between points, Home and End to jump to the ends.",
      roleDescription: "chart",
      summaryIntro: "{kind}, {count}.",
      summarySeries: "{label}: from {first} to {last}",
      summaryRange: "lowest {min} ({minAt}), highest {max} ({maxAt})",
      summaryTrendUp: "up {change}",
      summaryTrendDown: "down {change}",
      summaryTrendFlat: "stable",
      summaryTotal: "total {total}",
      summaryShare: "{label} {value} ({share})",
      category: "Category",
      value: "Value",
      total: "Total",
      kinds: { bar: "Bar chart", donut: "Donut chart", area: "Area chart", line: "Line chart" },
    },
  },
  fr: {
    activityTitle: "Votre travail sur cette machine",
    activityDesc: "Lu dans vos propres tâches et exécutions de Studios — rien d'estimé, rien d'envoyé ailleurs.",
    jobsPerDay: "Tâches par jour",
    jobsPerDayDesc: "Les {days} derniers jours, par résultat",
    day: "Jour",
    completed: "Terminées",
    failed: "En échec",
    running: "En cours",
    byKind: "Ce que vous avez créé",
    byKindDesc: "Vos tâches récentes, par type",
    kindHeader: "Type",
    jobsHeader: "Tâches",
    jobsCenter: "tâches",
    kinds: { image: "Images", video: "Vidéos", audio: "Voix off", text: "Texte" },
    timeToResult: "Délai jusqu'au résultat",
    timeToResultDesc: "Secondes médianes entre la demande et le résultat, mesurées sur votre machine",
    secondsHeader: "Secondes",
    studiosUsed: "Studios utilisés",
    studiosUsedDesc: "Exécutions par Studio",
    studioHeader: "Studio",
    runsHeader: "Exécutions",
    noStudioRun: "Aucun Studio n'a encore tourné.",
    usage: {
      period: "Période",
      periodOption: "{days} jours",
      kpiJobs: "Tâches",
      kpiSuccess: "Taux de réussite",
      kpiTime: "Délai médian jusqu'au résultat",
      kpiRuns: "Exécutions de Studios",
      kpiSuccessHint: "terminées sur finies",
      kpiTimeHint: "de la demande au résultat",
      kpiPeriodHint: "{days} derniers jours",
      jobsInsight: "{jobs} tâches · {change} par rapport à la période d'avant",
      jobsInsightFlat: "{jobs} tâches · comme la période d'avant",
      jobsInsightNone: "Aucune tâche sur cette période",
      kindInsight: "Surtout {kind} — {count} tâches",
      kindInsightNone: "Rien de créé sur cette période",
      studioInsight: "{studio} a tourné {runs} fois",
      timeInsight: "{kind} : {seconds} s à la médiane",
      timeInsightNone: "Aucune tâche terminée à chronométrer",
      source: "Lu dans vos propres tâches et exécutions de Studios — rien d'estimé.",
      export: "Exporter",
      exportCsv: "Télécharger le CSV",
      exportJson: "Télécharger le JSON",
      exportFile: "sovkora-usage-{days}j",
      noCredits: "Pas encore de crédits",
      noCreditsLeft: "Plus de crédits pour cette période",
      noCreditsAction: "Obtenir des crédits",
      recent: "Dernières créations",
      seeAll: "Tout voir",
    },
    credits: {
      nav: "Crédits",
      title: "Crédits et usage",
      subtitle: "Ce que vous pouvez dépenser, d'où cela vient, et ce que votre travail a consommé.",
      available: "Disponibles",
      meterLabel: "Vos crédits",
      granted: "De votre offre",
      purchased: "Achetés",
      held: "Réservés par le travail en cours",
      plan: "Offre {plan}",
      perPeriod: "{grant} crédits accordés à chaque période",
      getCredits: "Obtenir des crédits",
      seeUsage: "Crédits et usage",
      noTokens:
        "Le nombre de tokens n'est pas encore remonté par tâche : les graphiques montrent les tâches, les exécutions et les délais, qui le sont.",
    },
    queue: {
      etaSeconds: "Environ {s} s restantes",
      etaMinutes: "Environ {m} min restantes",
      steps: { done: "Terminée", current: "En cours", upcoming: "À venir", error: "En échec" },
      started: "{name} a démarré sur votre machine",
      ready: "{name} est prêt",
      failed: "{name} a échoué : {reason}",
      readyMany: "{count} résultats prêts",
      unknownError: "erreur inconnue",
    },
    loading: {
      workspace: "Chargement de votre espace",
      jobs: "Chargement de vos tâches",
      credits: "Chargement de vos crédits",
      run: "Chargement de l'exécution",
      slow: "Toujours en cours — le moteur est occupé",
    },
    pagination: {
      nav: "Pages de vos tâches",
      page: "Page {page}",
      compact: "Page {page} sur {count}",
      pageSize: "Lignes par page",
    },
    charts: {
      showData: "Voir les données",
      hideData: "Masquer les données",
      actions: "Actions du graphique",
      viewTable: "Voir en tableau",
      downloadCsv: "Télécharger le CSV",
      downloadPng: "Télécharger le PNG",
      downloadSvg: "Télécharger le SVG",
      copyData: "Copier les données",
      copied: "Données copiées",
      close: "Fermer",
      tableRows: "{count} lignes",
      toggleSeries: "Afficher ou masquer {label}",
      sparse: "{count} jours sur {total} ont des données.",
      trendVsPrevious: "{change} par rapport à la période d'avant",
      tableCaption: "{title} — données",
      loading: "Chargement du graphique…",
      empty: "Pas encore de données pour cette période.",
      error: "Le graphique n'a pas pu être chargé.",
      keyboardHint: "Flèches pour passer d'un point à l'autre, Début et Fin pour aller aux extrémités.",
      roleDescription: "graphique",
      summaryIntro: "{kind}, {count}.",
      summarySeries: "{label} : de {first} à {last}",
      summaryRange: "minimum {min} ({minAt}), maximum {max} ({maxAt})",
      summaryTrendUp: "en hausse de {change}",
      summaryTrendDown: "en baisse de {change}",
      summaryTrendFlat: "stable",
      summaryTotal: "total {total}",
      summaryShare: "{label} {value} ({share})",
      category: "Catégorie",
      value: "Valeur",
      total: "Total",
      kinds: { bar: "Histogramme", donut: "Anneau", area: "Aires", line: "Courbe" },
    },
  },
};
