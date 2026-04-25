-- V3__tenants.sql
-- Introduce tenants. Every auth_user belongs to exactly one tenant; per-tenant
-- settings (e.g. max_upload_bytes) live on this row. A single default tenant is
-- seeded so existing deployments keep working without manual backfill.
--
-- The plugin-side UserRepository in fiely-auth-jwt inserts rows without setting
-- tenant_id; the column therefore carries a DB default pointing at the seeded
-- default tenant so plugin code keeps compiling. When explicit tenancy UX is
-- added, inserts will start supplying tenant_id and the default can be dropped.

CREATE TABLE IF NOT EXISTS tenants (
    id               UUID         PRIMARY KEY,
    slug             VARCHAR(64)  NOT NULL UNIQUE,
    name             VARCHAR(255) NOT NULL,
    max_upload_bytes BIGINT       NOT NULL DEFAULT 104857600, -- 100 MiB
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Seed a stable default tenant. The UUID is fixed so references from test
-- fixtures and migrations are stable across environments.
INSERT INTO tenants (id, slug, name)
VALUES ('00000000-0000-0000-0000-000000000001', 'default', 'Default')
ON CONFLICT (id) DO NOTHING;

ALTER TABLE auth_users
    ADD COLUMN IF NOT EXISTS tenant_id UUID
        NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001'
        REFERENCES tenants(id) ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS auth_users_tenant_idx ON auth_users (tenant_id);
