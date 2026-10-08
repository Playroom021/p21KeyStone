import { useState } from 'react';
import type { FormEvent } from 'react';
import { listCustomers } from '../../api/customers';
import { createMySite, createSite, updateSite } from '../../api/sites';
import { getErrorMessage } from '../../api/client';
import { useAsync } from '../../hooks/useAsync';
import { blankToNull, hasErrors, validateSite } from '../../lib/validation';
import type { FieldErrors } from '../../lib/validation';
import type { Customer, PageResponse, PortalSite, Site } from '../../types/domain';
import { Alert, Field, LoadingState, Modal } from '../ui';

type Props =
  | { mode: 'staff'; site?: Site; onSaved: (saved: Site) => void; onClose: () => void }
  | { mode: 'portal'; onSaved: (saved: PortalSite) => void; onClose: () => void };

/**
 * Site form. `staff` mode creates under a chosen customer (POST /api/customers/{id}/sites) or edits
 * (PUT /api/sites/{id}; the customer is fixed). `portal` mode is the CUSTOMER's own add-site form.
 */
export default function SiteFormModal(props: Props) {
  const site = props.mode === 'staff' ? props.site : undefined;
  const needsCustomer = props.mode === 'staff' && !site;

  const customers = useAsync<PageResponse<Customer> | null>(
    () => (needsCustomer ? listCustomers({ size: 100, sortBy: 'companyName' }) : Promise.resolve(null)),
    [needsCustomer],
    'Could not load customers',
  );

  const [customerId, setCustomerId] = useState('');
  const [name, setName] = useState(site?.name ?? '');
  const [addressLine, setAddressLine] = useState(site?.addressLine ?? '');
  const [city, setCity] = useState(site?.city ?? '');
  const [errors, setErrors] = useState<FieldErrors>({});
  const [apiError, setApiError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    const found = validateSite({ customerId, name, addressLine, city }, needsCustomer);
    setErrors(found);
    if (hasErrors(found)) return;
    setBusy(true);
    setApiError(null);
    const body = { name: name.trim(), addressLine: blankToNull(addressLine), city: blankToNull(city) };
    try {
      if (props.mode === 'portal') {
        props.onSaved(await createMySite(body));
      } else if (site) {
        props.onSaved(await updateSite(site.id, body));
      } else {
        props.onSaved(await createSite(Number(customerId), body));
      }
    } catch (err) {
      setApiError(getErrorMessage(err, 'Could not save the site'));
      setBusy(false);
    }
  }

  const title = props.mode === 'portal' ? 'Add a site' : site ? 'Edit site' : 'New site';

  return (
    <Modal title={title} onClose={props.onClose}>
      <form onSubmit={submit} noValidate>
        {needsCustomer && (
          <>
            {customers.loading && <LoadingState label="Loading customers…" />}
            {customers.error && <Alert>{customers.error}</Alert>}
            {customers.data && (
              <Field label="Customer" error={errors.customerId}>
                {(p) => (
                  <select {...p} value={customerId} onChange={(e) => setCustomerId(e.target.value)}>
                    <option value="">Select a customer…</option>
                    {customers.data!.content.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.companyName}
                      </option>
                    ))}
                  </select>
                )}
              </Field>
            )}
            {customers.data && customers.data.content.length === 0 && (
              <p className="muted">There are no customers yet. Create a customer first.</p>
            )}
          </>
        )}
        {site && (
          <p className="muted">
            Customer: <strong>{site.customerName}</strong>
          </p>
        )}
        <Field label="Site name" error={errors.name}>
          {(p) => <input {...p} value={name} maxLength={150} onChange={(e) => setName(e.target.value)} />}
        </Field>
        <Field label="Address (optional)" error={errors.addressLine}>
          {(p) => <input {...p} value={addressLine} maxLength={255} onChange={(e) => setAddressLine(e.target.value)} />}
        </Field>
        <Field label="City (optional)" error={errors.city}>
          {(p) => <input {...p} value={city} maxLength={100} onChange={(e) => setCity(e.target.value)} />}
        </Field>
        {apiError && <Alert>{apiError}</Alert>}
        <div className="modal-actions">
          <button type="button" className="btn btn-secondary" onClick={props.onClose} disabled={busy}>
            Cancel
          </button>
          <button type="submit" className="btn" disabled={busy || (needsCustomer && !customers.data)}>
            {busy ? 'Saving…' : 'Save site'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
