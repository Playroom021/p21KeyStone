import { api } from './client';
import { cleanParams } from './params';
import type { WorkOrder, WorkOrderDetail, WorkOrderInput, WorkOrderStatus } from '../types/domain';

/** CUSTOMER-only portal endpoints (/api/customer/**). The backend scopes everything to the caller's customer. */
export async function listMyWorkOrders(status?: WorkOrderStatus | ''): Promise<WorkOrder[]> {
  const { data } = await api.get<WorkOrder[]>('/api/customer/work-orders', { params: cleanParams({ status }) });
  return data;
}
export async function getMyWorkOrder(id: number): Promise<WorkOrderDetail> {
  const { data } = await api.get<WorkOrderDetail>(`/api/customer/work-orders/${id}`);
  return data;
}
export async function raiseRequest(input: WorkOrderInput): Promise<WorkOrder> {
  const { data } = await api.post<WorkOrder>('/api/customer/work-orders', input);
  return data;
}
