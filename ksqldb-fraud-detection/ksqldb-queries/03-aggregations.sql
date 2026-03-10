-- ============================================================================
-- Windowed Aggregations for Fraud Detection
-- File: 03-aggregations.sql
-- Purpose: Time-based aggregations to detect velocity and pattern-based fraud
-- ============================================================================

-- ============================================================================
-- VELOCITY CHECK: Tumbling Window (5 minutes)
-- Detects high transaction frequency (velocity fraud)
-- Flags cards with more than 3 transactions in 5 minutes
-- ============================================================================

CREATE TABLE velocity_fraud_5min AS
    SELECT 
        card_number,
        COUNT(*) as txn_count,
        SUM(amount) as total_amount,
        AVG(amount) as avg_amount,
        COLLECT_LIST(merchant) as merchants,
        COLLECT_LIST(country) as countries,
        WINDOWSTART as window_start,
        WINDOWEND as window_end,
        'VELOCITY_5MIN' as fraud_type
    FROM transactions_stream
    WINDOW TUMBLING (SIZE 5 MINUTES)
    GROUP BY card_number
    HAVING COUNT(*) > 3
    EMIT CHANGES;

-- ============================================================================
-- VELOCITY CHECK: Tumbling Window (1 minute - More Aggressive)
-- Detects extremely rapid transactions
-- Flags cards with more than 2 transactions in 1 minute
-- ============================================================================

CREATE TABLE velocity_fraud_1min AS
    SELECT 
        card_number,
        COUNT(*) as txn_count,
        SUM(amount) as total_amount,
        COLLECT_LIST(transaction_id) as transaction_ids,
        WINDOWSTART as window_start,
        WINDOWEND as window_end,
        'VELOCITY_1MIN' as fraud_type
    FROM transactions_stream
    WINDOW TUMBLING (SIZE 1 MINUTE)
    GROUP BY card_number
    HAVING COUNT(*) > 2
    EMIT CHANGES;

-- ============================================================================
-- GEOGRAPHIC ANOMALY: Hopping Window (1 hour, advance 10 minutes)
-- Detects transactions from multiple countries in overlapping time windows
-- Uses hopping window to catch patterns across window boundaries
-- ============================================================================

CREATE TABLE geo_fraud_hopping AS
    SELECT 
        card_number,
        COUNT_DISTINCT(country) as unique_countries,
        COLLECT_LIST(country) as countries_list,
        COUNT(*) as txn_count,
        SUM(amount) as total_amount,
        WINDOWSTART as window_start,
        WINDOWEND as window_end,
        'GEO_FRAUD_HOPPING' as fraud_type
    FROM transactions_stream
    WINDOW HOPPING (SIZE 1 HOUR, ADVANCE BY 10 MINUTES)
    GROUP BY card_number
    HAVING COUNT_DISTINCT(country) > 1
    EMIT CHANGES;

-- ============================================================================
-- SPENDING PATTERN ANOMALY: Session Window
-- Detects burst spending patterns with session-based windows
-- Session ends after 10 minutes of inactivity
-- ============================================================================

CREATE TABLE spending_burst_session AS
    SELECT 
        card_number,
        COUNT(*) as txn_count,
        SUM(amount) as session_total,
        AVG(amount) as session_avg,
        MAX(amount) as session_max,
        COLLECT_LIST(merchant_category) as categories,
        WINDOWSTART as session_start,
        WINDOWEND as session_end,
        'SPENDING_BURST' as fraud_type
    FROM transactions_stream
    WINDOW SESSION (10 MINUTES)
    GROUP BY card_number
    HAVING 
        -- High transaction count in session
        COUNT(*) > 5
        OR
        -- High total spending in session
        SUM(amount) > 3000
    EMIT CHANGES;

-- ============================================================================
-- MERCHANT CATEGORY FREQUENCY: Tumbling Window (30 minutes)
-- Detects unusual merchant category patterns
-- Flags if high-risk categories are accessed frequently
-- ============================================================================

CREATE TABLE merchant_category_frequency AS
    SELECT 
        card_number,
        merchant_category,
        COUNT(*) as category_count,
        SUM(amount) as category_total,
        AVG(amount) as category_avg,
        WINDOWSTART as window_start,
        WINDOWEND as window_end
    FROM transactions_stream
    WINDOW TUMBLING (SIZE 30 MINUTES)
    GROUP BY card_number, merchant_category
    HAVING 
        merchant_category IN ('crypto_exchange', 'gambling', 'money_transfer')
        AND COUNT(*) >= 2
    EMIT CHANGES;

-- ============================================================================
-- HOURLY FRAUD SUMMARY: Tumbling Window (1 hour)
-- Aggregates fraud statistics per hour for monitoring dashboard
-- ============================================================================

CREATE TABLE hourly_fraud_summary AS
    SELECT 
        WINDOWSTART as hour_start,
        WINDOWEND as hour_end,
        COUNT(*) as total_transactions,
        SUM(CASE WHEN fraud_label = true THEN 1 ELSE 0 END) as fraud_count,
        SUM(CASE WHEN fraud_label = false THEN 1 ELSE 0 END) as normal_count,
        AVG(amount) as avg_transaction_amount,
        SUM(amount) as total_transaction_amount,
        COUNT_DISTINCT(card_number) as unique_cards,
        COUNT_DISTINCT(country) as unique_countries,
        -- Calculate fraud rate percentage
        (SUM(CASE WHEN fraud_label = true THEN 1 ELSE 0 END) * 100.0) / COUNT(*) as fraud_rate_percent
    FROM transactions_stream
    WINDOW TUMBLING (SIZE 1 HOUR)
    GROUP BY 1
    EMIT CHANGES;

-- ============================================================================
-- CARD-LEVEL STATISTICS: Tumbling Window (15 minutes)
-- Rolling statistics per card for real-time monitoring
-- ============================================================================

CREATE TABLE card_stats_15min AS
    SELECT 
        card_number,
        COUNT(*) as txn_count,
        SUM(amount) as total_spent,
        AVG(amount) as avg_amount,
        MIN(amount) as min_amount,
        MAX(amount) as max_amount,
        STDDEV_SAMP(amount) as std_dev,
        COUNT_DISTINCT(merchant_category) as unique_categories,
        COUNT_DISTINCT(country) as unique_countries,
        LATEST_BY_OFFSET(country) as last_country,
        WINDOWSTART as window_start,
        WINDOWEND as window_end
    FROM transactions_stream
    WINDOW TUMBLING (SIZE 15 MINUTES)
    GROUP BY card_number
    EMIT CHANGES;

-- ============================================================================
-- VELOCITY FRAUD ALERTS STREAM
-- Convert velocity tables to alert streams for unified processing
-- ============================================================================

CREATE STREAM velocity_fraud_alerts AS
    SELECT 
        card_number,
        txn_count,
        total_amount,
        avg_amount,
        fraud_type,
        -- Calculate fraud score based on velocity
        CASE 
            WHEN txn_count >= 6 THEN 95
            WHEN txn_count >= 5 THEN 85
            WHEN txn_count >= 4 THEN 75
            ELSE 65
        END as fraud_score,
        window_start,
        window_end,
        'Rapid transactions: ' + CAST(txn_count AS VARCHAR) + ' in 5 minutes' as alert_message
    FROM velocity_fraud_5min
    EMIT CHANGES;

-- ============================================================================
-- Test Queries (Uncomment to view results)
-- ============================================================================

-- View velocity fraud in real-time
-- SELECT * FROM velocity_fraud_5min EMIT CHANGES;

-- View geographic anomalies
-- SELECT 
--     card_number,
--     unique_countries,
--     countries_list,
--     txn_count
-- FROM geo_fraud_hopping 
-- EMIT CHANGES;

-- View hourly summary
-- SELECT * FROM hourly_fraud_summary EMIT CHANGES;

-- Pull query: Get current statistics for a specific card (change card number)
-- SELECT * FROM card_stats_15min WHERE card_number = '4532-1111-2222-3333';

-- View spending burst sessions
-- SELECT 
--     card_number,
--     txn_count,
--     session_total,
--     session_avg,
--     categories
-- FROM spending_burst_session 
-- EMIT CHANGES;

-- View velocity alerts
-- SELECT * FROM velocity_fraud_alerts EMIT CHANGES;
