import { useNavigate } from 'react-router-dom';
import { listSites } from '../../api/sites';
import { createWorkOrder } from '../../api/workOrders';
import WorkOrderForm from '../../components/forms/WorkOrderForm';
import { EmptyState, ErrorState, LoadingState, PageHeader } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { useBasePath } from '../../hooks/useBasePath';

/** Create a work order against any site (MANAGER / DISPATCHER). The new order starts in NEW. */
export default function NewWorkOrderPage() {
  const base = useBasePath();
  const navigate = useNavigate();
  const sites = useAsync(() => listSites({ size: 100, sortBy: 'name' }), [], 'Could not load sites');

  return (
    <>
      <PageHeader title="New work order" subtitle="It starts as NEW; assign a technician from the work order." />
      <section className="card form-card">
        {sites.loading && <LoadingState label="Loading sites…" />}
        {sites.error && <ErrorState message={sites.error} onRetry={sites.reload} />}
        {sites.data && sites.data.content.length === 0 && (
          <EmptyState title="No sites yet" hint="A work order needs a site. Create a customer and a site first." />
        )}
        {sites.data && sites.data.content.length > 0 && (
          <WorkOrderForm
            sites={sites.data.content.map((s) => ({ id: s.id, label: `${s.customerName} — ${s.name}` }))}
            submitLabel="Create work order"
            onCancel={() => navigate(`${base}/work-orders`)}
            onSubmit={async (input) => {
              const created = await createWorkOrder(input);
              navigate(`${base}/work-orders/${created.id}`, { replace: true });
            }}
          />
        )}
      </section>
    </>
  );
}
