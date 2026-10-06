import type { Role } from '../types/auth';
import type { WorkOrderStatus } from '../types/domain';
import type { JobAction } from '../api/workOrders';

/** Mirrors WorkOrderService / TechnicianJobService / PartUsageService / TimeLogService. UI hints only - the backend decides. */

export const isOpenStatus = (s: WorkOrderStatus): boolean =>
  s === 'NEW' || s === 'ASSIGNED' || s === 'IN_PROGRESS' || s === 'ON_HOLD';

/** Which technician buttons make sense for a work order in this status. */
export function technicianActions(status: WorkOrderStatus): JobAction[] {
  switch (status) {
    case 'ASSIGNED':
      return ['start'];
    case 'IN_PROGRESS':
      return ['hold', 'complete'];
    case 'ON_HOLD':
      return ['resume'];
    default:
      return [];
  }
}

export const JOB_ACTION_LABEL: Record<JobAction, string> = {
  start: 'Start job',
  hold: 'Put on hold',
  resume: 'Resume job',
  complete: 'Complete job',
};

/** Parts can be used/returned by a technician only while the job is being worked. */
export const canChangeParts = (status: WorkOrderStatus): boolean => status === 'IN_PROGRESS' || status === 'ON_HOLD';

/** Time can be logged while working, on hold, or as a late entry right after completion. */
export const canLogTime = (status: WorkOrderStatus): boolean =>
  status === 'IN_PROGRESS' || status === 'ON_HOLD' || status === 'COMPLETED';

export interface StaffCapabilities {
  assign: boolean;
  edit: boolean;
  cancel: boolean;
  close: boolean;
  remove: boolean;
}

/** What a MANAGER / DISPATCHER may do to a work order in this status. Other roles get nothing. */
export function staffCapabilities(role: Role, status: WorkOrderStatus): StaffCapabilities {
  const isStaff = role === 'MANAGER' || role === 'DISPATCHER';
  return {
    assign: isStaff && status === 'NEW',
    edit: isStaff && status !== 'CLOSED' && status !== 'CANCELLED',
    cancel: isStaff && isOpenStatus(status),
    close: isStaff && status === 'COMPLETED',
    remove: role === 'MANAGER' && status === 'NEW',
  };
}
