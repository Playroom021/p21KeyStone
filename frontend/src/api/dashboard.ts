import { api } from './client';
import type { DashboardSummary, SlaReport, TechnicianWorkloadResponse } from '../types/domain';

/** Report endpoints (MANAGER + DISPATCHER only). */
export async function fetchSummary(): Promise<DashboardSummary> {
  const { data } = await api.get<DashboardSummary>('/api/dashboard/summary');
  return data;
}
export async function fetchSlaReport(): Promise<SlaReport> {
  const { data } = await api.get<SlaReport>('/api/dashboard/sla');
  return data;
}
/** Also the only way to list technicians (id + name) for the assign picker. */
export async function fetchTechnicianWorkload(): Promise<TechnicianWorkloadResponse> {
  const { data } = await api.get<TechnicianWorkloadResponse>('/api/dashboard/technician-workload');
  return data;
}
