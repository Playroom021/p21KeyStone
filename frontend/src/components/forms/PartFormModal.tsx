import { useState } from 'react';
import type { FormEvent } from 'react';
import { createPart, updatePart } from '../../api/parts';
import { getErrorMessage } from '../../api/client';
import { blankToNull, hasErrors, validatePart } from '../../lib/validation';
import type { FieldErrors, PartForm } from '../../lib/validation';
import type { Part } from '../../types/domain';
import { Alert, Field, Modal } from '../ui';

export default function PartFormModal({
  part,
  onSaved,
  onClose,
}: {
  part?: Part;
  onSaved: (saved: Part) => void;
  onClose: () => void;
}) {
  const [form, setForm] = useState<PartForm>({
    sku: part?.sku ?? '',
    name: part?.name ?? '',
    description: part?.description ?? '',
    unit: part?.unit ?? 'each',
    quantityOnHand: part ? String(part.quantityOnHand) : '0',
    reorderLevel: part ? String(part.reorderLevel) : '0',
    unitCost: part ? part.unitCost.toFixed(2) : '',
  });
  const [errors, setErrors] = useState<FieldErrors>({});
  const [apiError, setApiError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const set = (key: keyof PartForm) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: e.target.value }));

  async function submit(e: FormEvent) {
    e.preventDefault();
    const found = validatePart(form);
    setErrors(found);
    if (hasErrors(found)) return;
    setBusy(true);
    setApiError(null);
    const body = {
      sku: form.sku.trim(),
      name: form.name.trim(),
      description: blankToNull(form.description),
      unit: form.unit.trim(),
      quantityOnHand: Number(form.quantityOnHand),
      reorderLevel: Number(form.reorderLevel),
      unitCost: Number(form.unitCost),
    };
    try {
      onSaved(part ? await updatePart(part.id, body) : await createPart(body));
    } catch (err) {
      setApiError(getErrorMessage(err, 'Could not save the part'));
      setBusy(false);
    }
  }

  return (
    <Modal title={part ? 'Edit part' : 'New part'} onClose={onClose}>
      <form onSubmit={submit} noValidate>
        <div className="form-row">
          <Field label="SKU" error={errors.sku}>
            {(p) => <input {...p} value={form.sku} maxLength={50} onChange={set('sku')} />}
          </Field>
          <Field label="Unit (e.g. each, m, kg)" error={errors.unit}>
            {(p) => <input {...p} value={form.unit} maxLength={20} onChange={set('unit')} />}
          </Field>
        </div>
        <Field label="Name" error={errors.name}>
          {(p) => <input {...p} value={form.name} maxLength={150} onChange={set('name')} />}
        </Field>
        <Field label="Description (optional)" error={errors.description}>
          {(p) => <textarea {...p} rows={2} value={form.description} maxLength={600} onChange={set('description')} />}
        </Field>
        <div className="form-row">
          <Field label="Quantity on hand" error={errors.quantityOnHand}>
            {(p) => <input {...p} inputMode="numeric" value={form.quantityOnHand} onChange={set('quantityOnHand')} />}
          </Field>
          <Field label="Reorder level" error={errors.reorderLevel}>
            {(p) => <input {...p} inputMode="numeric" value={form.reorderLevel} onChange={set('reorderLevel')} />}
          </Field>
          <Field label="Unit cost" error={errors.unitCost}>
            {(p) => <input {...p} inputMode="decimal" value={form.unitCost} onChange={set('unitCost')} />}
          </Field>
        </div>
        {apiError && <Alert>{apiError}</Alert>}
        <div className="modal-actions">
          <button type="button" className="btn btn-secondary" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button type="submit" className="btn" disabled={busy}>
            {busy ? 'Saving…' : 'Save part'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
