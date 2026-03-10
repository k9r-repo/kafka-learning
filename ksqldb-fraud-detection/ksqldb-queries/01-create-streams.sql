-- ============================================================================
-- KSQLDB Stream and Table Creation
-- File: 01-create-streams.sql
-- Purpose: Define source streams and tables for fraud detection system
-- ============================================================================

-- Step 1: Create the source stream from Kafka topic
-- This stream reads raw transaction data from the 'transactions' topic
CREATE STREAM transactions_stream (
    transaction_id VARCHAR,
    card_number VARCHAR,
    cardholder_name VARCHAR,
    amount DOUBLE,
    merchant VARCHAR,
    merchant_category VARCHAR,
    country VARCHAR,
    city VARCHAR,
    timestamp BIGINT,
    currency VARCHAR,
    fraud_label BOOLEAN,
    fraud_type VARCHAR
) WITH (
    KAFKA_TOPIC='transactions',
    VALUE_FORMAT='JSON',
    TIMESTAMP='timestamp',
    PARTITIONS=3
);

-- Verify the stream is created
DESCRIBE transactions_stream;

-- Step 2: Create a table to track user transaction statistics
-- This table maintains running averages and statistics per card
CREATE TABLE user_transaction_stats AS
    SELECT 
        card_number,
        COUNT(*) as total_transactions,
        AVG(amount) as avg_amount,
        STDDEV_SAMP(amount) as std_dev_amount,
        MIN(amount) as min_amount,
        MAX(amount) as max_amount,
        LATEST_BY_OFFSET(country) as last_country,
        LATEST_BY_OFFSET(timestamp) as last_transaction_time
    FROM transactions_stream
    GROUP BY card_number
    EMIT CHANGES;

-- Step 3: Create a stream with enriched transaction data
-- Join transactions with user statistics for anomaly detection
CREATE STREAM enriched_transactions AS
    SELECT 
        t.transaction_id,
        t.card_number,
        t.cardholder_name,
        t.amount,
        t.merchant,
        t.merchant_category,
        t.country,
        t.city,
        t.timestamp,
        t.currency,
        t.fraud_label,
        t.fraud_type,
        -- Add user statistics for fraud scoring
        u.avg_amount as user_avg_amount,
        u.std_dev_amount as user_std_dev,
        u.total_transactions as user_total_txns,
        u.last_country as user_last_country,
        -- Calculate deviation from average
        (t.amount - u.avg_amount) / NULLIF(u.std_dev_amount, 0) as z_score
    FROM transactions_stream t
    LEFT JOIN user_transaction_stats u
    ON t.card_number = u.card_number
    EMIT CHANGES;

-- Verify enriched stream
DESCRIBE enriched_transactions;

-- Step 4: Test queries to verify data flow
-- Uncomment these to test (they will run continuously)

-- View all transactions in real-time
-- SELECT * FROM transactions_stream EMIT CHANGES LIMIT 10;

-- View enriched transactions with user stats
-- SELECT 
--     card_number,
--     amount,
--     user_avg_amount,
--     z_score,
--     merchant,
--     country
-- FROM enriched_transactions 
-- EMIT CHANGES 
-- LIMIT 10;

-- Check user statistics table
-- SELECT * FROM user_transaction_stats EMIT CHANGES LIMIT 10;
