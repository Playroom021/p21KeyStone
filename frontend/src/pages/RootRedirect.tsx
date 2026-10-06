import { Navigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { ROLE_HOME } from '../auth/roles';
import FullPageMessage from '../components/FullPageMessage';

/** "/" -> the signed-in user's dashboard, or /login. */
export default function RootRedirect() {
  const { user, isInitializing } = useAuth();
  if (isInitializing) return <FullPageMessage text="Checking your session…" />;
  return <Navigate to={user ? ROLE_HOME[user.role] : '/login'} replace />;
}
