-- =============================================================
--  init-databases.sql
--  Run this once against a single PostgreSQL instance to create
--  all four service databases (alternative to separate containers).
-- =============================================================

CREATE DATABASE user_db;
CREATE DATABASE order_db;
CREATE DATABASE payment_db;
CREATE DATABASE notification_db;

-- Grant all privileges to the default user (adjust as needed)
GRANT ALL PRIVILEGES ON DATABASE user_db         TO postgres;
GRANT ALL PRIVILEGES ON DATABASE order_db        TO postgres;
GRANT ALL PRIVILEGES ON DATABASE payment_db      TO postgres;
GRANT ALL PRIVILEGES ON DATABASE notification_db TO postgres;
