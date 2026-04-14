-- V1__init.sql
-- Fiely baseline schema.
-- Feature tables (users, files, shares, ...) will be added in subsequent migrations.

CREATE TABLE IF NOT EXISTS fiely_schema_info (
    key         VARCHAR(64)  PRIMARY KEY,
    value       VARCHAR(255) NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

INSERT INTO fiely_schema_info (key, value) VALUES ('baseline', 'v1')
ON CONFLICT (key) DO NOTHING;
