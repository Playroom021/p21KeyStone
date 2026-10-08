import { formatDateTime } from '../../lib/format';
import type { WorkOrderDetail } from '../../types/domain';
import { PriorityBadge, SlaBadge, StatusBadge } from '../ui';

/** Read-only summary of a work order, shared by every role's detail page. */
export default function WorkOrderInfo({ wo }: { wo: WorkOrderDetail }) {
  return (
    <section className="card">
      <div className="badges">
        <StatusBadge status={wo.status} />
        <PriorityBadge priority={wo.priority} />
        {wo.slaStatus && <SlaBadge status={wo.slaStatus} />}
      </div>
      <dl className="info-grid">
        <div>
          <dt>Customer</dt>
          <dd>{wo.customerName}</dd>
        </div>
        <div>
          <dt>Site</dt>
          <dd>{wo.siteName}</dd>
        </div>
        <div>
          <dt>Technician</dt>
          <dd>{wo.assignedTechnician ?? 'Unassigned'}</dd>
        </div>
        <div>
          <dt>SLA due</dt>
          <dd>{formatDateTime(wo.slaDueAt)}</dd>
        </div>
        <div>
          <dt>Created</dt>
          <dd>{formatDateTime(wo.createdAt)}</dd>
        </div>
        <div>
          <dt>Last updated</dt>
          <dd>{formatDateTime(wo.updatedAt)}</dd>
        </div>
      </dl>
      <h3>Description</h3>
      <p className="prewrap">{wo.description?.trim() ? wo.description : <span className="muted">No description.</span>}</p>
    </section>
  );
}
