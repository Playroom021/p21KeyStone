-- ============================================================================
-- KEYSTONE - V4: Parts inventory, Part usage on work orders, Time logs (Step 6)
--
-- Stock can never go negative: enforced by the CHECK below AND by the
-- conditional UPDATE used by PartRepository.decrementStock (see HANDOFF.md).
-- ============================================================================

CREATE TABLE parts (
    id               BIGSERIAL PRIMARY KEY,
    sku              VARCHAR(50)    NOT NULL,
    name             VARCHAR(150)   NOT NULL,
    description      VARCHAR(500),
    unit             VARCHAR(20)    NOT NULL,
    quantity_on_hand INTEGER        NOT NULL,
    reorder_level    INTEGER        NOT NULL,
    unit_cost        NUMERIC(12, 2) NOT NULL,
    created_at       TIMESTAMPTZ    NOT NULL,
    updated_at       TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_parts_sku UNIQUE (sku),
    CONSTRAINT ck_parts_quantity_non_negative CHECK (quantity_on_hand >= 0),
    CONSTRAINT ck_parts_reorder_non_negative CHECK (reorder_level >= 0),
    CONSTRAINT ck_parts_unit_cost_non_negative CHECK (unit_cost >= 0)
);

CREATE TABLE part_usages (
    id               BIGSERIAL PRIMARY KEY,
    work_order_id    BIGINT         NOT NULL REFERENCES work_orders (id),
    part_id          BIGINT         NOT NULL REFERENCES parts (id),
    quantity         INTEGER        NOT NULL,
    unit_cost_at_use NUMERIC(12, 2) NOT NULL,
    note             VARCHAR(500),
    used_by_email    VARCHAR(150),
    used_by_role     VARCHAR(30),
    used_at          TIMESTAMPTZ    NOT NULL,
    CONSTRAINT ck_part_usages_quantity_positive CHECK (quantity > 0)
);

CREATE INDEX idx_part_usages_work_order_id ON part_usages (work_order_id);
CREATE INDEX idx_part_usages_part_id ON part_usages (part_id);

CREATE TABLE time_logs (
    id            BIGSERIAL PRIMARY KEY,
    work_order_id BIGINT       NOT NULL REFERENCES work_orders (id),
    technician_id BIGINT       NOT NULL REFERENCES app_users (id),
    started_at    TIMESTAMPTZ  NOT NULL,
    ended_at      TIMESTAMPTZ  NOT NULL,
    minutes       INTEGER      NOT NULL,
    note          VARCHAR(500),
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_time_logs_minutes_positive CHECK (minutes > 0),
    CONSTRAINT ck_time_logs_range CHECK (ended_at > started_at)
);

CREATE INDEX idx_time_logs_work_order_id ON time_logs (work_order_id);
CREATE INDEX idx_time_logs_technician_id ON time_logs (technician_id);
