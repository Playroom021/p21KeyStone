import { Link } from 'react-router-dom';
import { fetchSlaReport, fetchSummary, fetchTechnicianWorkload } from '../../api/dashboard';
import { EmptyState, ErrorState, LoadingState, PageHeader, PriorityBadge } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { useBasePath } from '../../hooks/useBasePath';
import { formatDateTime, formatMinutes, statusLabel } from '../../lib/format';
import { WORK_ORDER_STATUSES } from '../../types/domain';
import type { SlaWorkOrderItem } from '../../types/domain';

function Stat({ label, value, tone }: { label: string; value: string | number; tone?: 'bad' | 'warn' | 'good' }) {
  return (
    <div className={tone ? `stat stat-${tone}` : 'stat'}>
      <span className="stat-value">{value}</span>
      <span className="stat-label">{label}</span>
    </div>
  );
}

function SlaList({ title, items, kind, base }: { title: string; items: SlaWorkOrderItem[]; kind: 'breached' | 'risk'; base: string }) {
  return (
    <section className="card">
      <h2>{title}</h2>
      {items.length === 0 ? (
        <EmptyState title={kind === 'breached' ? 'Nothing is overdue' : 'Nothing is at risk'} />
      ) : (
        <ul className="item-list">
          {items.map((i) => (
            <li key={i.id} className="item">
              <div className="item-main">
                <Link to={`${base}/work-orders/${i.id}`}>
                  <strong>{i.code}</strong> · {i.title}
                </Link>
                <span className="muted small">
                  {i.assignedTechnician ?? 'Unassigned'} · due {formatDateTime(i.slaDueAt)}
                </span>
              </div>
              <div className="item-side">
                <PriorityBadge priority={i.priority} />
                <span className={kind === 'breached' ? 'error-text small' : 'small'}>
                  {kind === 'breached'
                    ? `${formatMinutes(i.minutesOverdue)} overdue`
                    : `${formatMinutes(i.minutesRemaining)} left`}
                </span>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

/** Manager dashboard: summary, SLA, and technician workload (three independent report calls). */
export default function ManagerDashboardPage() {
  const base = useBasePath();
  const summary = useAsync(fetchSummary, [], 'Could not load the summary');
  const sla = useAsync(fetchSlaReport, [], 'Could not load the SLA report');
  const workload = useAsync(fetchTechnicianWorkload, [], 'Could not load technician workload');

  return (
    <>
      <PageHeader title="Dashboard" subtitle="Work orders, SLA and team workload at a glance." />

      {summary.loading && <LoadingState label="Loading summary…" />}
      {summary.error && <ErrorState message={summary.error} onRetry={summary.reload} />}
      {summary.data && (
        <>
          <div className="stats">
            <Stat label="Total work orders" value={summary.data.totalWorkOrders} />
            <Stat label="Open" value={summary.data.openWorkOrders} />
            <Stat label="Completed" value={summary.data.completedWorkOrders} tone="good" />
            <Stat label="Overdue" value={summary.data.overdueWorkOrders} tone={summary.data.overdueWorkOrders > 0 ? 'bad' : undefined} />
            <Stat label="At risk" value={summary.data.sla.atRisk} tone={summary.data.sla.atRisk > 0 ? 'warn' : undefined} />
            <Stat
              label="SLA compliance"
              value={summary.data.sla.compliancePercent === null ? '—' : `${summary.data.sla.compliancePercent}%`}
            />
          </div>
          {summary.data.totalWorkOrders === 0 ? (
            <EmptyState
              title="No work orders yet"
              hint="Numbers appear here once work orders are created."
              action={
                <Link className="btn" to={`${base}/work-orders`}>
                  Go to work orders
                </Link>
              }
            />
          ) : (
            <section className="card">
              <h2>By status</h2>
              <ul className="status-counts">
                {WORK_ORDER_STATUSES.map((s) => (
                  <li key={s}>
                    <span>{statusLabel(s)}</span>
                    <strong>{summary.data!.countsByStatus[s] ?? 0}</strong>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </>
      )}

      {sla.loading && <LoadingState label="Loading SLA report…" />}
      {sla.error && <ErrorState message={sla.error} onRetry={sla.reload} />}
      {sla.data && (
        <div className="two-col">
          <SlaList title="Overdue" items={sla.data.breachedWorkOrders} kind="breached" base={base} />
          <SlaList title="At risk" items={sla.data.atRiskWorkOrders} kind="risk" base={base} />
        </div>
      )}

      <section className="card">
        <h2>Technician workload</h2>
        {workload.loading && <LoadingState label="Loading workload…" />}
        {workload.error && <ErrorState message={workload.error} onRetry={workload.reload} />}
        {workload.data && workload.data.technicians.length === 0 && (
          <EmptyState title="No technicians yet" hint="Technicians appear once users with that role exist." />
        )}
        {workload.data && workload.data.technicians.length > 0 && (
          <>
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Technician</th>
                    <th className="num">Assigned</th>
                    <th className="num">In progress</th>
                    <th className="num">On hold</th>
                    <th className="num">Overdue</th>
                    <th className="num">At risk</th>
                    <th className="num">Completed</th>
                  </tr>
                </thead>
                <tbody>
                  {workload.data.technicians.map((t) => (
                    <tr key={t.technicianId}>
                      <td data-label="Technician">
                        <strong>{t.technician}</strong>
                      </td>
                      <td data-label="Assigned" className="num">
                        {t.assigned}
                      </td>
                      <td data-label="In progress" className="num">
                        {t.inProgress}
                      </td>
                      <td data-label="On hold" className="num">
                        {t.onHold}
                      </td>
                      <td data-label="Overdue" className={t.overdue > 0 ? 'num error-text' : 'num'}>
                        {t.overdue}
                      </td>
                      <td data-label="At risk" className="num">
                        {t.atRisk}
                      </td>
                      <td data-label="Completed" className="num">
                        {t.completed}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="muted small">Open work with no technician: {workload.data.unassignedOpen}</p>
          </>
        )}
      </section>
    </>
  );
}

