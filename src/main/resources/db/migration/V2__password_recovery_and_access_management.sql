ALTER TABLE users ADD COLUMN credential_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE membership_store_roles ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE password_reset_tokens (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id),
  tenant_id UUID REFERENCES tenants(id),
  platform BOOLEAN NOT NULL,
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  credential_version BIGINT NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  used_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK ((platform AND tenant_id IS NULL) OR (NOT platform AND tenant_id IS NOT NULL))
);

CREATE INDEX ix_password_reset_user ON password_reset_tokens (user_id, expires_at);
