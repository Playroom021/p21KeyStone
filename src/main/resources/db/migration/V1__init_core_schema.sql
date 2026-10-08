-- ============================================================================
-- KEYSTONE - V1: initial core schema
-- Creates the tables backing the existing JPA entities (Customer, Site,
-- User, WorkOrder, WorkOrderStatusHistory). Column names/types/constraints
-- mirror the current entity mappings exactly, using Spring's default
-- camelCase -> snake_case column naming.
--
-- Scope note: this migration ONLY creates the tables needed by the entities
-- that already exist in the codebase as of this step. It intentionally does
-- NOT add tables for Parts, TimeLog, SLA tracking, or any auth/JWT-specific
-- tables beyond app_users - those are out of scope until their own step.
-- ============================================================================

-- ---- customers ----
CREATE TABLE customers (
    id            BIGSERIAL PRIMARY KEY,
    company_name  VARCHAR(150) NOT NULL,
    contact_email VARCHAR(150),
    created_at    TIMESTAMPTZ  NOT NULL
);

-- ---- sites ----
CREATE TABLE sites (
    id           BIGSERIAL PRIMARY KEY,
    customer_id  BIGINT       NOT NULL REFERENCES customers (id),
    name         VARCHAR(150) NOT NULL,
    address_line VARCHAR(255),
    city         VARCHAR(100),
    created_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_sites_customer_id ON sites (customer_id);

-- ---- app_users ----
-- Table name "app_users" (not "users") to avoid the reserved word, matching
-- the existing @Table(name = "app_users") mapping on the User entity.
CREATE TABLE app_users (
    id          BIGSERIAL PRIMARY KEY,
    full_name   VARCHAR(100)  NOT NULL,
    email       VARCHAR(150)  NOT NULL,
    password    VARCHAR(255)  NOT NULL,
    role        VARCHAR(30)   NOT NULL
                    CHECK (role IN ('MANAGER', 'LOCAL_CUSTOMER', 'LOCAL_WORKER', 'DEVELOPER')),
    customer_id BIGINT        REFERENCES customers (id),
    created_at  TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_app_users_email UNIQUE (email)
);

CREATE INDEX idx_app_users_customer_id ON app_users (customer_id);

-- ---- work_orders ----
CREATE TABLE work_orders (
    id                     BIGSERIAL PRIMARY KEY,
    code                   VARCHAR(20)   NOT NULL,
    title                  VARCHAR(200)  NOT NULL,
    description            VARCHAR(2000),
    customer_id            BIGINT        NOT NULL REFERENCES customers (id),
    site_id                BIGINT        NOT NULL REFERENCES sites (id),
    priority               VARCHAR(20)   NOT NULL
                               CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    status                 VARCHAR(20)   NOT NULL
                               CHECK (status IN ('NEW', 'ASSIGNED', 'IN_PROGRESS', 'ON_HOLD', 'RESOLVED', 'CLOSED', 'CANCELLED')),
    assigned_technician_id BIGINT        REFERENCES app_users (id),
    sla_due_at             TIMESTAMPTZ,
    created_at             TIMESTAMPTZ   NOT NULL,
    updated_at             TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_work_orders_code UNIQUE (code)
);

CREATE INDEX idx_work_orders_customer_id ON work_orders (customer_id);
CREATE INDEX idx_work_orders_site_id ON work_orders (site_id);
CREATE INDEX idx_work_orders_assigned_technician_id ON work_orders (assigned_technician_id);
CREATE INDEX idx_work_orders_status ON work_orders (status);

-- ---- work_order_status_history ----
CREATE TABLE work_order_status_history (
    id                BIGSERIAL PRIMARY KEY,
    work_order_id     BIGINT       NOT NULL REFERENCES work_orders (id),
    from_status       VARCHAR(20)
                          CHECK (from_status IN ('NEW', 'ASSIGNED', 'IN_PROGRESS', 'ON_HOLD', 'RESOLVED', 'CLOSED', 'CANCELLED')),
    to_status         VARCHAR(20)  NOT NULL
                          CHECK (to_status IN ('NEW', 'ASSIGNED', 'IN_PROGRESS', 'ON_HOLD', 'RESOLVED', 'CLOSED', 'CANCELLED')),
    note              VARCHAR(500),
    changed_by_email  VARCHAR(150),
    changed_by_role   VARCHAR(30),
    changed_at        TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_wosh_work_order_id ON work_order_status_history (work_order_id);
