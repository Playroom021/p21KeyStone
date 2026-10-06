import { useState } from 'react';
import { Link } from 'react-router-dom';
import { listWorkOrders } from '../../api/workOrders';
import AssignTechnicianModal from '../../components/forms/AssignTechnicianModal';
import {
  EmptyState,
  ErrorState,
  LoadingState,
  PageHeader,
  Pagination,
  PriorityBadge,
  SlaBadge,
  StatusBadge,
} from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { useBasePath } from '../../hooks/useBasePath';
import { useDebounced } from '../../hooks/useDebounced';
import { formatDateTime, priorityLabel, statusLabel } from '../../lib/format';
import { PRIORITIES, WORK_ORDER_STATUSES } from '../../types/domain';
import type { Priority, WorkOrder, WorkOrderStatus } from '../../types/domain';

/** Work-order list for MANAGER and DISPATCHER: filters, search, paging, quick "Assign" on NEW orders. */
export default function WorkOrdersPage() {
  const base = useBasePath();
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<WorkOrderStatus | ''>('');
  const [priority, setPriority] = useState<Priority | ''>('');
  const [notice, setNotice] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const debounced = useDebounced(search);
  const [assigning, setAssigning] = useState<WorkOrder | null>(null);

  const list = useAsync(
    () =>
      listWorkOrders({
        search: debounced,
        status,
        priority,
        page,
        size: 20,
        sortBy: 'createdAt',
        direction: 'desc',
      }),
    [debounced, status, priority, page],
    'Could not load work orders',
  );

  const filtered = Boolean(debounced || status || priority);

  return (
    <>
      <PageHeader
        title="Work orders"
        subtitle="Every job, newest first."
        actions={
          <Link className="btn" to={`${base}/new-work-order`}>
            New work order
          </Link>
        }
      />
      <div className="toolbar">
        <input
          type="search"
          placeholder="Search by code or title"
          aria-label="Search work orders"
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(0);
          }}
        />
        <select
          aria-label="Filter by status"
          value={status}
          onChange={(e) => {
            setStatus(e.target.value as WorkOrderStatus | '');
            setPage(0);
          }}
        >
          <option value="">All statuses</option>
          {WORK_ORDER_STATUSES.map((s) => (
            <option key={s} value={s}>
              {statusLabel(s)}
            </option>
          ))}
        </select>
        <select
          aria-label="Filter by priority"
          value={priority}
          onChange={(e) => {
            setPriority(e.target.value as Priority | '');
            setPage(0);
          }}
        >
          <option value="">All priorities</option>
          {PRIORITIES.map((p) => (
            <option key={p} value={p}>
              {priorityLabel(p)}
            </option>
          ))}
        </select>
      </div>
      {notice && (
        <div className="alert alert-success" role="status">
          {notice}
        </div>
      )}

      {list.loading && <LoadingState label="Loading work orders…" />}
      {list.error && <ErrorState message={list.error} onRetry={list.reload} />}
      {list.data && list.data.content.length === 0 && (
        <EmptyState
          title={filtered ? 'No work orders match your filters' : 'No work orders yet'}
          hint={filtered ? 'Clear a filter or change the search.' : 'Create the first work order to get started.'}
          action={
            !filtered && (
              <Link className="btn" to={`${base}/new-work-order`}>
                New work order
              </Link>
            )
          }
        />
      )}
      {list.data && list.data.content.length > 0 && (
        <>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Code</th>
                  <th>Title</th>
                  <th>Customer / site</th>
                  <th>Priority</th>
                  <th>Status</th>
                  <th>SLA</th>
                  <th>Technician</th>
                  <th>Due</th>
                  <th aria-label="Actions" />
                </tr>
              </thead>
              <tbody>
                {list.data.content.map((wo) => (
                  <tr key={wo.id}>
                    <td data-label="Code">
                      <Link to={`${base}/work-orders/${wo.id}`}>{wo.code}</Link>
                    </td>
                    <td data-label="Title">{wo.title}</td>
                    <td data-label="Customer / site">
                      {wo.customerName}
                      <span className="muted block">{wo.siteName}</span>
                    </td>
                    <td data-label="Priority">
                      <PriorityBadge priority={wo.priority} />
                    </td>
                    <td data-label="Status">
                      <StatusBadge status={wo.status} />
                    </td>
                    <td data-label="SLA">
                      <SlaBadge status={wo.slaStatus} />
                    </td>
                    <td data-label="Technician">{wo.assignedTechnician ?? <span className="muted">Unassigned</span>}</td>
                    <td data-label="Due">{formatDateTime(wo.slaDueAt)}</td>
                    <td className="row-actions">
                      {wo.status === 'NEW' && (
                        <button type="button" className="btn btn-secondary btn-sm" onClick={() => setAssigning(wo)}>
                          Assign
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={list.data.page} totalPages={list.data.totalPages} totalElements={list.data.totalElements} onPage={setPage} />
        </>
      )}

      {assigning && (
        <AssignTechnicianModal
          workOrderId={assigning.id}
          workOrderCode={assigning.code}
          onClose={() => setAssigning(null)}
          onAssigned={(updated) => {
            setAssigning(null);
            setNotice(`${updated.code} assigned to ${updated.assignedTechnician ?? 'the technician'}.`);
            list.reload();
          }}
        />
      )}
    </>
  );
}
