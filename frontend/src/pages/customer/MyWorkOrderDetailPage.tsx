import { Link, useParams } from 'react-router-dom';
import { getMyWorkOrder } from '../../api/customerPortal';
import StatusHistory from '../../components/workorder/StatusHistory';
import WorkOrderInfo from '../../components/workorder/WorkOrderInfo';
import { ErrorState, LoadingState, PageHeader } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { useBasePath } from '../../hooks/useBasePath';

/** One of the customer's work orders with its status history (read-only). Someone else's id is a 404. */
export default function MyWorkOrderDetailPage() {
  const { id } = useParams();
  const workOrderId = Number(id);
  const base = useBasePath();
  const detail = useAsync(() => getMyWorkOrder(workOrderId), [workOrderId], 'Could not load the work order');

  if (!Number.isInteger(workOrderId) || workOrderId <= 0) {
    return <ErrorState message="That work order link is not valid." />;
  }
  if (detail.loading) return <LoadingState label="Loading work order…" />;
  if (detail.error || !detail.data) {
    return (
      <>
        <ErrorState message={detail.error ?? 'Could not load the work order'} onRetry={detail.reload} />
        <p>
          <Link to={`${base}/work-orders`}>← Back to my work orders</Link>
        </p>
      </>
    );
  }

  const wo = detail.data;
  return (
    <>
      <p className="crumb">
        <Link to={`${base}/work-orders`}>← My work orders</Link>
      </p>
      <PageHeader title={`${wo.code} · ${wo.title}`} />
      <WorkOrderInfo wo={wo} />
      <section className="card">
        <h2>Status history</h2>
        <StatusHistory history={wo.history} />
      </section>
    </>
  );
}
