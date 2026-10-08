-- ============================================================================
-- KEYSTONE - V2: rename Role enum values (Step 3 - Authentication & Authorization)
--
-- The application's Role enum has been finalized to the 4 roles required by
-- this step: MANAGER, DISPATCHER, TECHNICIAN, CUSTOMER. V1 shipped with a
-- provisional set (MANAGER, LOCAL_CUSTOMER, LOCAL_WORKER, DEVELOPER) that is
-- being renamed here rather than edited in place in V1, per this project's
-- own migration rule (never edit a migration once shipped; add a new one).
--
-- Mapping applied to any existing rows:
--   LOCAL_CUSTOMER -> CUSTOMER
--   LOCAL_WORKER    -> TECHNICIAN
--   DEVELOPER       -> DISPATCHER
--   MANAGER         -> MANAGER (unchanged)
-- ============================================================================

-- Drop the old CHECK constraint before updating data, since the old
-- constraint would reject the new values mid-update otherwise.
ALTER TABLE app_users DROP CONSTRAINT app_users_role_check;

UPDATE app_users SET role = 'CUSTOMER'   WHERE role = 'LOCAL_CUSTOMER';
UPDATE app_users SET role = 'TECHNICIAN' WHERE role = 'LOCAL_WORKER';
UPDATE app_users SET role = 'DISPATCHER' WHERE role = 'DEVELOPER';

ALTER TABLE app_users
    ADD CONSTRAINT app_users_role_check
    CHECK (role IN ('MANAGER', 'DISPATCHER', 'TECHNICIAN', 'CUSTOMER'));

-- work_order_status_history.changed_by_role stores a free-text snapshot of
-- the role name at the time of the change (no CHECK constraint on it), so
-- old rows keep their historical value as-is; only new writes will use the
-- renamed roles going forward. This preserves an accurate audit trail
-- rather than rewriting history.
