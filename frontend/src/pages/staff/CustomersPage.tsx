import { useState } from 'react';
import { deleteCustomer, listCustomers } from '../../api/customers';
import CustomerFormModal from '../../components/forms/CustomerFormModal';
import { ConfirmDialog, EmptyState, ErrorState, LoadingState, PageHeader, Pagination } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { useDebounced } from '../../hooks/useDebounced';
import { formatDateTime } from '../../lib/format';
import type { Customer } from '../../types/domain';

/** Customers list + create/edit/delete. Delete is MANAGER-only on the backend, so `canDelete` hides the button elsewhere. */
export default function CustomersPage({ canDelete }: { canDelete: boolean }) {
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const debounced = useDebounced(search);
  const [editing, setEditing] = useState<Customer | 'new' | null>(null);
  const [deleting, setDeleting] = useState<Customer | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const list = useAsync(
    () => listCustomers({ search: debounced, page, size: 20, sortBy: 'companyName', direction: 'asc' }),
    [debounced, page],
    'Could not load customers',
  );

  return (
    <>
      <PageHeader
        title="Customers"
        subtitle="Companies that raise work."
        actions={
          <button type="button" className="btn" onClick={() => setEditing('new')}>
            New customer
          </button>
        }
      />
      <div className="toolbar">
        <input
          type="search"
          placeholder="Search by company name"
          aria-label="Search customers"
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(0);
          }}
        />
      </div>
      {notice && (
        <div className="alert alert-success" role="status">
          {notice}
        </div>
      )}

      {list.loading && <LoadingState label="Loading customers…" />}
      {list.error && <ErrorState message={list.error} onRetry={list.reload} />}
      {list.data && list.data.content.length === 0 && (
        <EmptyState
          title={debounced ? 'No customers match your search' : 'No customers yet'}
          hint={debounced ? 'Try a different name.' : 'Create the first customer to start adding sites.'}
        />
      )}
      {list.data && list.data.content.length > 0 && (
        <>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Company</th>
                  <th>Contact email</th>
                  <th>Created</th>
                  <th aria-label="Actions" />
                </tr>
              </thead>
              <tbody>
                {list.data.content.map((c) => (
                  <tr key={c.id}>
                    <td data-label="Company">
                      <strong>{c.companyName}</strong>
                    </td>
                    <td data-label="Contact email">{c.contactEmail ?? '—'}</td>
                    <td data-label="Created">{formatDateTime(c.createdAt)}</td>
                    <td className="row-actions">
                      <button type="button" className="btn btn-secondary btn-sm" onClick={() => setEditing(c)}>
                        Edit
                      </button>
                      {canDelete && (
                        <button type="button" className="btn btn-danger-outline btn-sm" onClick={() => setDeleting(c)}>
                          Delete
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={list.data.page} totalPages={list.data.totalPages} totalElements={list.data.totalElements} onPage={setPage} />
        </>
      )}

      {editing && (
        <CustomerFormModal
          customer={editing === 'new' ? undefined : editing}
          onClose={() => setEditing(null)}
          onSaved={(saved) => {
            setEditing(null);
            setNotice(`Saved ${saved.companyName}.`);
            list.reload();
          }}
        />
      )}
      {deleting && (
        <ConfirmDialog
          title="Delete customer"
          danger
          confirmLabel="Delete customer"
          message={
            <p>
              Delete <strong>{deleting.companyName}</strong>? A customer that still has sites or work orders cannot be deleted.
            </p>
          }
          onCancel={() => setDeleting(null)}
          onConfirm={async () => {
            await deleteCustomer(deleting.id);
            setDeleting(null);
            setNotice(`Deleted ${deleting.companyName}.`);
            if (list.data && list.data.content.length === 1 && page > 0) setPage(page - 1);
            else list.reload();
          }}
        />
      )}
    </>
  );
}
