import { useState } from 'react';
import type { FormEvent } from 'react';
import { fetchTechnicianWorkload } from '../../api/dashboard';
import { assignTechnician } from '../../api/workOrders';
import { getErrorMessage } from '../../api/client';
import { useAsync } from '../../hooks/useAsync';
import { validateNote } from '../../lib/validation';
import type { WorkOrderDetail } from '../../types/domain';
import { Alert, EmptyState, ErrorState, Field, LoadingState, Modal } from '../ui';

/** Assign a NEW work order to a technician (POST /api/work-orders/{id}/status with status ASSIGNED). */
export default function AssignTechnicianModal({
  workOrderId,
  workOrderCode,
  onAssigned,
  onClose,
}: {
  workOrderId: number;
  workOrderCode: string;
  onAssigned: (updated: WorkOrderDetail) => void;
  onClose: () => void;
}) {
  const workload = useAsync(fetchTechnicianWorkload, [], 'Could not load technicians');
  const [technicianId, setTechnicianId] = useState('');
  const [note, setNote] = useState('');
  const [fieldError, setFieldError] = useState<{ technician?: string; note?: string }>({});
  const [apiError, setApiError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    const errs: { technician?: string; note?: string } = {};
    if (!technicianId) errs.technician = 'Choose a technician';
    const noteProblem = validateNote(note);
    if (noteProblem) errs.note = noteProblem;
    setFieldError(errs);
    if (errs.technician || errs.note) return;
    setBusy(true);
    setApiError(null);
    try {
      onAssigned(await assignTechnician(workOrderId, Number(technicianId), note.trim()));
    } catch (err) {
      setApiError(getErrorMessage(err, 'Could not assign the technician'));
      setBusy(false);
    }
  }

  const technicians = workload.data?.technicians ?? [];

  return (
    <Modal title={`Assign technician · ${workOrderCode}`} onClose={onClose}>
      {workload.loading && <LoadingState label="Loading technicians…" />}
      {workload.error && <ErrorState message={workload.error} onRetry={workload.reload} />}
      {workload.data && technicians.length === 0 && (
        <EmptyState title="No technicians yet" hint="Create a user with the TECHNICIAN role first." />
      )}
      {workload.data && technicians.length > 0 && (
        <form onSubmit={submit} noValidate>
          <Field label="Technician" error={fieldError.technician}>
            {(p) => (
              <select {...p} value={technicianId} onChange={(e) => setTechnicianId(e.target.value)}>
                <option value="">Select a technician…</option>
                {technicians.map((t) => (
                  <option key={t.technicianId} value={t.technicianId}>
                    {t.technician} — {t.openTotal} open{t.overdue > 0 ? `, ${t.overdue} overdue` : ''}
                  </option>
                ))}
              </select>
            )}
          </Field>
          <Field label="Note (optional)" error={fieldError.note}>
            {(p) => <textarea {...p} rows={2} value={note} maxLength={600} onChange={(e) => setNote(e.target.value)} />}
          </Field>
          {apiError && <Alert>{apiError}</Alert>}
          <div className="modal-actions">
            <button type="button" className="btn btn-secondary" onClick={onClose} disabled={busy}>
              Cancel
            </button>
            <button type="submit" className="btn" disabled={busy}>
              {busy ? 'Assigning…' : 'Assign technician'}
            </button>
          </div>
        </form>
      )}
    </Modal>
  );
}
