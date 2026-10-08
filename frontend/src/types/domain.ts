/** API shapes of the existing backend (DTOs under com.keyStone.Playroom021.dto). */

export const WORK_ORDER_STATUSES = [
  'NEW',
  'ASSIGNED',
  'IN_PROGRESS',
  'ON_HOLD',
  'COMPLETED',
  'CLOSED',
  'CANCELLED',
] as const;
export type WorkOrderStatus = (typeof WORK_ORDER_STATUSES)[number];

export const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'] as const;
export type Priority = (typeof PRIORITIES)[number];

export type SlaStatus = 'ON_TRACK' | 'AT_RISK' | 'BREACHED';

/** Spring Data page wrapper (PageResponse). */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// ---- Customers / sites ----
export interface Customer {
  id: number;
  companyName: string;
  contactEmail: string | null;
  createdAt: string;
}
export interface CustomerInput {
  companyName: string;
  contactEmail: string | null;
}

/** SiteDetailResponse (staff API, /api/sites). */
export interface Site {
  id: number;
  customerId: number;
  customerName: string;
  name: string;
  addressLine: string | null;
  city: string | null;
  createdAt: string;
}
/** SiteResponse (customer portal, /api/customer/sites). */
export interface PortalSite {
  id: number;
  name: string;
  addressLine: string | null;
  city: string | null;
}
export interface SiteInput {
  name: string;
  addressLine: string | null;
  city: string | null;
}

// ---- Work orders ----
export interface StatusHistoryEntry {
  fromStatus: WorkOrderStatus | null;
  toStatus: WorkOrderStatus;
  note: string | null;
  changedByEmail: string;
  changedByRole: string;
  changedAt: string;
}

export interface WorkOrder {
  id: number;
  code: string;
  title: string;
  customerId: number;
  customerName: string;
  siteId: number;
  siteName: string;
  priority: Priority;
  status: WorkOrderStatus;
  assignedTechnicianId: number | null;
  assignedTechnician: string | null;
  slaDueAt: string | null;
  slaStatus?: SlaStatus | null;
  createdAt: string;
}

export interface WorkOrderDetail extends WorkOrder {
  description: string | null;
  updatedAt: string;
  history: StatusHistoryEntry[];
}

export interface WorkOrderInput {
  siteId: number;
  title: string;
  description: string | null;
  priority: Priority;
}

export interface WorkOrderStatusUpdate {
  status: WorkOrderStatus;
  technicianId?: number;
  note?: string | null;
}

export interface WorkOrderListParams {
  customerId?: number;
  siteId?: number;
  technicianId?: number;
  status?: WorkOrderStatus | '';
  priority?: Priority | '';
  search?: string;
  page?: number;
  size?: number;
  sortBy?: string;
  direction?: 'asc' | 'desc';
}

// ---- Parts / usage / time ----
export interface Part {
  id: number;
  sku: string;
  name: string;
  description: string | null;
  unit: string;
  quantityOnHand: number;
  reorderLevel: number;
  lowStock: boolean;
  unitCost: number;
  createdAt: string;
  updatedAt: string;
}
export interface PartInput {
  sku: string;
  name: string;
  description: string | null;
  unit: string;
  quantityOnHand: number;
  reorderLevel: number;
  unitCost: number;
}

export interface PartUsage {
  id: number;
  workOrderId: number;
  partId: number;
  partSku: string;
  partName: string;
  quantity: number;
  unitCostAtUse: number;
  lineCost: number;
  remainingStock: number | null;
  note: string | null;
  usedByEmail: string;
  usedByRole: string;
  usedAt: string;
}
export interface PartUsageInput {
  items: { partId: number; quantity: number }[];
  note: string | null;
}

export interface TimeLog {
  id: number;
  workOrderId: number;
  technicianId: number;
  technician: string;
  startedAt: string;
  endedAt: string;
  minutes: number;
  note: string | null;
  createdAt: string;
}
export interface TimeLogSummary {
  entries: TimeLog[];
  totalMinutes: number;
}
export interface TimeLogInput {
  startedAt: string;
  endedAt: string;
  note: string | null;
}

// ---- Dashboard / reports (MANAGER + DISPATCHER) ----
export interface SlaSummary {
  onTrackOpen: number;
  atRisk: number;
  breachedOpen: number;
  breachedCompleted: number;
  totalBreaches: number;
  metOnTime: number;
  compliancePercent: number | null;
}
export interface DashboardSummary {
  generatedAt: string;
  totalWorkOrders: number;
  openWorkOrders: number;
  completedWorkOrders: number;
  cancelledWorkOrders: number;
  overdueWorkOrders: number;
  countsByStatus: Record<string, number>;
  sla: SlaSummary;
}
export interface SlaWorkOrderItem {
  id: number;
  code: string;
  title: string;
  priority: Priority;
  status: WorkOrderStatus;
  assignedTechnicianId: number | null;
  assignedTechnician: string | null;
  slaDueAt: string | null;
  slaStatus: SlaStatus;
  minutesOverdue: number | null;
  minutesRemaining: number | null;
}
export interface SlaReport {
  generatedAt: string;
  summary: SlaSummary;
  breachedWorkOrders: SlaWorkOrderItem[];
  atRiskWorkOrders: SlaWorkOrderItem[];
  listLimit: number;
}
export interface TechnicianWorkload {
  technicianId: number;
  technician: string;
  assigned: number;
  inProgress: number;
  onHold: number;
  openTotal: number;
  overdue: number;
  atRisk: number;
  completed: number;
}
export interface TechnicianWorkloadResponse {
  generatedAt: string;
  technicians: TechnicianWorkload[];
  unassignedOpen: number;
}
