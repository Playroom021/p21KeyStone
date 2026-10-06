import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import type { Role } from '../types/auth';

/**
 * Layout route: only the listed roles may see the nested routes.
 * Must be nested inside <ProtectedRoute/>. Other roles go to /unauthorized.
 */
export default function RoleRoute({ allowed }: { allowed: Role[] }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (!allowed.includes(user.role)) return <Navigate to="/unauthorized" replace />;
  return <Outlet />;
}
