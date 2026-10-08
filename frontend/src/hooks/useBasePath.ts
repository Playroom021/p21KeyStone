import { useAuth } from '../auth/AuthContext';
import { ROLE_HOME } from '../auth/roles';

/** The signed-in user's route area (e.g. "/dispatcher"); shared pages build their links from it. */
export function useBasePath(): string {
  const { user } = useAuth();
  return user ? ROLE_HOME[user.role] : '';
}
