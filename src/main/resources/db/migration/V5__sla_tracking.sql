-- ============================================================================
-- KEYSTONE - V5: SLA tracking (Step 7)
--
-- work_orders.sla_due_at already exists (V1). This adds the completion time
-- (needed to judge SLA for finished work) and the SLA state the scheduler
-- maintains. Existing rows are backfilled from the status history.
-- ============================================================================

ALTER TABLE work_orders ADD COLUMN completed_at     TIMESTAMPTZ;
ALTER TABLE work_orders ADD COLUMN sla_status       VARCHAR(20)
    CONSTRAINT work_orders_sla_status_check CHECK (sla_status IN ('ON_TRACK', 'AT_RISK', 'BREACHED'));
ALTER TABLE work_orders ADD COLUMN sla_at_risk_at   TIMESTAMPTZ;
ALTER TABLE work_orders ADD COLUMN sla_breached_at  TIMESTAMPTZ;

-- Backfill completed_at for work already COMPLETED/CLOSED: first transition to COMPLETED.
UPDATE work_orders w
SET completed_at = (
    SELECT MIN(h.changed_at)
    FROM work_order_status_history h
    WHERE h.work_order_id = w.id AND h.to_status = 'COMPLETED'
)
WHERE w.status IN ('COMPLETED', 'CLOSED');

-- Backfill the final SLA outcome for finished work. Open work is left NULL: the
-- scheduler evaluates it on its first run.
UPDATE work_orders
SET sla_status = CASE WHEN completed_at <= sla_due_at THEN 'ON_TRACK' ELSE 'BREACHED' END,
    sla_breached_at = CASE WHEN completed_at > sla_due_at THEN completed_at ELSE NULL END
WHERE status IN ('COMPLETED', 'CLOSED')
  AND completed_at IS NOT NULL
  AND sla_due_at IS NOT NULL;

CREATE INDEX idx_work_orders_sla_due_at ON work_orders (sla_due_at);
