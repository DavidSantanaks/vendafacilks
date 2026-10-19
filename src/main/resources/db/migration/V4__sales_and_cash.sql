ALTER TABLE tenant_policies ADD COLUMN cash_difference_limit NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (cash_difference_limit >= 0);
ALTER TABLE tenant_policies ADD COLUMN cash_withdraw_limit NUMERIC(19,2) NOT NULL DEFAULT 1000 CHECK (cash_withdraw_limit > 0);

CREATE TABLE cash_registers (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  store_id UUID NOT NULL,
  name VARCHAR(80) NOT NULL CHECK (length(trim(name)) > 0),
  UNIQUE (tenant_id, store_id, id),
  UNIQUE (tenant_id, store_id, name),
  FOREIGN KEY (tenant_id, store_id) REFERENCES stores(tenant_id, id)
);
CREATE TABLE cash_sessions (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  store_id UUID NOT NULL,
  register_id UUID NOT NULL,
  operator_id UUID NOT NULL REFERENCES users(id),
  operation_id UUID NOT NULL,
  initial_amount NUMERIC(19,2) NOT NULL CHECK (initial_amount >= 0),
  status VARCHAR(12) NOT NULL CHECK (status IN ('OPEN','COUNTED','CLOSED')),
  opened_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  closed_at TIMESTAMPTZ,
  UNIQUE (tenant_id, store_id, id),
  UNIQUE (tenant_id, store_id, operation_id),
  FOREIGN KEY (tenant_id, store_id, register_id) REFERENCES cash_registers(tenant_id, store_id, id)
);
CREATE UNIQUE INDEX ux_cash_register_open ON cash_sessions(tenant_id,store_id,register_id) WHERE status <> 'CLOSED';
CREATE UNIQUE INDEX ux_cash_operator_open ON cash_sessions(tenant_id,store_id,operator_id) WHERE status <> 'CLOSED';
CREATE TABLE cash_counts (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  store_id UUID NOT NULL,
  session_id UUID NOT NULL,
  counted NUMERIC(19,2) NOT NULL CHECK (counted >= 0),
  expected NUMERIC(19,2) NOT NULL,
  actor_id UUID NOT NULL REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  UNIQUE (tenant_id, store_id, session_id),
  FOREIGN KEY (tenant_id,store_id,session_id) REFERENCES cash_sessions(tenant_id,store_id,id)
);
CREATE TABLE cash_events (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  store_id UUID NOT NULL,
  session_id UUID NOT NULL,
  operation_id UUID NOT NULL,
  request_hash VARCHAR(64) NOT NULL,
  type VARCHAR(12) NOT NULL CHECK (type IN ('OPEN','SUPPLY','WITHDRAW','SALE','CLOSE')),
  amount NUMERIC(19,2) NOT NULL,
  reason VARCHAR(240) NOT NULL,
  actor_id UUID NOT NULL REFERENCES users(id),
  approver_id UUID REFERENCES users(id),
  approval_reason VARCHAR(240),
  created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  UNIQUE (tenant_id,store_id,operation_id),
  FOREIGN KEY (tenant_id,store_id,session_id) REFERENCES cash_sessions(tenant_id,store_id,id),
  CHECK (approver_id IS NULL OR (approver_id <> actor_id AND length(trim(approval_reason)) >= 5)),
  CHECK ((type='WITHDRAW' AND amount<0) OR (type IN ('SUPPLY','SALE') AND amount>0) OR (type='OPEN' AND amount>=0) OR (type='CLOSE' AND amount=0))
);
CREATE INDEX ix_cash_events_session ON cash_events(tenant_id,store_id,session_id,created_at);
CREATE TABLE sales (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  store_id UUID NOT NULL,
  session_id UUID NOT NULL,
  actor_id UUID NOT NULL REFERENCES users(id),
  operation_id UUID NOT NULL,
  request_hash VARCHAR(64) NOT NULL,
  customer VARCHAR(120) NOT NULL DEFAULT '',
  subtotal NUMERIC(19,2) NOT NULL CHECK (subtotal>0),
  discount NUMERIC(19,2) NOT NULL CHECK (discount>=0),
  total NUMERIC(19,2) NOT NULL CHECK (total>0 AND total=subtotal-discount),
  change NUMERIC(19,2) NOT NULL CHECK (change>=0),
  commercial_status VARCHAR(16) NOT NULL DEFAULT 'COMPLETED' CHECK (commercial_status='COMPLETED'),
  fiscal_status VARCHAR(16) NOT NULL DEFAULT 'NOT_ISSUED' CHECK (fiscal_status='NOT_ISSUED'),
  mode VARCHAR(8) NOT NULL DEFAULT 'DEMO' CHECK (mode='DEMO'),
  created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  UNIQUE (tenant_id,store_id,id),
  UNIQUE (tenant_id,store_id,operation_id),
  FOREIGN KEY (tenant_id,store_id,session_id) REFERENCES cash_sessions(tenant_id,store_id,id)
);
CREATE INDEX ix_sales_period ON sales(tenant_id,store_id,created_at);
CREATE TABLE sale_items (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  store_id UUID NOT NULL,
  sale_id UUID NOT NULL,
  product_id UUID NOT NULL,
  sku VARCHAR(60) NOT NULL,
  name VARCHAR(160) NOT NULL,
  unit VARCHAR(2) NOT NULL CHECK (unit IN ('UN','KG')),
  quantity NUMERIC(19,3) NOT NULL CHECK (quantity>0),
  unit_price NUMERIC(19,2) NOT NULL CHECK (unit_price>0),
  subtotal NUMERIC(19,2) NOT NULL CHECK (subtotal>0),
  discount NUMERIC(19,2) NOT NULL CHECK (discount>=0 AND discount<subtotal),
  total NUMERIC(19,2) NOT NULL CHECK (total=subtotal-discount),
  ncm VARCHAR(8),
  cest VARCHAR(7),
  UNIQUE (tenant_id,store_id,sale_id,product_id),
  FOREIGN KEY (tenant_id,store_id,sale_id) REFERENCES sales(tenant_id,store_id,id),
  FOREIGN KEY (tenant_id,product_id) REFERENCES products(tenant_id,id)
);
CREATE TABLE sale_payments (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  store_id UUID NOT NULL,
  sale_id UUID NOT NULL,
  method VARCHAR(8) NOT NULL CHECK (method IN ('CASH','PIX','DEBIT','CREDIT')),
  amount NUMERIC(19,2) NOT NULL CHECK (amount>0),
  UNIQUE (tenant_id,store_id,sale_id,method),
  FOREIGN KEY (tenant_id,store_id,sale_id) REFERENCES sales(tenant_id,store_id,id)
);
CREATE TABLE sale_approvals (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  store_id UUID NOT NULL,
  sale_id UUID NOT NULL,
  type VARCHAR(16) NOT NULL CHECK (type IN ('DISCOUNT','NEGATIVE_STOCK')),
  actor_id UUID NOT NULL REFERENCES users(id),
  approver_id UUID NOT NULL REFERENCES users(id),
  reason VARCHAR(240) NOT NULL CHECK (length(trim(reason)) >= 5),
  created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  CHECK (actor_id<>approver_id),
  UNIQUE (tenant_id,store_id,sale_id,type),
  FOREIGN KEY (tenant_id,store_id,sale_id) REFERENCES sales(tenant_id,store_id,id)
);
ALTER TABLE stock_movements DROP CONSTRAINT stock_movements_type_check;
ALTER TABLE stock_movements ADD CONSTRAINT stock_movements_type_check CHECK (type IN ('ENTRY','ADJUSTMENT','SALE'));
ALTER TABLE stock_movements ADD COLUMN sale_id UUID;
ALTER TABLE stock_movements ADD CONSTRAINT fk_stock_sale FOREIGN KEY (tenant_id,store_id,sale_id) REFERENCES sales(tenant_id,store_id,id);
ALTER TABLE stock_movements ADD CONSTRAINT ck_stock_sale CHECK ((type='SALE' AND sale_id IS NOT NULL AND quantity<0) OR (type<>'SALE' AND sale_id IS NULL));

CREATE TRIGGER cash_counts_immutable BEFORE UPDATE OR DELETE ON cash_counts FOR EACH ROW EXECUTE FUNCTION prevent_audit_mutation();
CREATE TRIGGER cash_events_immutable BEFORE UPDATE OR DELETE ON cash_events FOR EACH ROW EXECUTE FUNCTION prevent_audit_mutation();
CREATE TRIGGER sales_immutable BEFORE UPDATE OR DELETE ON sales FOR EACH ROW EXECUTE FUNCTION prevent_audit_mutation();
CREATE TRIGGER sale_items_immutable BEFORE UPDATE OR DELETE ON sale_items FOR EACH ROW EXECUTE FUNCTION prevent_audit_mutation();
CREATE TRIGGER sale_payments_immutable BEFORE UPDATE OR DELETE ON sale_payments FOR EACH ROW EXECUTE FUNCTION prevent_audit_mutation();
CREATE TRIGGER sale_approvals_immutable BEFORE UPDATE OR DELETE ON sale_approvals FOR EACH ROW EXECUTE FUNCTION prevent_audit_mutation();
