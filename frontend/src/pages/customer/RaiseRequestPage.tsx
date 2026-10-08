import { Link, useNavigate } from 'react-router-dom';
import { raiseRequest } from '../../api/customerPortal';
import { listMySites } from '../../api/sites';
import WorkOrderForm from '../../components/forms/WorkOrderForm';
import { EmptyState, ErrorState, LoadingState, PageHeader } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { useBasePath } from '../../hooks/useBasePath';

/** Customer raises a service request (POST /api/customer/work-orders) against one of their own sites. */
export default function RaiseRequestPage() {
  const base = useBasePath();
  const navigate = useNavigate();
  const sites = useAsync(listMySites, [], 'Could not load your sites');

  return (
    <>
      <PageHeader title="Raise a request" subtitle="Tell us what needs attention and where." />
      <section className="card form-card">
        {sites.loading && <LoadingState label="Loading your sites…" />}
        {sites.error && <ErrorState message={sites.error} onRetry={sites.reload} />}
        {sites.data && sites.data.length === 0 && (
          <EmptyState
            title="Add a site first"
            hint="Requests are raised for one of your sites."
            action={
              <Link className="btn" to={`${base}/sites`}>
                Go to my sites
              </Link>
            }
          />
        )}
        {sites.data && sites.data.length > 0 && (
          <WorkOrderForm
            sites={sites.data.map((s) => ({ id: s.id, label: s.name }))}
            submitLabel="Submit request"
            onCancel={() => navigate(`${base}/work-orders`)}
            onSubmit={async (input) => {
              const created = await raiseRequest(input);
              navigate(`${base}/work-orders/${created.id}`, { replace: true });
            }}
          />
        )}
      </section>
    </>
  );
}
