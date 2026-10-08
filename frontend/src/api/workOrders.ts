import { api } from './client';
import { cleanParams } from './params';
import type {
  PageResponse,
  PartUsage,
  PartUsageInput,
  TimeLog,
  TimeLogInput,
  TimeLogSummary,
  WorkOrder,
  WorkOrderDetail,
  WorkOrderInput,
  WorkOrderListParams,
  WorkOrderStatus,
  WorkOrderStatusUpdate,
} from '../types/domain';

// ---- Staff + technician: /api/work-orders (the backend scopes rows by role) ----
export async function listWorkOrders(params: WorkOrderListParams = {}): Promise<PageResponse<WorkOrder>> {
  const { data } = await api.get<PageResponse<WorkOrder>>('/api/work-orders', { params: cleanParams(params) });
  return data;
}
export async function getWorkOrder(id: number): Promise<WorkOrderDetail> {
  const { data } = await api.get<WorkOrderDetail>(`/api/work-orders/${id}`);
  return data;
}
export async function createWorkOrder(input: WorkOrderInput): Promise<WorkOrder> {
  const { data } = await api.post<WorkOrder>('/api/work-orders', input);
  return data;
}
export async function updateWorkOrder(id: number, input: WorkOrderInput): Promise<WorkOrder> {
  const { data } = await api.put<WorkOrder>(`/api/work-orders/${id}`, input);
  return data;
}
export async function deleteWorkOrder(id: number): Promise<void> {
  await api.delete(`/api/work-orders/${id}`);
}
/** Generic transition (assign, cancel, close). Technicians use the job actions below. */
export async function transitionWorkOrder(id: number, body: WorkOrderStatusUpdate): Promise<WorkOrderDetail> {
  const { data } = await api.post<WorkOrderDetail>(`/api/work-orders/${id}/status`, body);
  return data;
}
export function assignTechnician(id: number, technicianId: number, note?: string | null) {
  return transitionWorkOrder(id, { status: 'ASSIGNED', technicianId, note: note || null });
}
export function setWorkOrderStatus(id: number, status: WorkOrderStatus, note?: string | null) {
  return transitionWorkOrder(id, { status, note: note || null });
}

// ---- Technician job actions ----
export type JobAction = 'start' | 'hold' | 'resume' | 'complete';
export async function performJobAction(id: number, action: JobAction, note?: string | null): Promise<WorkOrderDetail> {
  const { data } = await api.post<WorkOrderDetail>(`/api/work-orders/${id}/${action}`, { note: note || null });
  return data;
}

// ---- Part usage ----
export async function listPartUsage(workOrderId: number): Promise<PartUsage[]> {
  const { data } = await api.get<PartUsage[]>(`/api/work-orders/${workOrderId}/part-usage`);
  return data;
}
export async function recordPartUsage(workOrderId: number, input: PartUsageInput): Promise<PartUsage[]> {
  const { data } = await api.post<PartUsage[]>(`/api/work-orders/${workOrderId}/part-usage`, input);
  return data;
}
export async function removePartUsage(workOrderId: number, usageId: number): Promise<void> {
  await api.delete(`/api/work-orders/${workOrderId}/part-usage/${usageId}`);
}

// ---- Time logs ----
export async function listTimeLogs(workOrderId: number): Promise<TimeLogSummary> {
  const { data } = await api.get<TimeLogSummary>(`/api/work-orders/${workOrderId}/time-logs`);
  return data;
}
export async function createTimeLog(workOrderId: number, input: TimeLogInput): Promise<TimeLog> {
  const { data } = await api.post<TimeLog>(`/api/work-orders/${workOrderId}/time-logs`, input);
  return data;
}
export async function deleteTimeLog(workOrderId: number, logId: number): Promise<void> {
  await api.delete(`/api/work-orders/${workOrderId}/time-logs/${logId}`);
}
