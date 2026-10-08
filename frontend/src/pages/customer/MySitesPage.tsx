import { useState } from 'react';
import { Link } from 'react-router-dom';
import { listMySites } from '../../api/sites';
import SiteFormModal from '../../components/forms/SiteFormModal';
import { EmptyState, ErrorState, LoadingState, PageHeader } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { useBasePath } from '../../hooks/useBasePath';

/** The customer's own sites (GET /api/customer/sites) with an add-site form (POST /api/customer/sites). */
export default function MySitesPage() {
  const base = useBasePath();
  const sites = useAsync(listMySites, [], 'Could not load your sites');
  const [adding, setAdding] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  return (
    <>
      <PageHeader
        title="My sites"
        subtitle="The locations you can raise requests for."
        actions={
          <button type="button" className="btn" onClick={() => setAdding(true)}>
            Add site
          </button>
        }
      />
      {notice && (
        <div className="alert alert-success" role="status">
          {notice}
        </div>
      )}

      {sites.loading && <LoadingState label="Loading your sites…" />}
      {sites.error && <ErrorState message={sites.error} onRetry={sites.reload} />}
      {sites.data && sites.data.length === 0 && (
        <EmptyState
          title="No sites yet"
          hint="Add a site first, then you can raise a request for it."
          action={
            <button type="button" className="btn" onClick={() => setAdding(true)}>
              Add site
            </button>
          }
        />
      )}
      {sites.data && sites.data.length > 0 && (
        <ul className="job-list">
          {sites.data.map((s) => (
            <li key={s.id} className="job-card static">
              <div className="job-title">{s.name}</div>
              <div className="muted">{[s.addressLine, s.city].filter(Boolean).join(', ') || 'No address recorded'}</div>
            </li>
          ))}
        </ul>
      )}
      {sites.data && sites.data.length > 0 && (
        <p>
          <Link to={`${base}/requests/new`}>Raise a request →</Link>
        </p>
      )}

      {adding && (
        <SiteFormModal
          mode="portal"
          onClose={() => setAdding(false)}
          onSaved={(saved) => {
            setAdding(false);
            setNotice(`Added ${saved.name}.`);
            sites.reload();
          }}
        />
      )}
    </>
  );
}
