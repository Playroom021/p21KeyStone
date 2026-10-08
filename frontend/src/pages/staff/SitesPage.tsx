import { useState } from 'react';
import { listCustomers } from '../../api/customers';
import { deleteSite, listSites } from '../../api/sites';
import SiteFormModal from '../../components/forms/SiteFormModal';
import { ConfirmDialog, EmptyState, ErrorState, LoadingState, PageHeader, Pagination } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { useDebounced } from '../../hooks/useDebounced';
import { formatDateTime } from '../../lib/format';
import type { Site } from '../../types/domain';

/** Sites list (all customers, optional customer filter) + create/edit/delete. Delete is MANAGER-only. */
export default function SitesPage({ canDelete }: { canDelete: boolean }) {
  const [search, setSearch] = useState('');
  const [customerId, setCustomerId] = useState('');
  const [page, setPage] = useState(0);
  const debounced = useDebounced(search);
  const [editing, setEditing] = useState<Site | 'new' | null>(null);
  const [deleting, setDeleting] = useState<Site | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const customers = useAsync(() => listCustomers({ size: 100, sortBy: 'companyName' }), [], 'Could not load customers');
  const list = useAsync(
    () =>
      listSites({
        customerId: customerId ? Number(customerId) : undefined,
        search: debounced,
        page,
        size: 20,
        sortBy: 'name',
        direction: 'asc',
      }),
    [debounced, customerId, page],
    'Could not load sites',
  );

  return (
    <>
      <PageHeader
        title="Sites"
        subtitle="Locations where work is carried out."
        actions={
          <button type="button" className="btn" onClick={() => setEditing('new')}>
            New site
          </button>
        }
      />
      <div className="toolbar">
        <input
          type="search"
          placeholder="Search by site name"
          aria-label="Search sites"
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(0);
          }}
        />
        <select
          aria-label="Filter by customer"
          value={customerId}
          onChange={(e) => {
            setCustomerId(e.target.value);
            setPage(0);
          }}
        >
          <option value="">All customers</option>
          {customers.data?.content.map((c) => (
            <option key={c.id} value={c.id}>
              {c.companyName}
            </option>
          ))}
        </select>
      </div>
      {notice && (
        <div className="alert alert-success" role="status">
          {notice}
        </div>
      )}

      {list.loading && <LoadingState label="Loading sites…" />}
      {list.error && <ErrorState message={list.error} onRetry={list.reload} />}
      {list.data && list.data.content.length === 0 && (
        <EmptyState
          title={debounced || customerId ? 'No sites match your filters' : 'No sites yet'}
          hint={debounced || customerId ? 'Clear the search or customer filter.' : 'Add a site for one of your customers.'}
        />
      )}
      {list.data && list.data.content.length > 0 && (
        <>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Site</th>
                  <th>Customer</th>
                  <th>Address</th>
                  <th>Created</th>
                  <th aria-label="Actions" />
                </tr>
              </thead>
              <tbody>
                {list.data.content.map((s) => (
                  <tr key={s.id}>
                    <td data-label="Site">
                      <strong>{s.name}</strong>
                    </td>
                    <td data-label="Customer">{s.customerName}</td>
                    <td data-label="Address">{[s.addressLine, s.city].filter(Boolean).join(', ') || '—'}</td>
                    <td data-label="Created">{formatDateTime(s.createdAt)}</td>
                    <td className="row-actions">
                      <button type="button" className="btn btn-secondary btn-sm" onClick={() => setEditing(s)}>
                        Edit
                      </button>
                      {canDelete && (
                        <button type="button" className="btn btn-danger-outline btn-sm" onClick={() => setDeleting(s)}>
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
        <SiteFormModal
          mode="staff"
          site={editing === 'new' ? undefined : editing}
          onClose={() => setEditing(null)}
          onSaved={(saved) => {
            setEditing(null);
            setNotice(`Saved ${saved.name}.`);
            list.reload();
          }}
        />
      )}
      {deleting && (
        <ConfirmDialog
          title="Delete site"
          danger
          confirmLabel="Delete site"
          message={
            <p>
              Delete <strong>{deleting.name}</strong> ({deleting.customerName})? A site that has work orders cannot be deleted.
            </p>
          }
          onCancel={() => setDeleting(null)}
          onConfirm={async () => {
            await deleteSite(deleting.id);
            setDeleting(null);
            setNotice(`Deleted ${deleting.name}.`);
            if (list.data && list.data.content.length === 1 && page > 0) setPage(page - 1);
            else list.reload();
          }}
        />
      )}
    </>
  );
}
