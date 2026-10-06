import { useState } from 'react';
import type { FormEvent } from 'react';
import { listParts } from '../../api/parts';
import { listPartUsage, recordPartUsage, removePartUsage } from '../../api/workOrders';
import { getErrorMessage } from '../../api/client';
import { useAsync } from '../../hooks/useAsync';
import { formatAmount, formatDateTime } from '../../lib/format';
import { blankToNull, hasErrors, validatePartUsage } from '../../lib/validation';
import type { FieldErrors } from '../../lib/validation';
import type { PageResponse, Part, PartUsage } from '../../types/domain';
import { Alert, ConfirmDialog, EmptyState, ErrorState, Field, LoadingState } from '../ui';

/**
 * Parts used on a work order. `canEdit` (technician on an IN_PROGRESS / ON_HOLD job) adds the
 * "record part" form and the remove buttons; everyone else sees the list only.
 */
export default function PartUsagePanel({ workOrderId, canEdit }: { workOrderId: number; canEdit: boolean }) {
  const usage = useAsync(() => listPartUsage(workOrderId), [workOrderId], 'Could not load parts used');
  const parts = useAsync<PageResponse<Part> | null>(
    () => (canEdit ? listParts({ size: 100, sortBy: 'name' }) : Promise.resolve(null)),
    [canEdit],
    'Could not load the parts list',
  );

  const [partId, setPartId] = useState('');
  const [quantity, setQuantity] = useState('1');
  const [note, setNote] = useState('');
  const [errors, setErrors] = useState<FieldErrors>({});
  const [apiError, setApiError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [removing, setRemoving] = useState<PartUsage | null>(null);

  const chosen: Part | undefined = parts.data?.content.find((p) => String(p.id) === partId);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setNotice(null);
    const found = validatePartUsage({ partId, quantity, note }, chosen?.quantityOnHand);
    setErrors(found);
    if (hasErrors(found)) return;
    setBusy(true);
    setApiError(null);
    try {
      await recordPartUsage(workOrderId, {
        items: [{ partId: Number(partId), quantity: Number(quantity) }],
        note: blankToNull(note),
      });
      setNotice(`Recorded ${quantity} × ${chosen?.name ?? 'part'}.`);
      setPartId('');
      setQuantity('1');
      setNote('');
      usage.reload();
      parts.reload(); // stock changed
    } catch (err) {
      setApiError(getErrorMessage(err, 'Could not record the part'));
    } finally {
      setBusy(false);
    }
  }

  const total = (usage.data ?? []).reduce((sum, u) => sum + u.lineCost, 0);

  return (
    <div className="panel">
      {usage.loading && <LoadingState label="Loading parts used…" />}
      {usage.error && <ErrorState message={usage.error} onRetry={usage.reload} />}
      {usage.data && usage.data.length === 0 && (
        <EmptyState title="No parts used yet" hint={canEdit ? 'Record parts as you use them.' : undefined} />
      )}
      {usage.data && usage.data.length > 0 && (
        <>
          <ul className="item-list">
            {usage.data.map((u) => (
              <li key={u.id} className="item">
                <div className="item-main">
                  <strong>
                    {u.quantity} × {u.partName}
                  </strong>
                  <span className="muted small">
                    {u.partSku} · {formatAmount(u.lineCost)} ({formatAmount(u.unitCostAtUse)} each)
                  </span>
                  {u.note && <span className="prewrap small">{u.note}</span>}
                  <span className="muted small">
                    {formatDateTime(u.usedAt)} · {u.usedByEmail}
                  </span>
                </div>
                {canEdit && (
                  <button type="button" className="btn btn-danger-outline btn-sm" onClick={() => setRemoving(u)}>
                    Remove
                  </button>
                )}
              </li>
            ))}
          </ul>
          <p className="total">
            Total parts cost: <strong>{formatAmount(total)}</strong>
          </p>
        </>
      )}

      {canEdit && (
        <form onSubmit={submit} noValidate className="form subform">
          <h3>Record a part</h3>
          {parts.loading && <LoadingState label="Loading parts list…" />}
          {parts.error && <ErrorState message={parts.error} onRetry={parts.reload} />}
          {parts.data && parts.data.content.length === 0 && (
            <EmptyState title="No parts in inventory" hint="Ask a manager to add parts first." />
          )}
          {parts.data && parts.data.content.length > 0 && (
            <>
              <Field label="Part" error={errors.partId}>
                {(p) => (
                  <select {...p} value={partId} onChange={(e) => setPartId(e.target.value)}>
                    <option value="">Select a part…</option>
                    {parts.data!.content.map((part) => (
                      <option key={part.id} value={part.id} disabled={part.quantityOnHand === 0}>
                        {part.sku} — {part.name} ({part.quantityOnHand} {part.unit} in stock)
                      </option>
                    ))}
                  </select>
                )}
              </Field>
              <Field label="Quantity" error={errors.quantity}>
                {(p) => <input {...p} inputMode="numeric" value={quantity} onChange={(e) => setQuantity(e.target.value)} />}
              </Field>
              <Field label="Note (optional)" error={errors.note}>
                {(p) => <textarea {...p} rows={2} value={note} maxLength={600} onChange={(e) => setNote(e.target.value)} />}
              </Field>
              {notice && <Alert kind="success">{notice}</Alert>}
              {apiError && <Alert>{apiError}</Alert>}
              <button type="submit" className="btn btn-block" disabled={busy}>
                {busy ? 'Recording…' : 'Record part'}
              </button>
            </>
          )}
        </form>
      )}

      {removing && (
        <ConfirmDialog
          title="Remove part usage"
          confirmLabel="Remove and return to stock"
          danger
          message={
            <p>
              Remove {removing.quantity} × <strong>{removing.partName}</strong>? The quantity goes back into stock.
            </p>
          }
          onCancel={() => setRemoving(null)}
          onConfirm={async () => {
            await removePartUsage(workOrderId, removing.id);
            setRemoving(null);
            setNotice(null);
            usage.reload();
            parts.reload();
          }}
        />
      )}
    </div>
  );
}
