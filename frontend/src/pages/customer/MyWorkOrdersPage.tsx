import { useState } from 'react';
import { Link } from 'react-router-dom';
import { listMyWorkOrders } from '../../api/customerPortal';
import { EmptyState, ErrorState, LoadingState, PageHeader, PriorityBadge, SlaBadge, StatusBadge } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { useBasePath } from '../../hooks/useBasePath';
import { formatDateTime, statusLabel } from '../../lib/format';
import { WORK_ORDER_STATUSES } from '../../types/domain';
import type { WorkOrderStatus } from '../../types/domain';

/** The customer's own work orders (GET /api/customer/work-orders), optionally filtered by status. */
export default function MyWorkOrdersPage() {
  const base = useBasePath();
  const [status, setStatus] = useState<WorkOrderStatus | ''>('');
  const list = useAsync(() => listMyWorkOrders(status), [status], 'Could not load your work orders');

  return (
    <>
      <PageHeader
        title="My work orders"
        subtitle="Requests you have raised and their progress."
        actions={
          <Link className="btn" to={`${base}/requests/new`}>
            Raise request
          </Link>
        }
      />
      <div className="toolbar">
        <select aria-label="Filter by status" value={status} onChange={(e) => setStatus(e.target.value as WorkOrderStatus | '')}>
          <option value="">All statuses</option>
          {WORK_ORDER_STATUSES.map((s) => (
            <option key={s} value={s}>
              {statusLabel(s)}
            </option>
          ))}
        </select>
      </div>

      {list.loading && <LoadingState label="Loading your work orders…" />}
      {list.error && <ErrorState message={list.error} onRetry={list.reload} />}
      {list.data && list.data.length === 0 && (
        <EmptyState
          title={status ? 'No work orders with that status' : 'You have not raised any requests yet'}
          hint={status ? 'Choose another status.' : 'Raise a request when something needs attention at one of your sites.'}
          action={
            !status && (
              <Link className="btn" to={`${base}/requests/new`}>
                Raise request
              </Link>
            )
          }
        />
      )}
      {list.data && list.data.length > 0 && (
        <ul className="job-list">
          {list.data.map((wo) => (
            <li key={wo.id}>
              <Link className="job-card" to={`${base}/work-orders/${wo.id}`}>
                <div className="job-top">
                  <strong>{wo.code}</strong>
                  <StatusBadge status={wo.status} />
                </div>
                <div className="job-title">{wo.title}</div>
                <div className="muted">{wo.siteName}</div>
                <div className="job-bottom">
                  <PriorityBadge priority={wo.priority} />
                  <SlaBadge status={wo.slaStatus} />
                  <span className="muted small">Raised {formatDateTime(wo.createdAt)}</span>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
