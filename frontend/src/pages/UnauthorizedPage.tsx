import { Link } from 'react-router-dom';
import FullPageMessage from '../components/FullPageMessage';
import { useAuth } from '../auth/AuthContext';
import { ROLE_HOME } from '../auth/roles';

export default function UnauthorizedPage() {
  const { user } = useAuth();
  return (
    <FullPageMessage text="You do not have access to that page.">
      <Link to={user ? ROLE_HOME[user.role] : '/login'}>Go to my dashboard</Link>
    </FullPageMessage>
  );
}
