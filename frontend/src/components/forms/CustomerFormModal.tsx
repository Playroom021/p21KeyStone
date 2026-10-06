import { useState } from 'react';
import type { FormEvent } from 'react';
import { createCustomer, updateCustomer } from '../../api/customers';
import { getErrorMessage } from '../../api/client';
import { blankToNull, hasErrors, validateCustomer } from '../../lib/validation';
import type { FieldErrors } from '../../lib/validation';
import type { Customer } from '../../types/domain';
import { Alert, Field, Modal } from '../ui';

/** Create (no `customer`) or edit a customer. Calls the API itself and reports the saved record. */
export default function CustomerFormModal({
  customer,
  onSaved,
  onClose,
}: {
  customer?: Customer;
  onSaved: (saved: Customer) => void;
  onClose: () => void;
}) {
  const [companyName, setCompanyName] = useState(customer?.companyName ?? '');
  const [contactEmail, setContactEmail] = useState(customer?.contactEmail ?? '');
  const [errors, setErrors] = useState<FieldErrors>({});
  const [apiError, setApiError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    const found = validateCustomer({ companyName, contactEmail });
    setErrors(found);
    if (hasErrors(found)) return;
    setBusy(true);
    setApiError(null);
    const body = { companyName: companyName.trim(), contactEmail: blankToNull(contactEmail) };
    try {
      const saved = customer ? await updateCustomer(customer.id, body) : await createCustomer(body);
      onSaved(saved);
    } catch (err) {
      setApiError(getErrorMessage(err, 'Could not save the customer'));
      setBusy(false);
    }
  }

  return (
    <Modal title={customer ? 'Edit customer' : 'New customer'} onClose={onClose}>
      <form onSubmit={submit} noValidate>
        <Field label="Company name" error={errors.companyName}>
          {(p) => <input {...p} value={companyName} maxLength={150} onChange={(e) => setCompanyName(e.target.value)} />}
        </Field>
        <Field label="Contact email (optional)" error={errors.contactEmail}>
          {(p) => (
            <input {...p} type="email" inputMode="email" value={contactEmail} onChange={(e) => setContactEmail(e.target.value)} />
          )}
        </Field>
        {apiError && <Alert>{apiError}</Alert>}
        <div className="modal-actions">
          <button type="button" className="btn btn-secondary" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button type="submit" className="btn" disabled={busy}>
            {busy ? 'Saving…' : 'Save customer'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
