import axios, { AxiosError } from 'axios';
import { clearSession, getToken, UNAUTHORIZED_EVENT } from '../auth/storage';

/**
 * Shared Axios instance.
 * - baseURL: VITE_API_BASE_URL if set, otherwise same-origin (Vite proxies /api in dev).
 * - Every request carries "Authorization: Bearer <jwt>" when a token is stored.
 * - A 401 on a request that carried a token means the session is dead (expired,
 *   logged out, blacklisted): the session is cleared and the app is notified.
 */
export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  headers: { 'Content-Type': 'application/json' },
});

/** Public endpoints: never send a (possibly stale) token to them. */
const PUBLIC_PATHS = ['/api/auth/login', '/api/auth/signup'];

api.interceptors.request.use((config) => {
  const token = getToken();
  const isPublic = PUBLIC_PATHS.some((p) => config.url?.startsWith(p));
  if (token && !isPublic) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    const sentToken = Boolean(error.config?.headers?.get?.('Authorization'));
    if (error.response?.status === 401 && sentToken) {
      clearSession();
      window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
    }
    return Promise.reject(error);
  },
);

/** Backend error body (ApiError): { status, message }. */
interface ApiErrorBody {
  status?: number;
  message?: string;
}

/** Turn any thrown value into a message that is safe to show the user. */
export function getErrorMessage(error: unknown, fallback = 'Something went wrong'): string {
  if (axios.isAxiosError(error)) {
    if (!error.response) {
      return 'Cannot reach the server. Check that the backend is running.';
    }
    const body = error.response.data as ApiErrorBody | undefined;
    if (body && typeof body.message === 'string' && body.message) return body.message;
    if (error.response.status === 403) return 'You do not have permission to do that.';
  }
  return fallback;
}

/** HTTP status of a failed request, or null when there was no response (network error). */
export function getErrorStatus(error: unknown): number | null {
  return axios.isAxiosError(error) ? (error.response?.status ?? null) : null;
}
