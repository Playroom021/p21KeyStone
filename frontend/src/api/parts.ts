import { api } from './client';
import { cleanParams } from './params';
import type { PageResponse, Part, PartInput } from '../types/domain';

export interface PartListParams {
  search?: string;
  lowStock?: boolean;
  page?: number;
  size?: number;
  sortBy?: string;
  direction?: 'asc' | 'desc';
}

export async function listParts(params: PartListParams = {}): Promise<PageResponse<Part>> {
  const { data } = await api.get<PageResponse<Part>>('/api/parts', { params: cleanParams(params) });
  return data;
}
export async function createPart(input: PartInput): Promise<Part> {
  const { data } = await api.post<Part>('/api/parts', input);
  return data;
}
export async function updatePart(id: number, input: PartInput): Promise<Part> {
  const { data } = await api.put<Part>(`/api/parts/${id}`, input);
  return data;
}
export async function deletePart(id: number): Promise<void> {
  await api.delete(`/api/parts/${id}`);
}
