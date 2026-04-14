-- V2__auth_users.sql
-- Core user store, owned by the core schema and shared by any AuthProvider
-- plugin that authenticates against a local identity (e.g. fiely-auth-jwt).
--
-- Auth plugins query and write to this table via the shared DataSource;
-- they do NOT ship their own copy of it. Plugins that need *additional*,
-- private state (sessions, refresh-token revocation lists, OIDC nonces, …)
-- can still ship their own migrations via the PluginMigrations extension
-- point — see docs/plugin-architecture.md.

CREATE TABLE IF NOT EXISTS auth_users (
    id             UUID         PRIMARY KEY,
    username       VARCHAR(64)  NOT NULL UNIQUE,
    email          VARCHAR(255),
    display_name   VARCHAR(255),
    password_hash  VARCHAR(255) NOT NULL,
    roles          TEXT[]       NOT NULL DEFAULT '{}',
    is_enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS auth_users_email_idx ON auth_users (LOWER(email));
