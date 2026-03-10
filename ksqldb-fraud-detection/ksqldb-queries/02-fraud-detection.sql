-- ============================================================================
-- Fraud Detection Queries
-- File: 02-fraud-detection.sql
-- Purpose: Implement fraud detection patterns and alert generation
-- ============================================================================

-- ============================================================================
-- PATTERN 1: Amount Anomaly Detection
-- Detects transactions significantly higher than user's average
-- Uses z-score threshold of 3 (3 standard deviations)
-- ============================================================================

CREATE STREAM amount_anomaly_fraud AS
    SELECT 
        transaction_id,
        card_number,
        amount,
        user_avg_amount,
        user_std_dev,
        z_score,
        merchant,
        merchant_category,
        country,
        timestamp,
        'AMOUNT_ANOMALY' as fraud_type,
        -- Calculate fraud score (0-100 scale)
        CASE 
            WHEN ABS(z_score) >= 5 THEN 100
            WHEN ABS(z_score) >= 4 THEN 90
            WHEN ABS(z_score) >= 3 THEN 75
            ELSE 50
        END as fraud_score
    FROM enriched_transactions
    WHERE ABS(z_score) > 3.0
    EMIT CHANGES;

-- ============================================================================
-- PATTERN 2: High-Risk Merchant Category
-- Flags transactions from risky merchant categories
-- ============================================================================

CREATE STREAM high_risk_merchant_fraud AS
    SELECT 
        transaction_id,
        card_number,
        amount,
        merchant,
        merchant_category,
        country,
        timestamp,
        'HIGH_RISK_MERCHANT' as fraud_type,
        -- Risk scoring based on category and amount
        CASE 
            WHEN merchant_category = 'crypto_exchange' AND amount > 1000 THEN 95
            WHEN merchant_category = 'gambling' AND amount > 500 THEN 85
            WHEN merchant_category = 'money_transfer' AND amount > 2000 THEN 90
            WHEN merchant_category IN ('crypto_exchange', 'gambling', 'money_transfer') THEN 70
            ELSE 60
        END as fraud_score
    FROM enriched_transactions
    WHERE merchant_category IN ('crypto_exchange', 'gambling', 'money_transfer')
    EMIT CHANGES;

-- ============================================================================
-- PATTERN 3: Geographic Anomaly Detection
-- Detects transactions from different country than user's last location
-- ============================================================================

CREATE STREAM geo_anomaly_potential AS
    SELECT 
        transaction_id,
        card_number,
        country,
        user_last_country,
        amount,
        merchant,
        timestamp,
        'GEO_ANOMALY' as fraud_type,
        -- Higher score if amount is also high
        CASE 
            WHEN amount > 1000 THEN 85
            WHEN amount > 500 THEN 70
            ELSE 60
        END as fraud_score
    FROM enriched_transactions
    WHERE country != user_last_country
        AND user_last_country IS NOT NULL
    EMIT CHANGES;

-- ============================================================================
-- COMBINED FRAUD ALERTS STREAM
-- Merges all fraud patterns into a single alert stream
-- ============================================================================

-- First, standardize the schema for amount anomaly fraud
CREATE STREAM amount_anomaly_alerts AS
    SELECT 
        transaction_id,
        card_number,
        amount,
        merchant,
        merchant_category,
        country,
        timestamp,
        fraud_type,
        fraud_score,
        CAST(z_score AS VARCHAR) as additional_info
    FROM amount_anomaly_fraud
    EMIT CHANGES;

-- Standardize high-risk merchant fraud
CREATE STREAM high_risk_alerts AS
    SELECT 
        transaction_id,
        card_number,
        amount,
        merchant,
        merchant_category,
        country,
        timestamp,
        fraud_type,
        fraud_score,
        'High-risk category: ' + merchant_category as additional_info
    FROM high_risk_merchant_fraud
    EMIT CHANGES;

-- Standardize geo anomaly fraud
CREATE STREAM geo_anomaly_alerts AS
    SELECT 
        transaction_id,
        card_number,
        amount,
        merchant,
        merchant_category,
        country,
        timestamp,
        fraud_type,
        fraud_score,
        'Previous country: ' + user_last_country as additional_info
    FROM geo_anomaly_potential
    EMIT CHANGES;

-- Unified fraud alerts stream
CREATE STREAM fraud_alerts AS
    SELECT * FROM amount_anomaly_alerts
    EMIT CHANGES;

-- Insert from other alert streams into unified stream
INSERT INTO fraud_alerts SELECT * FROM high_risk_alerts;
INSERT INTO fraud_alerts SELECT * FROM geo_anomaly_alerts;

-- ============================================================================
-- HIGH-PRIORITY FRAUD ALERTS
-- Filter only high-score fraud alerts for immediate action
-- ============================================================================

CREATE STREAM critical_fraud_alerts AS
    SELECT 
        transaction_id,
        card_number,
        amount,
        merchant,
        fraud_type,
        fraud_score,
        timestamp,
        'CRITICAL' as priority,
        additional_info
    FROM fraud_alerts
    WHERE fraud_score >= 80
    EMIT CHANGES;

-- ============================================================================
-- VALID TRANSACTIONS STREAM
-- Stream of transactions that passed all fraud checks
-- This can be used for downstream processing
-- ============================================================================

CREATE STREAM valid_transactions AS
    SELECT 
        t.transaction_id,
        t.card_number,
        t.amount,
        t.merchant,
        t.merchant_category,
        t.country,
        t.timestamp,
        'APPROVED' as status
    FROM enriched_transactions t
    WHERE 
        -- Not an amount anomaly
        ABS(t.z_score) <= 3.0
        -- Not a high-risk merchant
        AND t.merchant_category NOT IN ('crypto_exchange', 'gambling', 'money_transfer')
        -- Same country as last transaction
        AND (t.country = t.user_last_country OR t.user_last_country IS NULL)
    EMIT CHANGES;

-- ============================================================================
-- Test Queries (Uncomment to view real-time results)
-- ============================================================================

-- View all fraud alerts in real-time
-- SELECT * FROM fraud_alerts EMIT CHANGES;

-- View only critical alerts
-- SELECT 
--     card_number,
--     amount,
--     fraud_type,
--     fraud_score,
--     merchant
-- FROM critical_fraud_alerts 
-- EMIT CHANGES;

-- Count fraud alerts by type (use in separate terminal)
-- SELECT 
--     fraud_type,
--     COUNT(*) as alert_count,
--     AVG(fraud_score) as avg_score
-- FROM fraud_alerts
-- WINDOW TUMBLING (SIZE 1 MINUTE)
-- GROUP BY fraud_type
-- EMIT CHANGES;

-- View valid transactions
-- SELECT * FROM valid_transactions EMIT CHANGES LIMIT 20;
