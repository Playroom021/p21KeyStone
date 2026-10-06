import { useState } from 'react';
import { deletePart, listParts } from '../../api/parts';
import PartFormModal from '../../components/forms/PartFormModal';
import { ConfirmDialog, EmptyState, ErrorState, LoadingState, PageHeader, Pagination } from '../../components/ui';
import { useAsync } from '../../hooks/useAsync';
import { useDebounced } from '../../hooks/useDebounced';
import { formatAmount } from '../../lib/format';
import type { Part } from '../../types/domain';

/** Parts / inventory (MANAGER). Create + edit are allowed to MANAGER and DISPATCHER by the backend; delete is MANAGER-only. */
export default function PartsPage() {
  const [search, setSearch] = useState('');
  const [lowStock, setLowStock] = useState(false);
  const [page, setPage] = useState(0);
  const debounced = useDebounced(search);
  const [editing, setEditing] = useState<Part | 'new' | null>(null);
  const [deleting, setDeleting] = useState<Part | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const list = useAsync(
    () => listParts({ search: debounced, lowStock: lowStock || undefined, page, size: 20, sortBy: 'name', direction: 'asc' }),
    [debounced, lowStock, page],
    'Could not load parts',
  );

  return (
    <>
      <PageHeader
        title="Parts & inventory"
        subtitle="Stock on hand and reorder levels."
        actions={
          <button type="button" className="btn" onClick={() => setEditing('new')}>
            New part
          </button>
        }
      />
      <div className="toolbar">
        <input
          type="search"
          placeholder="Search by SKU or name"
          aria-label="Search parts"
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(0);
          }}
        />
        <label className="check">
          <input
            type="checkbox"
            checked={lowStock}
            onChange={(e) => {
              setLowStock(e.target.checked);
              setPage(0);
            }}
          />
          Low stock only
        </label>
      </div>
      {notice && (
        <div className="alert alert-success" role="status">
          {notice}
        </div>
      )}

      {list.loading && <LoadingState label="Loading parts…" />}
      {list.error && <ErrorState message={list.error} onRetry={list.reload} />}
      {list.data && list.data.content.length === 0 && (
        <EmptyState
          title={debounced || lowStock ? 'No parts match your filters' : 'No parts yet'}
          hint={debounced || lowStock ? 'Clear the search or the low-stock filter.' : 'Add parts so technicians can record what they use.'}
        />
      )}
      {list.data && list.data.content.length > 0 && (
        <>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>SKU</th>
                  <th>Name</th>
                  <th className="num">On hand</th>
                  <th className="num">Reorder at</th>
                  <th className="num">Unit cost</th>
                  <th aria-label="Actions" />
                </tr>
              </thead>
              <tbody>
                {list.data.content.map((p) => (
                  <tr key={p.id}>
                    <td data-label="SKU">{p.sku}</td>
                    <td data-label="Name">
                      <strong>{p.name}</strong>
                      {p.lowStock && <span className="pill pill-warn">Low stock</span>}
                    </td>
                    <td data-label="On hand" className="num">
                      {p.quantityOnHand} {p.unit}
                    </td>
                    <td data-label="Reorder at" className="num">
                      {p.reorderLevel}
                    </td>
                    <td data-label="Unit cost" className="num">
                      {formatAmount(p.unitCost)}
                    </td>
                    <td className="row-actions">
                      <button type="button" className="btn btn-secondary btn-sm" onClick={() => setEditing(p)}>
                        Edit
                      </button>
                      <button type="button" className="btn btn-danger-outline btn-sm" onClick={() => setDeleting(p)}>
                        Delete
                      </button>
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
        <PartFormModal
          part={editing === 'new' ? undefined : editing}
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
          title="Delete part"
          danger
          confirmLabel="Delete part"
          message={
            <p>
              Delete <strong>{deleting.name}</strong> ({deleting.sku})? Parts already used on work orders cannot be deleted.
            </p>
          }
          onCancel={() => setDeleting(null)}
          onConfirm={async () => {
            await deletePart(deleting.id);
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
