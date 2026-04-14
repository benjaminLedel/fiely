-- V1__auth_users.sql
-- Schema owned by the fiely-auth-jwt plugin.
--
-- Tracked in its own history table (`flyway_plugin_fiely_auth_jwt_history`)
-- by the core's PluginMigrationRunner, so it is independent of the core
-- schema and of other plugins.

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
