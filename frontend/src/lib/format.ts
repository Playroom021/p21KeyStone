import type { Priority, SlaStatus, WorkOrderStatus } from '../types/domain';

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });
}

/** 125 -> "2h 5m", 45 -> "45m", 0 -> "0m". */
export function formatMinutes(minutes: number | null | undefined): string {
  if (minutes === null || minutes === undefined || !Number.isFinite(minutes)) return '—';
  const m = Math.max(0, Math.round(minutes));
  const h = Math.floor(m / 60);
  const rest = m % 60;
  if (h === 0) return `${rest}m`;
  const days = Math.floor(h / 24);
  if (days > 0) return `${days}d ${h % 24}h`;
  return rest === 0 ? `${h}h` : `${h}h ${rest}m`;
}

export function formatAmount(value: number | null | undefined): string {
  if (value === null || value === undefined || !Number.isFinite(value)) return '—';
  return value.toFixed(2);
}

const STATUS_LABELS: Record<WorkOrderStatus, string> = {
  NEW: 'New',
  ASSIGNED: 'Assigned',
  IN_PROGRESS: 'In progress',
  ON_HOLD: 'On hold',
  COMPLETED: 'Completed',
  CLOSED: 'Closed',
  CANCELLED: 'Cancelled',
};
export function statusLabel(status: string): string {
  return STATUS_LABELS[status as WorkOrderStatus] ?? status;
}

const PRIORITY_LABELS: Record<Priority, string> = { LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High', CRITICAL: 'Critical' };
export function priorityLabel(priority: string): string {
  return PRIORITY_LABELS[priority as Priority] ?? priority;
}

const SLA_LABELS: Record<SlaStatus, string> = { ON_TRACK: 'On track', AT_RISK: 'At risk', BREACHED: 'Breached' };
export function slaLabel(status: string): string {
  return SLA_LABELS[status as SlaStatus] ?? status;
}

/** "YYYY-MM-DDTHH:mm" (what <input type="datetime-local"> uses) for a Date, in local time. */
export function toLocalInputValue(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** Local datetime-local value -> ISO instant (UTC) for the API; null when unparseable. */
export function localInputToIso(value: string): string | null {
  if (!value) return null;
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? null : d.toISOString();
}
