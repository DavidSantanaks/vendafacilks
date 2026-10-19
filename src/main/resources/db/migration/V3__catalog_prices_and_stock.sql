CREATE TABLE categories (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  name VARCHAR(80) NOT NULL CHECK (length(trim(name)) > 0),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id)
);
CREATE UNIQUE INDEX ux_categories_name ON categories (tenant_id, lower(name));

CREATE TABLE products (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  sku VARCHAR(60) NOT NULL CHECK (sku = upper(sku) AND length(sku) > 0),
  name VARCHAR(160) NOT NULL CHECK (length(trim(name)) > 0),
  barcode VARCHAR(64),
  category_id UUID,
  unit VARCHAR(2) NOT NULL CHECK (unit IN ('UN', 'KG')),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  ncm VARCHAR(8) CHECK (ncm IS NULL OR ncm ~ '^[0-9]{8}$'),
  cest VARCHAR(7) CHECK (cest IS NULL OR cest ~ '^[0-9]{7}$'),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE (tenant_id, id),
  UNIQUE (tenant_id, sku),
  UNIQUE (tenant_id, barcode),
  FOREIGN KEY (tenant_id, category_id) REFERENCES categories(tenant_id, id)
);
CREATE INDEX ix_products_search ON products (tenant_id, active, name);

CREATE TABLE product_images (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  product_id UUID NOT NULL,
  content BYTEA NOT NULL CHECK (octet_length(content) BETWEEN 1 AND 3145728),
  width INTEGER NOT NULL CHECK (width BETWEEN 1 AND 1200),
  height INTEGER NOT NULL CHECK (height BETWEEN 1 AND 1200),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (tenant_id, product_id),
  FOREIGN KEY (tenant_id, product_id) REFERENCES products(tenant_id, id)
);

CREATE TABLE store_prices (
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  store_id UUID NOT NULL,
  product_id UUID NOT NULL,
  amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  version BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (tenant_id, store_id, product_id),
  FOREIGN KEY (tenant_id, store_id) REFERENCES stores(tenant_id, id),
  FOREIGN KEY (tenant_id, product_id) REFERENCES products(tenant_id, id)
);

CREATE TABLE store_price_history (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  store_id UUID NOT NULL,
  product_id UUID NOT NULL,
  previous_amount NUMERIC(19,2),
  amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
  active BOOLEAN NOT NULL,
  actor_id UUID NOT NULL REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (tenant_id, store_id) REFERENCES stores(tenant_id, id),
  FOREIGN KEY (tenant_id, product_id) REFERENCES products(tenant_id, id)
);
CREATE INDEX ix_price_history ON store_price_history (tenant_id, store_id, product_id, created_at DESC);
CREATE TRIGGER price_history_immutable BEFORE UPDATE OR DELETE ON store_price_history
FOR EACH ROW EXECUTE FUNCTION prevent_audit_mutation();

CREATE TABLE stock_balances (
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  store_id UUID NOT NULL,
  product_id UUID NOT NULL,
  quantity NUMERIC(19,3) NOT NULL DEFAULT 0,
  minimum_quantity NUMERIC(19,3) CHECK (minimum_quantity >= 0),
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (tenant_id, store_id, product_id),
  FOREIGN KEY (tenant_id, store_id) REFERENCES stores(tenant_id, id),
  FOREIGN KEY (tenant_id, product_id) REFERENCES products(tenant_id, id)
);

CREATE TABLE stock_movements (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  store_id UUID NOT NULL,
  product_id UUID NOT NULL,
  operation_id UUID NOT NULL,
  request_hash VARCHAR(64) NOT NULL,
  type VARCHAR(16) NOT NULL CHECK (type IN ('ENTRY', 'ADJUSTMENT')),
  quantity NUMERIC(19,3) NOT NULL CHECK (quantity <> 0),
  balance_after NUMERIC(19,3) NOT NULL,
  reason VARCHAR(240) NOT NULL CHECK (length(trim(reason)) >= 5),
  actor_id UUID NOT NULL REFERENCES users(id),
  approver_id UUID REFERENCES users(id),
  approval_reason VARCHAR(240),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK (type <> 'ENTRY' OR quantity > 0),
  CHECK (balance_after >= 0 OR (approver_id IS NOT NULL AND length(trim(approval_reason)) >= 5)),
  CHECK (approver_id IS NULL OR approver_id <> actor_id),
  UNIQUE (tenant_id, store_id, operation_id),
  FOREIGN KEY (tenant_id, store_id, product_id) REFERENCES stock_balances(tenant_id, store_id, product_id)
);
CREATE INDEX ix_stock_movements ON stock_movements (tenant_id, store_id, product_id, created_at DESC);
CREATE TRIGGER stock_movements_immutable BEFORE UPDATE OR DELETE ON stock_movements
FOR EACH ROW EXECUTE FUNCTION prevent_audit_mutation();

CREATE TABLE import_jobs (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  store_id UUID NOT NULL,
  actor_id UUID NOT NULL REFERENCES users(id),
  filename VARCHAR(120) NOT NULL,
  file_hash VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL CHECK (status IN ('PREVIEW', 'REJECTED', 'COMPLETED')),
  row_count INTEGER NOT NULL CHECK (row_count BETWEEN 1 AND 1000),
  error_count INTEGER NOT NULL DEFAULT 0 CHECK (error_count >= 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMPTZ NOT NULL,
  completed_at TIMESTAMPTZ,
  UNIQUE (tenant_id, store_id, id),
  FOREIGN KEY (tenant_id, store_id) REFERENCES stores(tenant_id, id)
);
CREATE INDEX ix_import_jobs_actor ON import_jobs (tenant_id, store_id, actor_id, created_at DESC);

CREATE TABLE import_rows (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  store_id UUID NOT NULL,
  job_id UUID NOT NULL,
  row_number INTEGER NOT NULL CHECK (row_number BETWEEN 2 AND 1001),
  template_version VARCHAR(256) NOT NULL,
  sku VARCHAR(256) NOT NULL,
  name VARCHAR(256) NOT NULL,
  barcode VARCHAR(256) NOT NULL,
  category VARCHAR(256) NOT NULL,
  unit VARCHAR(256) NOT NULL,
  price VARCHAR(256) NOT NULL,
  initial_quantity VARCHAR(256) NOT NULL,
  minimum_quantity VARCHAR(256) NOT NULL,
  ncm VARCHAR(256) NOT NULL,
  cest VARCHAR(256) NOT NULL,
  product_id UUID,
  UNIQUE (tenant_id, store_id, job_id, row_number),
  FOREIGN KEY (tenant_id, store_id, job_id) REFERENCES import_jobs(tenant_id, store_id, id),
  FOREIGN KEY (tenant_id, product_id) REFERENCES products(tenant_id, id)
);

CREATE TABLE import_row_errors (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  store_id UUID NOT NULL,
  job_id UUID NOT NULL,
  row_number INTEGER NOT NULL,
  message VARCHAR(240) NOT NULL,
  FOREIGN KEY (tenant_id, store_id, job_id, row_number)
    REFERENCES import_rows(tenant_id, store_id, job_id, row_number)
);
