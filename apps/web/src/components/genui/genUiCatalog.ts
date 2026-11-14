import { chartsGenUiCatalog } from "@krizaka/charts/genui";
import { baseGenUiCatalog, type GenUiCatalog } from "@krizaka/ui/genui";

/**
 * The components a generated answer may draw — Krizaka UI's base catalog and the charts' (ADR-082, catalog
 * krizaka:genui/v1). One list for the chat and for a Studio run's dashboard, so both refuse the same things.
 */
export const GENUI_CATALOG: GenUiCatalog = { ...baseGenUiCatalog, ...chartsGenUiCatalog };
