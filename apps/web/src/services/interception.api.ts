/**
 * @file interception.api.ts
 * @description Stateless outbound adapter for interception schema and resolution operations.
 * Extracts network logic previously inlined inside `InterceptionForm.tsx`.
 */

import { restRequest } from "./rest-client";

// ── Types ────────────────────────────────────────────────────────────────────

export interface SchemaFieldOption {
  label: string;
  value: string;
}

export interface SchemaField {
  name: string;
  label: string;
  type: "text" | "select" | "textarea";
  required?: boolean;
  options?: SchemaFieldOption[];
  defaultValue?: string;
  placeholder?: string;
}

export interface SchemaDescriptor {
  title: string;
  description: string;
  fields: SchemaField[];
}

/**
 * Stateless adapter exposing interception-related network operations.
 */
export const InterceptionApi = {
  /**
   * Fetches a dynamic interception form schema from the BFF REST proxy
   * (`GET /api/v1/interceptions/{schemaId}`). The endpoint returns the raw schema JSON, which
   * `restRequest` parses into the descriptor.
   *
   * @param schemaId - The schema identifier to resolve.
   * @returns The parsed schema descriptor with field definitions.
   */
  fetchSchema: async (schemaId: string): Promise<SchemaDescriptor> => {
    const schema = await restRequest<SchemaDescriptor>(`/api/v1/interceptions/${encodeURIComponent(schemaId)}`);
    if (!schema) throw new Error("No schema returned from server.");
    return schema;
  },

  /**
   * Submits interception form responses to the BFF REST proxy
   * (`POST /api/v1/interceptions/resolve`).
   *
   * @param interceptionType - The interception type identifier.
   * @param schemaId - The schema identifier.
   * @param responses - The form response key-value map.
   * @returns The resolved status from the server.
   */
  resolve: async (interceptionType: string, schemaId: string, responses: Record<string, string>): Promise<boolean> => {
    const data = await restRequest<{ resolved: boolean }>("/api/v1/interceptions/resolve", {
      method: "POST",
      body: { interceptionType, schemaId, responses },
    });
    return data?.resolved ?? false;
  },
} as const;
