import { useEffect, useId, useRef, useState } from 'react';
import type { FormEvent, ReactNode } from 'react';
import { getErrorMessage } from '../api/client';
import { priorityLabel, slaLabel, statusLabel } from '../lib/format';
import { validateNote } from '../lib/validation';

// ---- Page chrome ----
export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <div className="page-header">
      <div>
        <h1>{title}</h1>
        {subtitle && <p className="muted">{subtitle}</p>}
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </div>
  );
}

// ---- Loading / error / empty states ----
export function LoadingState({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="state" role="status" aria-live="polite">
      <span className="spinner" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="state state-error" role="alert">
      <p>{message}</p>
      {onRetry && (
        <button type="button" className="btn btn-secondary" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  );
}

export function EmptyState({ title, hint, action }: { title: string; hint?: string; action?: ReactNode }) {
  return (
    <div className="state">
      <strong>{title}</strong>
      {hint && <p className="muted">{hint}</p>}
      {action}
    </div>
  );
}

export function Alert({ kind = 'error', children }: { kind?: 'error' | 'success'; children: ReactNode }) {
  return (
    <div className={kind === 'error' ? 'alert' : 'alert alert-success'} role={kind === 'error' ? 'alert' : 'status'}>
      {children}
    </div>
  );
}

// ---- Badges ----
export function StatusBadge({ status }: { status: string }) {
  return <span className={`pill status-${status.toLowerCase().replace(/_/g, '-')}`}>{statusLabel(status)}</span>;
}
export function PriorityBadge({ priority }: { priority: string }) {
  return <span className={`pill priority-${priority.toLowerCase()}`}>{priorityLabel(priority)}</span>;
}
export function SlaBadge({ status }: { status: string | null | undefined }) {
  if (!status) return <span className="muted">—</span>;
  return <span className={`pill sla-${status.toLowerCase().replace(/_/g, '-')}`}>{slaLabel(status)}</span>;
}

// ---- Pagination ----
export function Pagination({
  page,
  totalPages,
  totalElements,
  onPage,
}: {
  page: number;
  totalPages: number;
  totalElements: number;
  onPage: (page: number) => void;
}) {
  if (totalPages <= 1) return <p className="muted pager-count">{totalElements} total</p>;
  return (
    <div className="pager">
      <button type="button" className="btn btn-secondary" disabled={page <= 0} onClick={() => onPage(page - 1)}>
        Previous
      </button>
      <span className="muted">
        Page {page + 1} of {totalPages} · {totalElements} total
      </span>
      <button type="button" className="btn btn-secondary" disabled={page >= totalPages - 1} onClick={() => onPage(page + 1)}>
        Next
      </button>
    </div>
  );
}

// ---- Form field wrapper ----
export function Field({
  label,
  error,
  hint,
  children,
}: {
  label: string;
  error?: string;
  hint?: string;
  /** Render-prop so the control gets the generated id and aria attributes. */
  children: (props: { id: string; 'aria-invalid': boolean; 'aria-describedby'?: string }) => ReactNode;
}) {
  const id = useId();
  const msgId = `${id}-msg`;
  return (
    <div className={error ? 'field field-invalid' : 'field'}>
      <label htmlFor={id}>{label}</label>
      {children({ id, 'aria-invalid': Boolean(error), 'aria-describedby': error || hint ? msgId : undefined })}
      {error ? (
        <span id={msgId} className="field-error">
          {error}
        </span>
      ) : hint ? (
        <span id={msgId} className="field-hint">
          {hint}
        </span>
      ) : null}
    </div>
  );
}

// ---- Modal / sheet ----
export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  const titleId = useId();
  const panelRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const previouslyFocused = document.activeElement as HTMLElement | null;
    const first = panelRef.current?.querySelector<HTMLElement>('input, select, textarea, button:not(.modal-close)');
    first?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('keydown', onKey);
      previouslyFocused?.focus?.();
    };
    // Focus handling only on open/close.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal" role="dialog" aria-modal="true" aria-labelledby={titleId} ref={panelRef}>
        <div className="modal-head">
          <h2 id={titleId}>{title}</h2>
          <button type="button" className="modal-close" aria-label="Close" onClick={onClose}>
            ×
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}

/**
 * Confirmation dialog for a destructive or state-changing action. Runs `onConfirm`
 * (optionally with a note), shows the backend's error message inside the dialog if it fails,
 * and closes only on success.
 */
export function ConfirmDialog({
  title,
  message,
  confirmLabel,
  danger = false,
  noteLabel,
  onConfirm,
  onCancel,
}: {
  title: string;
  message?: ReactNode;
  confirmLabel: string;
  danger?: boolean;
  /** When set, shows an optional note box (max 500 chars) and passes it to onConfirm. */
  noteLabel?: string;
  onConfirm: (note: string) => Promise<void>;
  onCancel: () => void;
}) {
  const [note, setNote] = useState('');
  const [noteError, setNoteError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    const problem = noteLabel ? validateNote(note) : null;
    setNoteError(problem);
    if (problem) return;
    setBusy(true);
    setError(null);
    try {
      await onConfirm(note.trim());
    } catch (err) {
      setError(getErrorMessage(err, 'The action failed'));
      setBusy(false);
    }
  }

  return (
    <Modal title={title} onClose={busy ? () => undefined : onCancel}>
      <form onSubmit={submit} noValidate>
        {message && <div className="modal-body">{message}</div>}
        {noteLabel && (
          <Field label={noteLabel} error={noteError ?? undefined}>
            {(p) => <textarea {...p} rows={3} value={note} onChange={(e) => setNote(e.target.value)} maxLength={600} />}
          </Field>
        )}
        {error && <Alert>{error}</Alert>}
        <div className="modal-actions">
          <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={busy}>
            Cancel
          </button>
          <button type="submit" className={danger ? 'btn btn-danger' : 'btn'} disabled={busy}>
            {busy ? 'Working…' : confirmLabel}
          </button>
        </div>
      </form>
    </Modal>
  );
}
