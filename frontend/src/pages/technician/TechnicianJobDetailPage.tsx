import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getWorkOrder, performJobAction } from '../../api/workOrders';
import type { JobAction } from '../../api/workOrders';
import { useAuth } from '../../auth/AuthContext';
import PartUsagePanel from '../../components/workorder/PartUsagePanel';
import StatusHistory from '../../components/workorder/StatusHistory';
import TimeLogsPanel from '../../components/workorder/TimeLogsPanel';
import WorkOrderInfo from '../../components/workorder/WorkOrderInfo';
import { ConfirmDialog, ErrorState, LoadingState } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { useBasePath } from '../../hooks/useBasePath';
import { canChangeParts, canLogTime, JOB_ACTION_LABEL, technicianActions } from '../../lib/lifecycle';

type Tab = 'details' | 'parts' | 'time' | 'history';
const TABS: { id: Tab; label: string }[] = [
  { id: 'details', label: 'Details' },
  { id: 'parts', label: 'Parts' },
  { id: 'time', label: 'Time' },
  { id: 'history', label: 'History' },
];

const ACTION_HELP: Record<JobAction, string> = {
  start: 'Mark this job as in progress.',
  hold: 'Pause this job. The SLA clock keeps running while a job is on hold.',
  resume: 'Move this job back to in progress.',
  complete: 'Mark the work as finished. Log your time and parts first if you have not yet.',
};
const ACTION_DONE: Record<JobAction, string> = {
  start: 'Job started.',
  hold: 'Job put on hold.',
  resume: 'Job resumed.',
  complete: 'Job completed.',
};

/** Technician job screen: details, start/hold/resume/complete, parts used, time logs, status history. Mobile-first. */
export default function TechnicianJobDetailPage() {
  const { id } = useParams();
  const workOrderId = Number(id);
  const base = useBasePath();
  const { user } = useAuth();
  const [tab, setTab] = useState<Tab>('details');
  const [action, setAction] = useState<JobAction | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const detail = useAsync(() => getWorkOrder(workOrderId), [workOrderId], 'Could not load this job');

  if (!Number.isInteger(workOrderId) || workOrderId <= 0) {
    return <ErrorState message="That job link is not valid." />;
  }
  if (detail.loading) return <LoadingState label="Loading job…" />;
  if (detail.error || !detail.data || !user) {
    return (
      <>
        <ErrorState message={detail.error ?? 'Could not load this job'} onRetry={detail.reload} />
        <p>
          <Link to={`${base}/jobs`}>← Back to my jobs</Link>
        </p>
      </>
    );
  }

  const wo = detail.data;
  const actions = technicianActions(wo.status);

  return (
    <div className={actions.length > 0 ? 'has-action-bar' : undefined}>
      <p className="crumb">
        <Link to={`${base}/jobs`}>← My jobs</Link>
      </p>
      <h1 className="job-heading">
        {wo.code} · {wo.title}
      </h1>
      {notice && (
        <div className="alert alert-success" role="status">
          {notice}
        </div>
      )}

      <div className="segmented" role="tablist" aria-label="Job sections">
        {TABS.map((t) => (
          <button
            key={t.id}
            type="button"
            role="tab"
            aria-selected={tab === t.id}
            className={tab === t.id ? 'seg seg-on' : 'seg'}
            onClick={() => setTab(t.id)}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'details' && <WorkOrderInfo wo={wo} />}
      {tab === 'parts' && (
        <section className="card">
          <h2>Parts used</h2>
          <PartUsagePanel workOrderId={wo.id} canEdit={canChangeParts(wo.status)} />
        </section>
      )}
      {tab === 'time' && (
        <section className="card">
          <h2>Time logs</h2>
          <TimeLogsPanel
            workOrderId={wo.id}
            canAdd={canLogTime(wo.status)}
            deletableTechnicianId={canLogTime(wo.status) ? user.id : null}
          />
        </section>
      )}
      {tab === 'history' && (
        <section className="card">
          <h2>Status history</h2>
          <StatusHistory history={wo.history} />
        </section>
      )}

      {actions.length > 0 && (
        <div className="action-bar">
          {actions.map((a, i) => (
            <button
              key={a}
              type="button"
              className={i === 0 && a !== 'hold' ? 'btn btn-lg' : 'btn btn-secondary btn-lg'}
              onClick={() => setAction(a)}
            >
              {JOB_ACTION_LABEL[a]}
            </button>
          ))}
        </div>
      )}

      {action && (
        <ConfirmDialog
          title={`${JOB_ACTION_LABEL[action]} · ${wo.code}`}
          confirmLabel={JOB_ACTION_LABEL[action]}
          noteLabel="Note (optional)"
          message={<p>{ACTION_HELP[action]}</p>}
          onCancel={() => setAction(null)}
          onConfirm={async (note) => {
            const updated = await performJobAction(wo.id, action, note);
            detail.setData(updated);
            setAction(null);
            setNotice(ACTION_DONE[action]);
            // After completing, jump to Time: late time entries are still allowed on a COMPLETED job.
            if (action === 'complete') setTab('time');
          }}
        />
      )}
    </div>
  );
}
