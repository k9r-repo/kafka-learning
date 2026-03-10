# Project Architecture: Real-Time Fraud Detection with KSQLDB

## 📐 System Architecture Overview

This document provides a detailed explanation of the architecture, data flow, and technical design decisions for the real-time fraud detection system.

---

## 🏗️ High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Transaction Generator                        │
│                           (Python)                                  │
│  • Simulates credit card transactions                              │
│  • Injects fraud patterns (velocity, amount, geo)                  │
│  • Publishes to Kafka topic                                        │
└────────────────────────────┬────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         Apache Kafka                                │
│  Topic: transactions                                                │
│  • Partitions: 3 (for parallelism)                                 │
│  • Replication Factor: 1 (single broker setup)                     │
│  • Retention: 7 days                                                │
└────────────────────────────┬────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        KSQLDB Server                                │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │               Stream Processing Pipeline                     │  │
│  ├──────────────────────────────────────────────────────────────┤  │
│  │  1. transactions_stream (source stream)                     │  │
│  │  2. user_transaction_stats (stateful table)                 │  │
│  │  3. enriched_transactions (stream + user stats join)        │  │
│  │  4. Fraud detection streams:                                │  │
│  │     • amount_anomaly_fraud                                  │  │
│  │     • high_risk_merchant_fraud                              │  │
│  │     • geo_anomaly_potential                                 │  │
│  │  5. Windowed aggregations:                                  │  │
│  │     • velocity_fraud_5min (tumbling window)                 │  │
│  │     • geo_fraud_hopping (hopping window)                    │  │
│  │     • spending_burst_session (session window)               │  │
│  │  6. Unified fraud_alerts stream                             │  │
│  │  7. Custom UDF: FraudScoreUDF (Java)                        │  │
│  └──────────────────────────────────────────────────────────────┘  │
└────────────────────────────┬────────────────────────────────────────┘
                            │
            ┌───────────────┼───────────────┐
            ▼               ▼               ▼
    ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
    │ fraud_alerts │ │ valid_txns   │ │ velocity_    │
    │   (Topic)    │ │   (Topic)    │ │ fraud (Topic)│
    └──────────────┘ └──────────────┘ └──────────────┘
```

---

## 🔄 Data Flow Detailed

### 1. **Data Ingestion**

```
Transaction Generator → Kafka Producer API → transactions topic
```

**Transaction Schema:**
```json
{
  "transaction_id": "string",
  "card_number": "string",
  "cardholder_name": "string",
  "amount": "double",
  "merchant": "string",
  "merchant_category": "string",
  "country": "string",
  "city": "string",
  "timestamp": "bigint",
  "currency": "string",
  "fraud_label": "boolean",
  "fraud_type": "string"
}
```

### 2. **Stream Creation**

```sql
CREATE STREAM transactions_stream (...) 
WITH (
  KAFKA_TOPIC='transactions',
  VALUE_FORMAT='JSON',
  TIMESTAMP='timestamp'
);
```

- **Purpose:** Creates a KSQLDB stream from the Kafka topic
- **Format:** JSON (human-readable, flexible schema)
- **Timestamp:** Uses transaction timestamp for windowing operations

### 3. **Stateful Table Creation**

```sql
CREATE TABLE user_transaction_stats AS
  SELECT 
    card_number,
    COUNT(*) as total_transactions,
    AVG(amount) as avg_amount,
    STDDEV_SAMP(amount) as std_dev_amount,
    ...
  FROM transactions_stream
  GROUP BY card_number;
```

- **Purpose:** Maintains running statistics per card
- **Materialized View:** Continuously updated as new transactions arrive
- **State Store:** Backed by RocksDB (embedded key-value store)
- **Used For:** Anomaly detection (z-score calculation)

### 4. **Stream Enrichment**

```sql
CREATE STREAM enriched_transactions AS
  SELECT 
    t.*,
    u.avg_amount as user_avg_amount,
    u.std_dev_amount as user_std_dev,
    (t.amount - u.avg_amount) / NULLIF(u.std_dev_amount, 0) as z_score
  FROM transactions_stream t
  LEFT JOIN user_transaction_stats u
  ON t.card_number = u.card_number;
```

- **Purpose:** Enriches transactions with user statistics
- **Join Type:** Stream-Table join (efficient lookup)
- **Z-Score Calculation:** Measures deviation from user's average

---

## 🔍 Fraud Detection Patterns

### Pattern 1: Amount Anomaly (Statistical)

**Algorithm:**
- Calculate z-score: `(amount - avg) / stddev`
- Flag if `|z-score| > 3.0` (3 standard deviations)

**KSQL Implementation:**
```sql
CREATE STREAM amount_anomaly_fraud AS
  SELECT ...
  FROM enriched_transactions
  WHERE ABS(z_score) > 3.0;
```

**Scoring:**
- z ≥ 5: Score = 100
- z ≥ 4: Score = 90
- z ≥ 3: Score = 75

### Pattern 2: Velocity Fraud (Tumbling Window)

**Algorithm:**
- Count transactions per card in 5-minute windows
- Flag if count > 3

**KSQL Implementation:**
```sql
CREATE TABLE velocity_fraud_5min AS
  SELECT 
    card_number,
    COUNT(*) as txn_count,
    ...
  FROM transactions_stream
  WINDOW TUMBLING (SIZE 5 MINUTES)
  GROUP BY card_number
  HAVING COUNT(*) > 3;
```

**Window Type:** Tumbling (non-overlapping)
```
|---W1---|---W2---|---W3---|
0        5        10       15 (minutes)
```

### Pattern 3: Geographic Anomaly (Hopping Window)

**Algorithm:**
- Detect multiple countries in 1-hour windows
- Use overlapping windows to catch boundary cases

**KSQL Implementation:**
```sql
CREATE TABLE geo_fraud_hopping AS
  SELECT 
    card_number,
    COUNT_DISTINCT(country) as unique_countries,
    ...
  FROM transactions_stream
  WINDOW HOPPING (SIZE 1 HOUR, ADVANCE BY 10 MINUTES)
  GROUP BY card_number
  HAVING COUNT_DISTINCT(country) > 1;
```

**Window Type:** Hopping (overlapping)
```
|--------W1--------|
      |--------W2--------|
            |--------W3--------|
0    10   20   30   40   50   60 (minutes)
```

### Pattern 4: Spending Burst (Session Window)

**Algorithm:**
- Detect burst spending patterns
- Session ends after 10 minutes of inactivity

**KSQL Implementation:**
```sql
CREATE TABLE spending_burst_session AS
  SELECT ...
  FROM transactions_stream
  WINDOW SESSION (10 MINUTES)
  GROUP BY card_number
  HAVING COUNT(*) > 5 OR SUM(amount) > 3000;
```

**Window Type:** Session (variable-size based on gaps)
```
Txn Txn    Txn Txn Txn       Txn (← 10min gap)
|---|  |-|---|---|    |----Session 1----|  |---Session 2---|
```

---

## 🧮 Custom UDF: FraudScoreUDF

### Architecture

```java
@UdfDescription(name = "fraud_score", ...)
public class FraudScoreUDF {
  
  @Udf public Integer calculateZScoreRisk(...) { }
  @Udf public Integer calculateVelocityRisk(...) { }
  @Udf public Integer calculateGeoRisk(...) { }
  @Udf public Integer calculateMerchantRisk(...) { }
  @Udf public Integer calculateComprehensiveScore(...) { }
}
```

### Deployment

1. **Build:** Maven creates JAR with dependencies
2. **Mount:** Docker volume mounts JAR to KSQLDB container
3. **Load:** KSQLDB auto-discovers UDF via classpath
4. **Usage:** Available in SQL queries as `FRAUD_SCORE(...)`

### Comprehensive Scoring Algorithm

**Weighted Factors:**
- Z-Score: 30%
- Velocity: 30%
- Geographic: 25%
- Merchant: 15%

**Boost Logic:**
- If any factor ≥ 90: Boost to ≥ 85
- If 3+ factors ≥ 75: Add 10 points
- If 2 factors ≥ 75: Add 5 points

---

## 🛠️ Technology Stack Details

### Apache Kafka 3.6

**Configuration:**
- **Broker:** Single node (development setup)
- **Partitions:** 3 (allows parallel processing)
- **Replication:** 1 (single broker)
- **Log Retention:** 7 days

### KSQLDB 0.29

**Key Features Used:**
- **Streams:** Unbounded, append-only event sequences
- **Tables:** Stateful, materialized views
- **Windows:** Tumbling, hopping, session
- **Joins:** Stream-stream, stream-table
- **UDFs:** Custom Java functions

**State Storage:**
- **RocksDB:** Embedded key-value store
- **Changelog Topics:** Kafka topics for state recovery
- **Rebalancing:** Automatic partition reassignment

### Docker Compose

**Services:**
- **Zookeeper:** Kafka coordination (port 2181)
- **Kafka:** Message broker (port 9092)
- **Schema Registry:** Avro schema management (port 8081)
- **KSQLDB Server:** Stream processing (port 8088)
- **KSQLDB CLI:** Interactive terminal
- **Kafka UI:** Web dashboard (port 8080)

---

## 📊 Performance Characteristics

### Throughput
- **Generator:** ~1000 txn/sec (configurable)
- **Kafka:** ~10k txn/sec (single partition)
- **KSQLDB:** ~5k txn/sec (per stream)

### Latency
- **End-to-End:** < 200ms (ingestion → alert)
- **Windowing:** Dependent on window size
- **UDF Execution:** ~5ms per call

### Scalability
- **Kafka Partitions:** Horizontal scaling
- **KSQLDB Instances:** Can run multiple servers
- **State Stores:** Automatically distributed

---

## 🔐 Security Considerations

### Current Implementation (Development)
- ✅ No authentication (local development)
- ✅ No encryption (localhost)
- ✅ Open ports (Docker network)

### Production Recommendations
- ⚠️ Enable SASL/SSL for Kafka
- ⚠️ Implement KSQLDB authentication
- ⚠️ Use secrets management (Vault)
- ⚠️ Network segmentation
- ⚠️ Audit logging
- ⚠️ Data encryption at rest

---

## 🧪 Testing Strategy

### Unit Tests (Java UDF)
- **Framework:** JUnit 5 + AssertJ
- **Coverage:** 53 tests, all critical paths
- **Assertions:** Edge cases, nulls, boundaries

### Integration Tests
- **Scenario Testing:** Normal, fraudulent, velocity
- **Data Generation:** Controlled fraud injection
- **Validation:** Query results match expectations

### End-to-End Tests
- **Full Pipeline:** Generator → Kafka → KSQLDB → Alerts
- **Visual Verification:** Kafka UI screenshots
- **Performance:** Latency and throughput metrics

---

## 📈 Monitoring & Observability

### Available Metrics
- **Kafka:** Broker metrics via JMX
- **KSQLDB:** Query performance, state store size
- **UDF:** Execution time, invocation count

### Visualization
- **Kafka UI:** Topics, messages, consumer groups
- **KSQLDB Queries:** Real-time push queries
- **Dashboard:** Hourly fraud summary table

---

## 🚀 Deployment Options

### Local Development (Current)
```bash
docker-compose up -d
```

### Production Considerations
- **Kubernetes:** Helm charts for Confluent Platform
- **Cloud:** AWS MSK, Confluent Cloud
- **Monitoring:** Prometheus + Grafana
- **Alerting:** PagerDuty, Slack integration

---

## 📚 Design Decisions

### Why KSQLDB over Kafka Streams?
- ✅ **SQL Syntax:** More accessible, less code
- ✅ **Rapid Development:** Faster prototyping
- ✅ **Built-in State Management:** Automatic handling
- ❌ **Less Flexibility:** Streams API more powerful

### Why JSON over Avro?
- ✅ **Readability:** Human-readable format
- ✅ **Simplicity:** No schema registry dependency
- ❌ **Performance:** Avro more efficient
- ❌ **Validation:** No schema enforcement

### Why Multiple Windows?
- **Tumbling:** Discrete time buckets, clear boundaries
- **Hopping:** Overlapping analysis, catch boundary cases
- **Session:** Variable-size, detect burst patterns

---

**Author:** Kalpesh Rawal  
**Last Updated:** March 10, 2026  
**Version:** 1.0.0
