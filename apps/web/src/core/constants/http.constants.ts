/**
 * @file http.constants.ts
 * @description Shared HTTP and API constants used across services and components.
 *
 * Eliminates Sonar S1192 (duplicate string literals) for HTTP headers,
 * methods, content types, and common status values.
 *
 * @example
 * import { HTTP_HEADER, HTTP_METHOD, CONTENT_TYPE, JOB_STATUS } from "@/core/constants/http.constants";
 */

// ─── HTTP Methods ───────────────────────────────────────────────────────────
export const HTTP_METHOD = {
  GET: "GET",
  POST: "POST",
  PUT: "PUT",
  PATCH: "PATCH",
  DELETE: "DELETE",
} as const;

export type HttpMethod = (typeof HTTP_METHOD)[keyof typeof HTTP_METHOD];

// ─── HTTP Headers ───────────────────────────────────────────────────────────
export const HTTP_HEADER = {
  CONTENT_TYPE: "Content-Type",
  AUTHORIZATION: "Authorization",
  ACCEPT: "Accept",
} as const;

// ─── Content Types ──────────────────────────────────────────────────────────
export const CONTENT_TYPE = {
  JSON: "application/json",
  FORM_DATA: "multipart/form-data",
  TEXT_EVENT_STREAM: "text/event-stream",
} as const;

// ─── Job / Task Status ──────────────────────────────────────────────────────
export const JOB_STATUS = {
  PENDING: "PENDING",
  PROCESSING: "PROCESSING",
  COMPLETED: "COMPLETED",
  FAILED: "FAILED",
  CANCELLED: "CANCELLED",
  DELETED: "DELETED",
} as const;

export type JobStatusValue = (typeof JOB_STATUS)[keyof typeof JOB_STATUS];

// ─── Theme Modes ────────────────────────────────────────────────────────────
export const THEME_MODE = {
  LIGHT: "light",
  DARK: "dark",
  SYSTEM: "system",
  CYBERPUNK: "cyberpunk",
  KRIZAKA: "krizaka",
} as const;

export type ThemeModeValue = (typeof THEME_MODE)[keyof typeof THEME_MODE];

// ─── Routes ─────────────────────────────────────────────────────────────────
export const ROUTE = {
  LOGIN: "/login",
  DASHBOARD: "/dashboard",
  CHAT: "/chat",
  SETTINGS: "/settings",
} as const;
