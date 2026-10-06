import { ROLES, type Role } from '../types/auth';

/** Home (dashboard) path for each role. Also the root of that role's route area. */
export const ROLE_HOME: Record<Role, string> = {
  MANAGER: '/manager',
  DISPATCHER: '/dispatcher',
  TECHNICIAN: '/technician',
  CUSTOMER: '/customer',
};

export const ROLE_LABEL: Record<Role, string> = {
  MANAGER: 'Manager',
  DISPATCHER: 'Dispatcher',
  TECHNICIAN: 'Technician',
  CUSTOMER: 'Customer',
};

export function isRole(value: unknown): value is Role {
  return typeof value === 'string' && (ROLES as readonly string[]).includes(value);
}

/** True if `path` is the role's home or lives under it (e.g. /manager/anything). */
export function canAccessPath(role: Role, path: string): boolean {
  const home = ROLE_HOME[role];
  return path === home || path.startsWith(home + '/');
}

/**
 * Where to send a user after login: the page they originally wanted if their
 * role may see it, otherwise their own dashboard.
 */
export function postLoginPath(role: Role, requested?: string | null): string {
  if (requested && canAccessPath(role, requested)) return requested;
  return ROLE_HOME[role];
}
