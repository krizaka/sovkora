import { z } from "zod";

/**
 * Stateless adapter for the identity service's organizations (orazaka#372), through the BFF (`/api/v1/**`).
 * Parsed, never cast: this console shows which key costs what and lets an administrator act on it.
 */
export const OrganizationSchema = z.object({ id: z.string(), slug: z.string(), name: z.string() });
export type Organization = z.infer<typeof OrganizationSchema>;

/** One `svc_*` key of an organization, with the account that owns it (`ownerUserId` is the billing actor). */
export const ServiceKeySchema = z.object({
  id: z.string(),
  name: z.string(),
  keyPrefix: z.string(),
  ownerUserId: z.string(),
  ownerUsername: z.string(),
  lastUsedAt: z.string().nullable().optional(),
});
export type ServiceKey = z.infer<typeof ServiceKeySchema>;

async function read(path: string): Promise<unknown> {
  const response = await fetch(path);
  if (!response.ok) throw new Error(`${response.status} ${response.statusText}`);
  return response.json();
}

export const OrganizationsAdminApi = {
  list: async (): Promise<Organization[]> => z.array(OrganizationSchema).parse(await read("/api/v1/organizations")),

  serviceKeys: async (organizationId: string): Promise<ServiceKey[]> =>
    z
      .array(ServiceKeySchema)
      .parse(await read(`/api/v1/organizations/${encodeURIComponent(organizationId)}/service-keys`)),
};
