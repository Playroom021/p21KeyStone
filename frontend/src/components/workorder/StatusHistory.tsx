import { formatDateTime } from '../../lib/format';
import type { StatusHistoryEntry } from '../../types/domain';
import { EmptyState, StatusBadge } from '../ui';

/** Chronological status timeline (the backend returns it oldest first). */
export default function StatusHistory({ history }: { history: StatusHistoryEntry[] }) {
  if (history.length === 0) return <EmptyState title="No history yet" />;
  return (
    <ol className="timeline">
      {history.map((h, i) => (
        <li key={`${h.changedAt}-${i}`}>
          <div className="timeline-head">
            {h.fromStatus ? (
              <>
                <StatusBadge status={h.fromStatus} /> <span aria-label="to">→</span> <StatusBadge status={h.toStatus} />
              </>
            ) : (
              <>
                <span className="muted">Created as</span> <StatusBadge status={h.toStatus} />
              </>
            )}
          </div>
          {h.note && <p className="prewrap">{h.note}</p>}
          <p className="muted small">
            {formatDateTime(h.changedAt)} · {h.changedByEmail} ({h.changedByRole.toLowerCase()})
          </p>
        </li>
      ))}
    </ol>
  );
}
