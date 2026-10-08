import { useState } from 'react';
import { Link } from 'react-router-dom';
import { listWorkOrders } from '../../api/workOrders';
import { EmptyState, ErrorState, LoadingState, PageHeader, Pagination, PriorityBadge, SlaBadge, StatusBadge } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { useBasePath } from '../../hooks/useBasePath';
import { ACTIVE_JOB_STATUSES, dueLabel, mergeActiveJobs } from '../../lib/jobs';
import type { PageResponse, WorkOrder } from '../../types/domain';

type Tab = 'active' | 'completed' | 'all';
const TABS: { id: Tab; label: string }[] = [
  { id: 'active', label: 'Active' },
  { id: 'completed', label: 'Completed' },
  { id: 'all', label: 'All' },
];

/**
 * Technician "My jobs". The backend only ever returns work orders assigned to the caller.
 * Active = assigned + in progress + on hold (three calls, merged, soonest due first).
 */
export default function TechnicianJobsPage() {
  const base = useBasePath();
  const [tab, setTab] = useState<Tab>('active');
  const [page, setPage] = useState(0);

  const jobs = useAsync<{ items: WorkOrder[]; paging: PageResponse<WorkOrder> | null }>(
    async () => {
      if (tab === 'active') {
        const pages = await Promise.all(
          ACTIVE_JOB_STATUSES.map((status) => listWorkOrders({ status, size: 100, sortBy: 'slaDueAt', direction: 'asc' })),
        );
        return { items: mergeActiveJobs(pages.map((p) => p.content)), paging: null };
      }
      const result = await listWorkOrders({
        status: tab === 'completed' ? 'COMPLETED' : '',
        page,
        size: 20,
        sortBy: 'createdAt',
        direction: 'desc',
      });
      return { items: result.content, paging: result };
    },
    [tab, page],
    'Could not load your jobs',
  );

  return (
    <>
      <PageHeader title="My jobs" subtitle="Work assigned to you." />
      <div className="segmented" role="tablist" aria-label="Job filter">
        {TABS.map((t) => (
          <button
            key={t.id}
            type="button"
            role="tab"
            aria-selected={tab === t.id}
            className={tab === t.id ? 'seg seg-on' : 'seg'}
            onClick={() => {
              setTab(t.id);
              setPage(0);
            }}
          >
            {t.label}
          </button>
        ))}
      </div>

      {jobs.loading && <LoadingState label="Loading your jobs…" />}
      {jobs.error && <ErrorState message={jobs.error} onRetry={jobs.reload} />}
      {jobs.data && jobs.data.items.length === 0 && (
        <EmptyState
          title={tab === 'active' ? 'No active jobs' : tab === 'completed' ? 'No completed jobs yet' : 'No jobs yet'}
          hint="New assignments from your dispatcher show up here."
        />
      )}
      {jobs.data && jobs.data.items.length > 0 && (
        <>
          <ul className="job-list">
            {jobs.data.items.map((wo) => {
              const due = dueLabel(wo.slaDueAt, wo.status);
              return (
                <li key={wo.id}>
                  <Link className="job-card" to={`${base}/jobs/${wo.id}`}>
                    <div className="job-top">
                      <strong>{wo.code}</strong>
                      <StatusBadge status={wo.status} />
                    </div>
                    <div className="job-title">{wo.title}</div>
                    <div className="muted">
                      {wo.customerName} · {wo.siteName}
                    </div>
                    <div className="job-bottom">
                      <PriorityBadge priority={wo.priority} />
                      <SlaBadge status={wo.slaStatus} />
                      {due && <span className={wo.slaStatus === 'BREACHED' ? 'error-text small' : 'muted small'}>{due}</span>}
                    </div>
                  </Link>
                </li>
              );
            })}
          </ul>
          {jobs.data.paging && (
            <Pagination
              page={jobs.data.paging.page}
              totalPages={jobs.data.paging.totalPages}
              totalElements={jobs.data.paging.totalElements}
              onPage={setPage}
            />
          )}
        </>
      )}
    </>
  );
}
