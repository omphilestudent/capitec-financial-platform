-- Creates every service database that does not exist yet. Idempotent: safe on a fresh
-- volume, on an existing volume, and on every `docker compose up` (the db-init service
-- runs this file each time). Existing databases and their data are never touched.
--
-- Run by: docker compose service "db-init" (psql -f).

SELECT 'CREATE DATABASE customer_service_db' WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'customer_service_db') \gexec
SELECT 'CREATE DATABASE account_service_db' WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'account_service_db') \gexec
SELECT 'CREATE DATABASE transaction_service_db' WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'transaction_service_db') \gexec
SELECT 'CREATE DATABASE case_service_db' WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'case_service_db') \gexec
SELECT 'CREATE DATABASE fraud_detection_db' WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'fraud_detection_db') \gexec
SELECT 'CREATE DATABASE notification_db' WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'notification_db') \gexec
SELECT 'CREATE DATABASE statement_service_db' WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'statement_service_db') \gexec
SELECT 'CREATE DATABASE transaction_aggregation_db' WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'transaction_aggregation_db') \gexec
