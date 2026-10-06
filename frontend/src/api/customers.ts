import { api } from './client';
import { cleanParams } from './params';
import type { Customer, CustomerInput, PageResponse } from '../types/domain';

export interface ListParams {
  search?: string;
  page?: number;
  size?: number;
  sortBy?: string;
  direction?: 'asc' | 'desc';
}

export async function listCustomers(params: ListParams = {}): Promise<PageResponse<Customer>> {
  const { data } = await api.get<PageResponse<Customer>>('/api/customers', { params: cleanParams(params) });
  return data;
}
export async function createCustomer(input: CustomerInput): Promise<Customer> {
  const { data } = await api.post<Customer>('/api/customers', input);
  return data;
}
export async function updateCustomer(id: number, input: CustomerInput): Promise<Customer> {
  const { data } = await api.put<Customer>(`/api/customers/${id}`, input);
  return data;
}
export async function deleteCustomer(id: number): Promise<void> {
  await api.delete(`/api/customers/${id}`);
}
