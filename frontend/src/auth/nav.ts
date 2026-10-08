import { ROLE_HOME } from './roles';
import type { Role } from '../types/auth';

export interface NavItem {
  to: string;
  label: string;
  /** Match the path exactly (only for the role's dashboard). */
  end?: boolean;
}

/** Sidebar/top navigation per role. Every `to` lives under the role's home, so canAccessPath() holds. */
export const NAV_ITEMS: Record<Role, NavItem[]> = {
  MANAGER: [
    { to: `${ROLE_HOME.MANAGER}`, label: 'Dashboard', end: true },
    { to: `${ROLE_HOME.MANAGER}/customers`, label: 'Customers' },
    { to: `${ROLE_HOME.MANAGER}/sites`, label: 'Sites' },
    { to: `${ROLE_HOME.MANAGER}/work-orders`, label: 'Work orders' },
    { to: `${ROLE_HOME.MANAGER}/parts`, label: 'Parts' },
  ],
  DISPATCHER: [
    { to: `${ROLE_HOME.DISPATCHER}/work-orders`, label: 'Work orders' },
    { to: `${ROLE_HOME.DISPATCHER}/new-work-order`, label: 'New work order' },
    { to: `${ROLE_HOME.DISPATCHER}/customers`, label: 'Customers' },
    { to: `${ROLE_HOME.DISPATCHER}/sites`, label: 'Sites' },
  ],
  TECHNICIAN: [{ to: `${ROLE_HOME.TECHNICIAN}/jobs`, label: 'My jobs' }],
  CUSTOMER: [
    { to: `${ROLE_HOME.CUSTOMER}/work-orders`, label: 'My work orders' },
    { to: `${ROLE_HOME.CUSTOMER}/sites`, label: 'My sites' },
    { to: `${ROLE_HOME.CUSTOMER}/requests/new`, label: 'Raise request' },
  ],
};
