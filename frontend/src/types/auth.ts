export const ROLES = ['MANAGER', 'DISPATCHER', 'TECHNICIAN', 'CUSTOMER'] as const;
export type Role = (typeof ROLES)[number];

/** Response body of POST /api/auth/login and /api/auth/signup (AuthResponse on the backend). */
export interface AuthResponse {
  token: string;
  id: number;
  fullName: string;
  email: string;
  role: Role;
  companyName: string | null;
}

/** The user object kept in the frontend (token is stored separately). */
export interface AuthUser {
  id: number;
  fullName: string;
  email: string;
  role: Role;
  companyName: string | null;
}

/** Request body of POST /api/auth/signup (SignupRequest on the backend). */
export interface SignupPayload {
  fullName: string;
  email: string;
  password: string;
  role: Role;
  /** Required by the backend only when role is CUSTOMER. */
  companyName?: string;
}
