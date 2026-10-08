import { isRole } from './roles';
import type { AuthUser } from '../types/auth';

export const TOKEN_KEY = 'keystone_token';
export const USER_KEY = 'keystone_user';

/** Fired (on window) by the API client when the backend answers 401 to a stored token. */
export const UNAUTHORIZED_EVENT = 'keystone:unauthorized';

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function getStoredUser(): AuthUser | null {
  try {
    const raw = localStorage.getItem(USER_KEY);
    if (!raw) return null;
    const u = JSON.parse(raw) as Partial<AuthUser>;
    if (
      typeof u.id !== 'number' ||
      typeof u.fullName !== 'string' ||
      typeof u.email !== 'string' ||
      !isRole(u.role)
    ) {
      return null;
    }
    return {
      id: u.id,
      fullName: u.fullName,
      email: u.email,
      role: u.role,
      companyName: u.companyName ?? null,
    };
  } catch {
    return null;
  }
}

export function saveSession(token: string, user: AuthUser): void {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function clearSession(): void {
  try {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  } catch {
    /* storage unavailable - nothing to clear */
  }
}
