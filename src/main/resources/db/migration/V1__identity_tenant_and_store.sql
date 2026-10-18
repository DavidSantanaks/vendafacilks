CREATE TABLE tenants (
  id UUID PRIMARY KEY,
  slug VARCHAR(63) NOT NULL UNIQUE,
  name VARCHAR(120) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,
  CHECK (slug ~ '^[a-z][a-z0-9-]{1,61}[a-z0-9]$'),
  CHECK (slug NOT IN ('admin', 'www', 'api', 'mail', 'static', 'support', 'status'))
);

CREATE TABLE stores (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  name VARCHAR(120) NOT NULL,
  city VARCHAR(100) NOT NULL,
  state CHAR(2) NOT NULL DEFAULT 'SP',
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  CHECK (state ~ '^[A-Z]{2}$')
);

CREATE TABLE users (
  id UUID PRIMARY KEY,
  email VARCHAR(254) NOT NULL UNIQUE,
  name VARCHAR(120) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  platform_admin BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (email = lower(email))
);

CREATE TABLE memberships (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  user_id UUID NOT NULL REFERENCES users(id),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE (tenant_id, user_id),
  UNIQUE (tenant_id, id)
);

CREATE TABLE permissions (
  code VARCHAR(64) PRIMARY KEY
);

INSERT INTO permissions (code) VALUES
  ('PRODUCT_READ'), ('PRODUCT_WRITE'), ('PRODUCT_IMPORT'), ('PRICE_CHANGE'),
  ('STOCK_READ'), ('STOCK_ADJUST'), ('SALE_CREATE'), ('SALE_CANCEL'), ('SALE_RETURN'),
  ('DISCOUNT_APPLY'), ('DISCOUNT_APPROVE'), ('NEGATIVE_STOCK_APPROVE'),
  ('CASH_OPEN'), ('CASH_WITHDRAW'), ('CASH_WITHDRAW_APPROVE'), ('CASH_REINFORCE'),
  ('CASH_CLOSE'), ('CASH_DIFFERENCE_APPROVE'), ('REFUND_EXECUTE'),
  ('FISCAL_CONFIGURE'), ('FISCAL_ISSUE'), ('REPORT_VIEW'), ('USER_MANAGE');

CREATE TABLE roles (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  name VARCHAR(60) NOT NULL,
  discount_limit NUMERIC(5,2) NOT NULL CHECK (discount_limit BETWEEN 0 AND 99.99),
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, name)
);

CREATE TABLE role_permissions (
  role_id UUID NOT NULL REFERENCES roles(id),
  permission_code VARCHAR(64) NOT NULL REFERENCES permissions(code),
  PRIMARY KEY (role_id, permission_code)
);

CREATE TABLE membership_store_roles (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  membership_id UUID NOT NULL,
  store_id UUID NOT NULL,
  role_id UUID NOT NULL,
  FOREIGN KEY (tenant_id, membership_id) REFERENCES memberships(tenant_id, id),
  FOREIGN KEY (tenant_id, store_id) REFERENCES stores(tenant_id, id),
  FOREIGN KEY (tenant_id, role_id) REFERENCES roles(tenant_id, id),
  UNIQUE (tenant_id, membership_id, store_id, role_id)
);

CREATE TABLE tenant_policies (
  tenant_id UUID PRIMARY KEY REFERENCES tenants(id),
  blind_cash_close BOOLEAN NOT NULL DEFAULT TRUE,
  negative_stock_requires_approval BOOLEAN NOT NULL DEFAULT TRUE,
  cash_withdraw_requires_approval BOOLEAN NOT NULL DEFAULT TRUE,
  maximum_discount NUMERIC(5,2) NOT NULL DEFAULT 20 CHECK (maximum_discount BETWEEN 0 AND 99.99)
);

CREATE TABLE audit_logs (
  id UUID PRIMARY KEY,
  tenant_id UUID REFERENCES tenants(id),
  store_id UUID,
  actor_id UUID REFERENCES users(id),
  action VARCHAR(80) NOT NULL,
  target_id UUID,
  correlation_id VARCHAR(36) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (tenant_id, store_id) REFERENCES stores(tenant_id, id)
);

CREATE INDEX ix_grants_context ON membership_store_roles (tenant_id, membership_id, store_id);
CREATE INDEX ix_stores_tenant ON stores (tenant_id, active);
CREATE INDEX ix_audit_context ON audit_logs (tenant_id, store_id, created_at DESC);

-- Eventos de auditoria são permanentes, inclusive para o usuário SQL da aplicação.
CREATE FUNCTION prevent_audit_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'Audit events are immutable';
END;
$$;
CREATE TRIGGER audit_logs_immutable BEFORE UPDATE OR DELETE ON audit_logs
FOR EACH ROW EXECUTE FUNCTION prevent_audit_mutation();
