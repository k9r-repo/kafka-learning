-- PostgreSQL Configuration for Debezium CDC
-- Enable Write-Ahead Logging (WAL) for Change Data Capture

-- These settings are typically in postgresql.conf, but we'll document them here
-- They should be set in the Docker container environment or config file

-- Required for Debezium:
-- wal_level = logical
-- max_wal_senders = 4
-- max_replication_slots = 4

-- Create a replication user (optional, but best practice for production)
-- For this learning project, we're using the default postgres user

-- Create a publication for all tables (required by Debezium)
CREATE PUBLICATION dbz_publication FOR ALL TABLES;

-- Verify the publication was created
SELECT * FROM pg_publication WHERE pubname = 'dbz_publication';

COMMENT ON PUBLICATION dbz_publication IS 'Publication for Debezium CDC connector to capture changes';
