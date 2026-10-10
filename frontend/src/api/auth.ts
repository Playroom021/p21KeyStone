import { api } from './client';
import type { AuthResponse, AuthUser, SignupPayload } from '../types/auth';

export async function loginRequest(email: string, password: string): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/api/auth/login', { email, password });
  return data;
}

/** POST /api/auth/signup - answers 201 with the same body as login (token + user). */
export async function signupRequest(payload: SignupPayload): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/api/auth/signup', payload);
  return data;
}

export async function logoutRequest(): Promise<void> {
  await api.post('/api/auth/logout');
}

/** GET /api/me - used to confirm a stored token is still valid. */
export async function fetchMe(): Promise<AuthUser> {
  const { data } = await api.get<Omit<AuthUser, 'companyName'>>('/api/me');
  return { ...data, companyName: null };
}
