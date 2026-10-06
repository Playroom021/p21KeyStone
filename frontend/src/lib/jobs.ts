import { formatMinutes } from './format';
import type { WorkOrder, WorkOrderStatus } from '../types/domain';

/** The statuses a technician works on. "My jobs → Active" loads these three and merges them. */
export const ACTIVE_JOB_STATUSES: WorkOrderStatus[] = ['ASSIGNED', 'IN_PROGRESS', 'ON_HOLD'];

/** Merge several lists into one, soonest SLA due first (no due time last), ties broken by id. */
export function mergeActiveJobs(lists: WorkOrder[][]): WorkOrder[] {
  const dueMs = (wo: WorkOrder) => (wo.slaDueAt ? new Date(wo.slaDueAt).getTime() : Number.POSITIVE_INFINITY);
  return lists.flat().sort((a, b) => {
    const da = dueMs(a);
    const db = dueMs(b);
    if (da !== db) return da < db ? -1 : 1;
    return a.id - b.id;
  });
}

/** "Due in 3h" / "Overdue by 45m" for open work; empty string when there is nothing useful to say. */
export function dueLabel(slaDueAt: string | null | undefined, status: WorkOrderStatus, now: Date = new Date()): string {
  if (!slaDueAt) return '';
  if (status !== 'ASSIGNED' && status !== 'IN_PROGRESS' && status !== 'ON_HOLD' && status !== 'NEW') return '';
  const due = new Date(slaDueAt).getTime();
  if (Number.isNaN(due)) return '';
  const minutes = Math.round((due - now.getTime()) / 60000);
  if (minutes >= 0) return `Due in ${formatMinutes(minutes)}`;
  return `Overdue by ${formatMinutes(-minutes)}`;
}
