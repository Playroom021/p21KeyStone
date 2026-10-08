import { api } from './client';
import { cleanParams } from './params';
import type { PageResponse, PortalSite, Site, SiteInput } from '../types/domain';

export interface SiteListParams {
  customerId?: number;
  search?: string;
  page?: number;
  size?: number;
  sortBy?: string;
  direction?: 'asc' | 'desc';
}

/** Staff: GET /api/sites (optionally filtered by customer). */
export async function listSites(params: SiteListParams = {}): Promise<PageResponse<Site>> {
  const { data } = await api.get<PageResponse<Site>>('/api/sites', { params: cleanParams(params) });
  return data;
}
export async function createSite(customerId: number, input: SiteInput): Promise<Site> {
  const { data } = await api.post<Site>(`/api/customers/${customerId}/sites`, input);
  return data;
}
export async function updateSite(id: number, input: SiteInput): Promise<Site> {
  const { data } = await api.put<Site>(`/api/sites/${id}`, input);
  return data;
}
export async function deleteSite(id: number): Promise<void> {
  await api.delete(`/api/sites/${id}`);
}

// ---- Customer portal (CUSTOMER role only) ----
export async function listMySites(): Promise<PortalSite[]> {
  const { data } = await api.get<PortalSite[]>('/api/customer/sites');
  return data;
}
export async function createMySite(input: SiteInput): Promise<PortalSite> {
  const { data } = await api.post<PortalSite>('/api/customer/sites', input);
  return data;
}
