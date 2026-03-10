# KSQLDB Queries Guide

This directory contains SQL scripts for the real-time fraud detection system built with KSQLDB.

---

## 📁 Files Overview

### **01-create-streams.sql**
Creates the foundational streams and tables:
- `transactions_stream` - Source stream from Kafka topic
- `user_transaction_stats` - Table tracking per-card statistics
- `enriched_transactions` - Stream with joined user statistics

### **02-fraud-detection.sql**
Implements fraud detection patterns:
- **Amount Anomaly** - Detects transactions > 3 standard deviations from user average
- **High-Risk Merchant** - Flags crypto, gambling, money transfer categories
- **Geographic Anomaly** - Detects country changes
- **Unified Alerts** - Combines all fraud patterns into `fraud_alerts` stream

### **03-aggregations.sql**
Time-based windowed aggregations:
- **Velocity Checks** - Tumbling windows (1 min, 5 min) for rapid transactions
- **Geographic Anomaly** - Hopping windows (1 hour) for impossible travel
- **Spending Burst** - Session windows for burst patterns
- **Hourly Summary** - Dashboard statistics

---

## 🚀 How to Use

### Step 1: Start KSQLDB CLI
```bash
docker exec -it ksqldb-cli ksql http://ksqldb-server:8088
```

### Step 2: Run Scripts in Order

**Execute each script by copying and pasting the SQL statements:**

1. First, create streams:
```sql
-- Copy and paste contents of 01-create-streams.sql
```

2. Then, create fraud detection logic:
```sql
-- Copy and paste contents of 02-fraud-detection.sql
```

3. Finally, create windowed aggregations:
```sql
-- Copy and paste contents of 03-aggregations.sql
```

### Step 3: Verify Creation

```sql
-- List all streams
SHOW STREAMS;

-- List all tables
SHOW TABLES;

-- Describe a specific stream
DESCRIBE fraud_alerts;
```

---

## 🔍 Useful Queries

### Real-Time Monitoring

**View all fraud alerts as they happen:**
```sql
SELECT * FROM fraud_alerts EMIT CHANGES;
```

**View only critical fraud (score >= 80):**
```sql
SELECT * FROM critical_fraud_alerts EMIT CHANGES;
```

**Monitor velocity fraud:**
```sql
SELECT * FROM velocity_fraud_5min EMIT CHANGES;
```

**Geographic anomalies:**
```sql
SELECT * FROM geo_fraud_hopping EMIT CHANGES;
```

### Dashboard Queries

**Fraud count by type (last minute):**
```sql
SELECT 
    fraud_type,
    COUNT(*) as alert_count,
    AVG(fraud_score) as avg_score
FROM fraud_alerts
WINDOW TUMBLING (SIZE 1 MINUTE)
GROUP BY fraud_type
EMIT CHANGES;
```

**Hourly statistics:**
```sql
SELECT * FROM hourly_fraud_summary EMIT CHANGES;
```

### Pull Queries (Point-in-Time)

**Get current stats for a specific card:**
```sql
SELECT * FROM user_transaction_stats 
WHERE card_number = '4532-1111-2222-3333';
```

**Get velocity fraud in last window:**
```sql
SELECT * FROM velocity_fraud_5min 
WHERE card_number = '4532-1111-2222-3333';
```

---

## 🎓 Key Concepts Demonstrated

### 1. **Streams vs Tables**
- **Streams:** Unbounded, append-only (transactions_stream, fraud_alerts)
- **Tables:** Stateful, updated (user_transaction_stats, velocity_fraud_5min)

### 2. **Window Types**

**Tumbling Windows:**
```sql
WINDOW TUMBLING (SIZE 5 MINUTES)
```
- Non-overlapping, discrete time buckets
- Used for: Velocity checks, hourly summaries

**Hopping Windows:**
```sql
WINDOW HOPPING (SIZE 1 HOUR, ADVANCE BY 10 MINUTES)
```
- Overlapping windows
- Used for: Geographic anomalies to catch patterns across boundaries

**Session Windows:**
```sql
WINDOW SESSION (10 MINUTES)
```
- Variable-size windows based on inactivity gaps
- Used for: Spending burst detection

### 3. **Stream Processing Patterns**

**Filtering:**
```sql
WHERE ABS(z_score) > 3.0
```

**Aggregation:**
```sql
COUNT(*), SUM(amount), AVG(amount), STDDEV_SAMP(amount)
```

**Joins:**
```sql
FROM transactions_stream t
LEFT JOIN user_transaction_stats u
ON t.card_number = u.card_number
```

**Collection Functions:**
```sql
COLLECT_LIST(merchant), COUNT_DISTINCT(country)
```

### 4. **Push vs Pull Queries**

**Push Queries (Continuous):**
```sql
SELECT * FROM fraud_alerts EMIT CHANGES;
```
- Continuously streams results
- Used for real-time monitoring

**Pull Queries (Point-in-Time):**
```sql
SELECT * FROM user_transaction_stats WHERE card_number = '...';
```
- Returns current state
- Used for dashboards, API queries

---

## 🧪 Testing the Queries

### 1. Start the Transaction Generator
```bash
cd ../data-generator
python transaction_generator.py
```

### 2. Open Multiple KSQLDB CLI Terminals

**Terminal 1 - Monitor Fraud Alerts:**
```bash
docker exec -it ksqldb-cli ksql http://ksqldb-server:8088
```
```sql
SELECT * FROM fraud_alerts EMIT CHANGES;
```

**Terminal 2 - Monitor Velocity Fraud:**
```bash
docker exec -it ksqldb-cli ksql http://ksqldb-server:8088
```
```sql
SELECT * FROM velocity_fraud_5min EMIT CHANGES;
```

**Terminal 3 - Monitor Valid Transactions:**
```bash
docker exec -it ksqldb-cli ksql http://ksqldb-server:8088
```
```sql
SELECT * FROM valid_transactions EMIT CHANGES;
```

### 3. Verify Data Flow

You should see:
- ✅ Fraud alerts appearing in Terminal 1 (RED transactions from generator)
- ✅ Velocity alerts when rapid transactions are generated
- ✅ Valid transactions flowing through Terminal 3

---

## 🐛 Troubleshooting

### Issue: "Stream does not exist"
**Solution:** Run the create scripts in order (01 → 02 → 03)

### Issue: "No data appearing"
**Solution:** 
1. Check if generator is running: `docker logs -f kafka`
2. Verify topic exists: `SHOW TOPICS;`
3. Check source stream: `SELECT * FROM transactions_stream EMIT CHANGES LIMIT 5;`

### Issue: "Table not available for pull queries"
**Solution:** Some tables need time to materialize. Wait 30 seconds after creation.

---

## 📚 References

- **KSQLDB Documentation:** https://docs.ksqldb.io
- **Window Types:** https://docs.ksqldb.io/en/latest/concepts/time-and-windows-in-ksqldb-queries/
- **Stream Processing:** https://docs.ksqldb.io/en/latest/developer-guide/ksqldb-reference/

---

**Author:** Kalpesh Rawal  
**Project:** Kafka Learning Portfolio - Fraud Detection System
