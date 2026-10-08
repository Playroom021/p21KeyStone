import { useAuth } from '../auth/AuthContext';
import { ROLE_LABEL } from '../auth/roles';

export default function RoleDashboardPlaceholder({
  description,
}: {
  description: string;
}) {
  const { user } = useAuth();

  if (!user) return null;

  return (
    <section className="card">
      <h1>{ROLE_LABEL[user.role]} dashboard</h1>
      <p className="muted">{description}</p>
      <p>
        <strong>Authentication:</strong>{' '}
        <span className="ok-text">JWT authenticated</span>
      </p>
      <p className="muted">
        Dashboard data is available through the dashboard APIs.
      </p>
    </section>
  );
}
