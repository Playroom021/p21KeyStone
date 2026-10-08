import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { fetchMe, loginRequest, logoutRequest, signupRequest } from '../api/auth';
import {
  TOKEN_KEY,
  UNAUTHORIZED_EVENT,
  clearSession,
  getStoredUser,
  getToken,
  saveSession,
} from './storage';
import type { AuthResponse, AuthUser, SignupPayload } from '../types/auth';

interface AuthContextValue {
  user: AuthUser | null;
  isAuthenticated: boolean;
  /** True until a stored session has been checked against the backend. */
  isInitializing: boolean;
  login: (email: string, password: string) => Promise<AuthUser>;
  /** POST /api/auth/signup, then stores the returned session exactly like login. */
  signup: (payload: SignupPayload) => Promise<AuthUser>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  // A user object without a token (or vice versa) is not a session.
  const [user, setUser] = useState<AuthUser | null>(() => (getToken() ? getStoredUser() : null));
  const [isInitializing, setIsInitializing] = useState<boolean>(() => user !== null);

  // On startup, confirm a stored token is still accepted by the backend (GET /api/me).
  // 401 -> the API client clears the session and fires UNAUTHORIZED_EVENT (handled below).
  // Any other failure (e.g. backend briefly down) keeps the stored session.
  useEffect(() => {
    if (!user) return;
    let cancelled = false;
    fetchMe()
      .then((me) => {
        if (cancelled) return;
        const token = getToken();
        if (!token) return;
        const merged: AuthUser = { ...me, companyName: user.companyName };
        saveSession(token, merged);
        setUser(merged);
      })
      .catch(() => {
        /* handled by the 401 interceptor; other errors keep the session */
      })
      .finally(() => {
        if (!cancelled) setIsInitializing(false);
      });
    return () => {
      cancelled = true;
    };
    // Run once for the session found at startup.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Session died (401 from the API) -> drop the user; routes redirect to /login.
  useEffect(() => {
    const onUnauthorized = () => setUser(null);
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
  }, []);

  // Logging out in another tab logs out this one too.
  useEffect(() => {
    const onStorage = (e: StorageEvent) => {
      if (e.key === TOKEN_KEY && !e.newValue) setUser(null);
    };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, []);

  /** Store the token + user returned by login or signup and make them the current session. */
  const startSession = useCallback((res: AuthResponse): AuthUser => {
    const nextUser: AuthUser = {
      id: res.id,
      fullName: res.fullName,
      email: res.email,
      role: res.role,
      companyName: res.companyName ?? null,
    };
    saveSession(res.token, nextUser);
    setUser(nextUser);
    setIsInitializing(false);
    return nextUser;
  }, []);

  const login = useCallback(
    async (email: string, password: string) => startSession(await loginRequest(email, password)),
    [startSession],
  );

  const signup = useCallback(
    async (payload: SignupPayload) => startSession(await signupRequest(payload)),
    [startSession],
  );

  const logout = useCallback(async () => {
    try {
      // Blacklists the token server-side. Sent while the token is still stored.
      await logoutRequest();
    } catch {
      // Even if the call fails, the local session must still end.
    }
    clearSession();
    setUser(null);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ user, isAuthenticated: user !== null, isInitializing, login, signup, logout }),
    [user, isInitializing, login, signup, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
