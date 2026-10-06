import { useState } from 'react';
import type { FormEvent } from 'react';
import { getErrorMessage } from '../../api/client';
import { priorityLabel } from '../../lib/format';
import { blankToNull, hasErrors, validateWorkOrder } from '../../lib/validation';
import type { FieldErrors } from '../../lib/validation';
import { PRIORITIES } from '../../types/domain';
import type { Priority, WorkOrderInput } from '../../types/domain';
import { Alert, Field } from '../ui';

export interface SiteOption {
  id: number;
  label: string;
}

/**
 * Shared work-order form: staff "create" / "edit" and the customer's "raise request".
 * Validates, builds the request body and hands it to `onSubmit`; a thrown error is shown under the form.
 */
export default function WorkOrderForm({
  sites,
  initial,
  lockSite = false,
  submitLabel,
  onSubmit,
  onCancel,
}: {
  sites: SiteOption[];
  initial?: { siteId: number; title: string; description: string | null; priority: Priority };
  /** Editing: the site cannot change after creation (the backend ignores it). */
  lockSite?: boolean;
  submitLabel: string;
  onSubmit: (input: WorkOrderInput) => Promise<void>;
  onCancel?: () => void;
}) {
  const [siteId, setSiteId] = useState(initial ? String(initial.siteId) : '');
  const [title, setTitle] = useState(initial?.title ?? '');
  const [description, setDescription] = useState(initial?.description ?? '');
  const [priority, setPriority] = useState<string>(initial?.priority ?? 'MEDIUM');
  const [errors, setErrors] = useState<FieldErrors>({});
  const [apiError, setApiError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    const found = validateWorkOrder({ siteId, title, description, priority });
    setErrors(found);
    if (hasErrors(found)) return;
    setBusy(true);
    setApiError(null);
    try {
      await onSubmit({
        siteId: Number(siteId),
        title: title.trim(),
        description: blankToNull(description),
        priority: priority as Priority,
      });
    } catch (err) {
      setApiError(getErrorMessage(err, 'Could not save the work order'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} noValidate className="form">
      <Field label="Site" error={errors.siteId}>
        {(p) => (
          <select {...p} value={siteId} disabled={lockSite} onChange={(e) => setSiteId(e.target.value)}>
            <option value="">Select a site…</option>
            {sites.map((s) => (
              <option key={s.id} value={s.id}>
                {s.label}
              </option>
            ))}
          </select>
        )}
      </Field>
      <Field label="Title" error={errors.title}>
        {(p) => <input {...p} value={title} maxLength={200} onChange={(e) => setTitle(e.target.value)} />}
      </Field>
      <Field label="Description (optional)" error={errors.description} hint={`${description.length}/2000`}>
        {(p) => <textarea {...p} rows={4} value={description} maxLength={2100} onChange={(e) => setDescription(e.target.value)} />}
      </Field>
      <Field label="Priority" error={errors.priority}>
        {(p) => (
          <select {...p} value={priority} onChange={(e) => setPriority(e.target.value)}>
            {PRIORITIES.map((value) => (
              <option key={value} value={value}>
                {priorityLabel(value)}
              </option>
            ))}
          </select>
        )}
      </Field>
      {apiError && <Alert>{apiError}</Alert>}
      <div className="form-actions">
        {onCancel && (
          <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={busy}>
            Cancel
          </button>
        )}
        <button type="submit" className="btn" disabled={busy}>
          {busy ? 'Saving…' : submitLabel}
        </button>
      </div>
    </form>
  );
}
