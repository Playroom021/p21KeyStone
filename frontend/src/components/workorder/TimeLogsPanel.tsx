import { useState } from 'react';
import type { FormEvent } from 'react';
import { createTimeLog, deleteTimeLog, listTimeLogs } from '../../api/workOrders';
import { getErrorMessage } from '../../api/client';
import { useAsync } from '../../hooks/useAsync';
import { formatDateTime, formatMinutes, localInputToIso, toLocalInputValue } from '../../lib/format';
import { blankToNull, hasErrors, validateTimeLog } from '../../lib/validation';
import type { FieldErrors } from '../../lib/validation';
import type { TimeLog } from '../../types/domain';
import { Alert, ConfirmDialog, EmptyState, ErrorState, Field, LoadingState } from '../ui';

function defaultRange() {
  const end = new Date();
  end.setSeconds(0, 0);
  const start = new Date(end.getTime() - 60 * 60 * 1000);
  return { start: toLocalInputValue(start), end: toLocalInputValue(end) };
}

/**
 * Time logged on a work order. `canAdd` shows the form (technician on IN_PROGRESS / ON_HOLD / COMPLETED);
 * `deletableTechnicianId` is the technician whose own entries may be deleted (null = read-only).
 */
export default function TimeLogsPanel({
  workOrderId,
  canAdd,
  deletableTechnicianId,
}: {
  workOrderId: number;
  canAdd: boolean;
  deletableTechnicianId: number | null;
}) {
  const logs = useAsync(() => listTimeLogs(workOrderId), [workOrderId], 'Could not load time logs');
  const initial = defaultRange();
  const [startedAt, setStartedAt] = useState(initial.start);
  const [endedAt, setEndedAt] = useState(initial.end);
  const [note, setNote] = useState('');
  const [errors, setErrors] = useState<FieldErrors>({});
  const [apiError, setApiError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [removing, setRemoving] = useState<TimeLog | null>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setNotice(null);
    const found = validateTimeLog({ startedAt, endedAt, note });
    setErrors(found);
    if (hasErrors(found)) return;
    const startIso = localInputToIso(startedAt);
    const endIso = localInputToIso(endedAt);
    if (!startIso || !endIso) return;
    setBusy(true);
    setApiError(null);
    try {
      const saved = await createTimeLog(workOrderId, { startedAt: startIso, endedAt: endIso, note: blankToNull(note) });
      setNotice(`Logged ${formatMinutes(saved.minutes)}.`);
      setNote('');
      const next = defaultRange();
      setStartedAt(next.start);
      setEndedAt(next.end);
      logs.reload();
    } catch (err) {
      setApiError(getErrorMessage(err, 'Could not save the time log'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="panel">
      {logs.loading && <LoadingState label="Loading time logs…" />}
      {logs.error && <ErrorState message={logs.error} onRetry={logs.reload} />}
      {logs.data && logs.data.entries.length === 0 && (
        <EmptyState title="No time logged yet" hint={canAdd ? 'Add the time you spent on this job.' : undefined} />
      )}
      {logs.data && logs.data.entries.length > 0 && (
        <>
          <ul className="item-list">
            {logs.data.entries.map((t) => (
              <li key={t.id} className="item">
                <div className="item-main">
                  <strong>{formatMinutes(t.minutes)}</strong>
                  <span className="muted small">
                    {formatDateTime(t.startedAt)} → {formatDateTime(t.endedAt)}
                  </span>
                  {t.note && <span className="prewrap small">{t.note}</span>}
                  <span className="muted small">{t.technician}</span>
                </div>
                {deletableTechnicianId !== null && t.technicianId === deletableTechnicianId && (
                  <button type="button" className="btn btn-danger-outline btn-sm" onClick={() => setRemoving(t)}>
                    Delete
                  </button>
                )}
              </li>
            ))}
          </ul>
          <p className="total">
            Total time: <strong>{formatMinutes(logs.data.totalMinutes)}</strong>
          </p>
        </>
      )}

      {canAdd && (
        <form onSubmit={submit} noValidate className="form subform">
          <h3>Log time</h3>
          <Field label="Started" error={errors.startedAt}>
            {(p) => <input {...p} type="datetime-local" value={startedAt} onChange={(e) => setStartedAt(e.target.value)} />}
          </Field>
          <Field label="Ended" error={errors.endedAt}>
            {(p) => <input {...p} type="datetime-local" value={endedAt} onChange={(e) => setEndedAt(e.target.value)} />}
          </Field>
          <Field label="Note (optional)" error={errors.note}>
            {(p) => <textarea {...p} rows={2} value={note} maxLength={600} onChange={(e) => setNote(e.target.value)} />}
          </Field>
          {notice && <Alert kind="success">{notice}</Alert>}
          {apiError && <Alert>{apiError}</Alert>}
          <button type="submit" className="btn btn-block" disabled={busy}>
            {busy ? 'Saving…' : 'Save time log'}
          </button>
        </form>
      )}

      {removing && (
        <ConfirmDialog
          title="Delete time log"
          danger
          confirmLabel="Delete time log"
          message={<p>Delete the {formatMinutes(removing.minutes)} entry? This cannot be undone.</p>}
          onCancel={() => setRemoving(null)}
          onConfirm={async () => {
            await deleteTimeLog(workOrderId, removing.id);
            setRemoving(null);
            setNotice(null);
            logs.reload();
          }}
        />
      )}
    </div>
  );
}
