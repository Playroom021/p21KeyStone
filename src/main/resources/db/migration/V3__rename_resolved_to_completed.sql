-- ============================================================================
-- KEYSTONE - V3: rename WorkOrderStatus.RESOLVED -> COMPLETED (Step 5 - Work
-- Order management + lifecycle)
--
-- V1 shipped work_orders.status / work_order_status_history.{from,to}_status
-- with a provisional status list that used RESOLVED. This step's spec calls
-- for COMPLETED instead, so it is renamed here (new migration, not an edit
-- to V1) following the same pattern as V2's role rename.
-- ============================================================================

ALTER TABLE work_orders DROP CONSTRAINT work_orders_status_check;
ALTER TABLE work_order_status_history DROP CONSTRAINT work_order_status_history_from_status_check;
ALTER TABLE work_order_status_history DROP CONSTRAINT work_order_status_history_to_status_check;

UPDATE work_orders SET status = 'COMPLETED' WHERE status = 'RESOLVED';
UPDATE work_order_status_history SET from_status = 'COMPLETED' WHERE from_status = 'RESOLVED';
UPDATE work_order_status_history SET to_status = 'COMPLETED' WHERE to_status = 'RESOLVED';

ALTER TABLE work_orders
    ADD CONSTRAINT work_orders_status_check
    CHECK (status IN ('NEW', 'ASSIGNED', 'IN_PROGRESS', 'ON_HOLD', 'COMPLETED', 'CLOSED', 'CANCELLED'));

ALTER TABLE work_order_status_history
    ADD CONSTRAINT work_order_status_history_from_status_check
    CHECK (from_status IN ('NEW', 'ASSIGNED', 'IN_PROGRESS', 'ON_HOLD', 'COMPLETED', 'CLOSED', 'CANCELLED'));

ALTER TABLE work_order_status_history
    ADD CONSTRAINT work_order_status_history_to_status_check
    CHECK (to_status IN ('NEW', 'ASSIGNED', 'IN_PROGRESS', 'ON_HOLD', 'COMPLETED', 'CLOSED', 'CANCELLED'));
